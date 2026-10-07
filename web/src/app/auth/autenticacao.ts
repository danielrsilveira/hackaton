import { Injectable, computed, inject, signal } from '@angular/core';

import { ConfigRuntime } from './config';
import { claimsDoJwt, expiracaoMs } from './jwt';
import { aleatorio, desafioPkce } from './pkce';

/**
 * Login com Amazon Cognito (Managed Login) por Authorization Code + PKCE, sem biblioteca e sem client secret.
 *
 * Armazenamento: os tokens ficam em memória e, para sobreviver a F5, em sessionStorage (escopo da aba,
 * apagado ao fechá-la). Nunca em localStorage. Só o ID token e o refresh token são guardados: o access
 * token é descartado porque a API não o usa.
 *
 * A API recebe o ID token (ver ValidadoresCognito no backend): é o único token do Cognito que traz o
 * claim `email`, usado para achar o usuário no banco.
 */

const CHAVE_SESSAO = 'sisgares.auth.sessao';
const CHAVE_PKCE = 'sisgares.auth.pkce';
const CHAVE_LOGIN_EM = 'sisgares.auth.loginEm';

/** Renova o ID token quando falta menos que isto para expirar. */
export const MARGEM_RENOVACAO_MS = 60_000;
/** Um 401 logo após um login recém-concluído indica problema de configuração, não sessão expirada. */
export const JANELA_ANTI_LACO_MS = 15_000;

interface Tokens {
  idToken: string;
  refreshToken: string | null;
}

interface Pkce {
  verifier: string;
  state: string;
  nonce: string;
  rota: string;
}

interface RespostaToken {
  id_token: string;
  refresh_token?: string;
}

export class ErroRede extends Error {}
export class ErroToken extends Error {
  constructor(readonly status: number) {
    super('Falha no endpoint de token');
  }
}

function armazenamento(): Storage | null {
  try {
    return window.sessionStorage;
  } catch {
    return null;
  }
}

/** Só caminhos internos: evita redirecionar para outro site após o login (open redirect). */
export function rotaSegura(rota: unknown): string {
  return typeof rota === 'string' && rota.startsWith('/') && !rota.startsWith('//') && !rota.includes('\\') ? rota : '/';
}

@Injectable({ providedIn: 'root' })
export class Autenticacao {
  private readonly config = inject(ConfigRuntime);

  private tokens: Tokens | null = null;
  private renovando: Promise<string | null> | null = null;
  private redirecionando = false;

  /** true no modo cognito. No modo simulado, nada deste serviço é usado. */
  readonly ativo = computed(() => this.config.valor().authModo === 'cognito');
  /** Falha que bloqueia a tela (login não concluído, configuração inválida, 401 repetido). */
  readonly erro = signal<string | null>(null);
  /** Token válido, mas o e-mail não tem cadastro no SISGARES (403 da API). */
  readonly acessoNegado = signal(false);

  // ---------- Inicialização (APP_INITIALIZER) ----------

  /**
   * Garante uma sessão antes de a aplicação renderizar: conclui o retorno do login (?code=), restaura a
   * sessão da aba ou redireciona ao login. No redirecionamento a promessa nunca resolve, para o Angular
   * não renderizar (nem chamar a API) enquanto a página está sendo trocada.
   */
  async iniciar(): Promise<void> {
    if (!this.ativo()) {
      return;
    }
    const cfgErro = this.config.erro();
    if (cfgErro) {
      this.erro.set(cfgErro);
      return;
    }

    const params = new URL(window.location.href).searchParams;
    if (params.has('error')) {
      // Não repassa error_description à tela: pode conter detalhes internos do provedor.
      this.pkceLer(true);
      this.limparUrl('/');
      this.erro.set('Não foi possível concluir o login. Tente novamente.');
      return;
    }
    if (params.has('code')) {
      await this.concluirLogin(params.get('code') ?? '', params.get('state'));
      return;
    }

    this.restaurar();
    if (this.tokens) {
      try {
        if (await this.tokenParaApi()) {
          return;
        }
      } catch {
        // Sem rede para renovar agora: segue sem sessão e cai no login abaixo.
      }
    }
    await this.entrar();
    if (!this.erro()) {
      await new Promise<never>(() => undefined);
    }
  }

  // ---------- Login / logout ----------

  /** Redireciona ao Managed Login (code + PKCE + state + nonce) e volta para a rota atual. */
  async entrar(): Promise<void> {
    if (this.redirecionando) {
      return;
    }
    this.redirecionando = true;
    try {
      const cfg = this.config.valor();
      const verifier = aleatorio(32);
      const pkce: Pkce = {
        verifier,
        state: aleatorio(16),
        nonce: aleatorio(16),
        rota: rotaSegura(window.location.pathname + window.location.search + window.location.hash),
      };
      const desafio = await desafioPkce(verifier);
      armazenamento()?.setItem(CHAVE_PKCE, JSON.stringify(pkce));

      const url = new URL(`${cfg.cognitoDomain}/oauth2/authorize`);
      url.searchParams.set('response_type', 'code');
      url.searchParams.set('client_id', cfg.clientId);
      url.searchParams.set('redirect_uri', cfg.redirectUri);
      url.searchParams.set('scope', cfg.scopes);
      url.searchParams.set('state', pkce.state);
      url.searchParams.set('nonce', pkce.nonce);
      url.searchParams.set('code_challenge', desafio);
      url.searchParams.set('code_challenge_method', 'S256');
      this.irPara(url.toString());
    } catch {
      this.redirecionando = false;
      this.erro.set('Não foi possível iniciar o login neste navegador. Use HTTPS ou localhost.');
    }
  }

  /** Encerra a sessão local, revoga o refresh token (melhor esforço) e sai do Managed Login. */
  async sair(): Promise<void> {
    const cfg = this.config.valor();
    const refresh = this.tokens?.refreshToken ?? null;
    this.limparSessao();
    if (refresh) {
      await Promise.race([this.revogar(refresh), new Promise<void>((r) => setTimeout(r, 1500))]);
    }
    const url = new URL(`${cfg.cognitoDomain}/logout`);
    url.searchParams.set('client_id', cfg.clientId);
    url.searchParams.set('logout_uri', cfg.logoutUri);
    this.irPara(url.toString());
  }

  /**
   * A API respondeu 401. Se o login acabou de acontecer, não redireciona de novo (evitaria um laço
   * infinito com a API recusando o token): mostra o erro.
   */
  sessaoRejeitada(): void {
    const loginEm = Number(armazenamento()?.getItem(CHAVE_LOGIN_EM) ?? 0);
    if (Date.now() - loginEm < JANELA_ANTI_LACO_MS) {
      this.erro.set('Não foi possível validar o seu acesso. Saia e tente novamente; se persistir, procure o suporte.');
      return;
    }
    this.limparSessao();
    void this.entrar();
  }

  // ---------- Token para a API ----------

  /**
   * ID token vigente, renovando-o se estiver perto de expirar. Null se não há sessão (ou se o refresh
   * foi recusado, caso em que a sessão é descartada). Falha de rede propaga erro e mantém a sessão.
   */
  async tokenParaApi(): Promise<string | null> {
    if (!this.tokens) {
      return null;
    }
    if (expiracaoMs(this.tokens.idToken) - Date.now() > MARGEM_RENOVACAO_MS) {
      return this.tokens.idToken;
    }
    return this.renovar();
  }

  /**
   * Uma renovação por vez: com rotação de refresh token, usar o mesmo token em duas chamadas
   * simultâneas faria a segunda falhar e derrubaria a sessão.
   */
  private renovar(): Promise<string | null> {
    if (!this.renovando) {
      this.renovando = this.executarRenovacao().finally(() => (this.renovando = null));
    }
    return this.renovando;
  }

  private async executarRenovacao(): Promise<string | null> {
    const refresh = this.tokens?.refreshToken;
    if (!refresh) {
      this.limparSessao();
      return null;
    }
    try {
      const r = await this.postToken({
        grant_type: 'refresh_token',
        client_id: this.config.valor().clientId,
        refresh_token: refresh,
      });
      this.guardar(r, refresh);
      return this.tokens?.idToken ?? null;
    } catch (e) {
      if (e instanceof ErroToken && (e.status === 400 || e.status === 401)) {
        this.limparSessao(); // refresh token expirado, revogado ou já rotacionado
        return null;
      }
      throw e;
    }
  }

  // ---------- Retorno do login ----------

  private async concluirLogin(code: string, state: string | null): Promise<void> {
    const pkce = this.pkceLer(true); // uso único
    const rota = rotaSegura(pkce?.rota);
    this.limparUrl(rota);
    if (!pkce || !code || !state || state !== pkce.state) {
      this.erro.set('Não foi possível concluir o login. Tente novamente.');
      return;
    }
    try {
      const cfg = this.config.valor();
      const r = await this.postToken({
        grant_type: 'authorization_code',
        client_id: cfg.clientId,
        code,
        redirect_uri: cfg.redirectUri,
        code_verifier: pkce.verifier,
      });
      if (claimsDoJwt(r.id_token)?.['nonce'] !== pkce.nonce) {
        throw new Error('nonce');
      }
      this.guardar(r, null);
      armazenamento()?.setItem(CHAVE_LOGIN_EM, String(Date.now()));
    } catch {
      this.limparSessao();
      this.erro.set('Não foi possível concluir o login. Tente novamente.');
    }
  }

  // ---------- Infra ----------

  private async postToken(corpo: Record<string, string>): Promise<RespostaToken> {
    let resp: Response;
    try {
      resp = await fetch(`${this.config.valor().cognitoDomain}/oauth2/token`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams(corpo),
      });
    } catch {
      throw new ErroRede();
    }
    if (!resp.ok) {
      throw new ErroToken(resp.status);
    }
    const json = (await resp.json().catch(() => null)) as Partial<RespostaToken> | null;
    if (!json || typeof json.id_token !== 'string' || json.id_token.split('.').length !== 3) {
      throw new ErroToken(resp.status);
    }
    return json as RespostaToken;
  }

  private async revogar(refreshToken: string): Promise<void> {
    const cfg = this.config.valor();
    try {
      await fetch(`${cfg.cognitoDomain}/oauth2/revoke`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({ token: refreshToken, client_id: cfg.clientId }),
      });
    } catch {
      // Melhor esforço: o logout do Managed Login e a expiração do token continuam valendo.
    }
  }

  private guardar(r: RespostaToken, refreshAnterior: string | null): void {
    // Com rotação ativa o Cognito devolve um refresh token novo; sem rotação, mantém o anterior.
    this.tokens = {
      idToken: r.id_token,
      refreshToken: typeof r.refresh_token === 'string' ? r.refresh_token : refreshAnterior,
    };
    armazenamento()?.setItem(CHAVE_SESSAO, JSON.stringify(this.tokens));
  }

  private restaurar(): void {
    try {
      const bruto = armazenamento()?.getItem(CHAVE_SESSAO);
      const t = bruto ? (JSON.parse(bruto) as Partial<Tokens>) : null;
      if (t && typeof t.idToken === 'string') {
        this.tokens = { idToken: t.idToken, refreshToken: typeof t.refreshToken === 'string' ? t.refreshToken : null };
      }
    } catch {
      this.limparSessao();
    }
  }

  private limparSessao(): void {
    this.tokens = null;
    armazenamento()?.removeItem(CHAVE_SESSAO);
  }

  private pkceLer(apagar: boolean): Pkce | null {
    const s = armazenamento();
    try {
      const bruto = s?.getItem(CHAVE_PKCE);
      const p = bruto ? (JSON.parse(bruto) as Partial<Pkce>) : null;
      if (!p || typeof p.verifier !== 'string' || typeof p.state !== 'string' || typeof p.nonce !== 'string') {
        return null;
      }
      return { verifier: p.verifier, state: p.state, nonce: p.nonce, rota: rotaSegura(p.rota) };
    } catch {
      return null;
    } finally {
      if (apagar) {
        s?.removeItem(CHAVE_PKCE);
      }
    }
  }

  private limparUrl(rota: string): void {
    window.history.replaceState(null, '', rota);
  }

  /** Isolado para os testes (jsdom não navega). */
  protected irPara(url: string): void {
    window.location.assign(url);
  }
}
