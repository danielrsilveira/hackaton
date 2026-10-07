import { TestBed } from '@angular/core/testing';
import type { Mock } from 'vitest';

import { Autenticacao, JANELA_ANTI_LACO_MS, rotaSegura } from './autenticacao';
import { CONFIG_SIMULADA, ConfigApp, ConfigRuntime } from './config';

const CFG: ConfigApp = {
  authModo: 'cognito',
  cognitoDomain: 'https://sisgares-teste.auth.us-east-1.amazoncognito.com',
  clientId: 'client-teste',
  redirectUri: 'http://localhost:3000',
  logoutUri: 'http://localhost:3000',
  scopes: 'openid email',
};

/** JWT sem assinatura válida: o front só lê exp e nonce. */
function jwt(claims: Record<string, unknown>): string {
  const b64 = (o: unknown) => btoa(JSON.stringify(o)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  return `${b64({ alg: 'RS256' })}.${b64(claims)}.assinatura`;
}
const emSegundos = (ms: number) => Math.floor((Date.now() + ms) / 1000);

function resposta(status: number, corpo?: unknown): Response {
  return new Response(corpo === undefined ? null : JSON.stringify(corpo), { status });
}

const tick = () => new Promise<void>((r) => setTimeout(r));

describe('Autenticacao (Cognito, code + PKCE)', () => {
  let auth: Autenticacao;
  let irPara: Mock<(url: string) => void>;
  let fetchMock: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    sessionStorage.clear();
    localStorage.clear();
    window.history.replaceState(null, '', '/');
    fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);

    TestBed.configureTestingModule({});
    TestBed.inject(ConfigRuntime).valor.set(CFG);
    auth = TestBed.inject(Autenticacao);
    irPara = vi.fn<(url: string) => void>();
    (auth as unknown as { irPara: (u: string) => void }).irPara = irPara;
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.useRealTimers();
  });

  function prepararRetorno(state = 'estado-ok', nonce = 'nonce-ok', rota = '/reserva/12') {
    sessionStorage.setItem('sisgares.auth.pkce', JSON.stringify({ verifier: 'verifier-123', state, nonce, rota }));
    window.history.replaceState(null, '', `/?code=codigo-abc&state=${state}`);
  }

  it('no modo simulado não faz nada', async () => {
    TestBed.inject(ConfigRuntime).valor.set(CONFIG_SIMULADA);
    await auth.iniciar();
    expect(auth.ativo()).toBe(false);
    expect(irPara).not.toHaveBeenCalled();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('sem sessão redireciona ao Managed Login com PKCE S256, state e nonce, e não libera a aplicação', async () => {
    const pendente = Symbol('pendente');
    const resultado = await Promise.race([auth.iniciar(), tick().then(() => tick()).then(() => pendente)]);
    expect(resultado).toBe(pendente); // a promessa do inicializador não resolve enquanto navega

    expect(irPara).toHaveBeenCalledTimes(1);
    const url = new URL(irPara.mock.calls[0][0] as string);
    expect(url.origin + url.pathname).toBe(`${CFG.cognitoDomain}/oauth2/authorize`);
    expect(url.searchParams.get('response_type')).toBe('code');
    expect(url.searchParams.get('client_id')).toBe('client-teste');
    expect(url.searchParams.get('redirect_uri')).toBe('http://localhost:3000');
    expect(url.searchParams.get('scope')).toBe('openid email');
    expect(url.searchParams.get('code_challenge_method')).toBe('S256');
    expect(url.searchParams.get('code_challenge')).toMatch(/^[A-Za-z0-9_-]{43}$/);
    expect(url.searchParams.get('state')).toBeTruthy();
    expect(url.searchParams.get('nonce')).toBeTruthy();
    expect(url.searchParams.has('client_secret')).toBe(false);

    const pkce = JSON.parse(sessionStorage.getItem('sisgares.auth.pkce') ?? '{}');
    expect(pkce.state).toBe(url.searchParams.get('state'));
    expect(localStorage.length).toBe(0);
  });

  it('conclui o login: troca o code com o verifier, confere state e nonce, limpa a URL e guarda só em sessionStorage', async () => {
    prepararRetorno();
    const idToken = jwt({ exp: emSegundos(3600_000), nonce: 'nonce-ok', email: 'ana@exemplo.gov.br' });
    fetchMock.mockResolvedValue(resposta(200, { id_token: idToken, access_token: 'acesso', refresh_token: 'refresh-1', expires_in: 3600 }));

    await auth.iniciar();

    expect(auth.erro()).toBeNull();
    expect(await auth.tokenParaApi()).toBe(idToken);
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe(`${CFG.cognitoDomain}/oauth2/token`);
    const corpo = new URLSearchParams(init.body as URLSearchParams);
    expect(corpo.get('grant_type')).toBe('authorization_code');
    expect(corpo.get('code')).toBe('codigo-abc');
    expect(corpo.get('code_verifier')).toBe('verifier-123');
    expect(corpo.get('redirect_uri')).toBe('http://localhost:3000');
    expect(corpo.has('client_secret')).toBe(false);

    expect(window.location.pathname + window.location.search).toBe('/reserva/12');
    expect(sessionStorage.getItem('sisgares.auth.pkce')).toBeNull(); // uso único
    const guardado = sessionStorage.getItem('sisgares.auth.sessao') ?? '';
    expect(guardado).toContain('refresh-1');
    expect(guardado).not.toContain('acesso'); // o access token é descartado
    expect(localStorage.length).toBe(0); // nunca localStorage
  });

  it('recusa o retorno com state diferente e não chama o endpoint de token', async () => {
    prepararRetorno('estado-ok');
    window.history.replaceState(null, '', '/?code=codigo-abc&state=outro');
    await auth.iniciar();
    expect(auth.erro()).toBeTruthy();
    expect(fetchMock).not.toHaveBeenCalled();
    expect(await auth.tokenParaApi()).toBeNull();
  });

  it('recusa retorno sem PKCE pendente (ex.: link reaproveitado)', async () => {
    window.history.replaceState(null, '', '/?code=codigo-abc&state=x');
    await auth.iniciar();
    expect(auth.erro()).toBeTruthy();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('recusa o ID token com nonce diferente', async () => {
    prepararRetorno();
    fetchMock.mockResolvedValue(resposta(200, { id_token: jwt({ exp: emSegundos(3600_000), nonce: 'outro' }) }));
    await auth.iniciar();
    expect(auth.erro()).toBeTruthy();
    expect(await auth.tokenParaApi()).toBeNull();
  });

  it('mostra erro genérico quando o Cognito devolve error=, sem redirecionar em laço', async () => {
    window.history.replaceState(null, '', '/?error=access_denied&error_description=detalhe+interno');
    await auth.iniciar();
    expect(auth.erro()).toBe('Não foi possível concluir o login. Tente novamente.');
    expect(auth.erro()).not.toContain('detalhe');
    expect(irPara).not.toHaveBeenCalled();
    expect(window.location.search).toBe('');
  });

  it('não redireciona para fora do site após o login (rota inválida vira /)', async () => {
    expect(rotaSegura('//evil.example/x')).toBe('/');
    expect(rotaSegura('https://evil.example')).toBe('/');
    expect(rotaSegura('/\\evil')).toBe('/');
    expect(rotaSegura('/minhas?x=1')).toBe('/minhas?x=1');

    prepararRetorno('s', 'nonce-ok', '//evil.example');
    window.history.replaceState(null, '', '/?code=c&state=s');
    fetchMock.mockResolvedValue(resposta(200, { id_token: jwt({ exp: emSegundos(3600_000), nonce: 'nonce-ok' }) }));
    await auth.iniciar();
    expect(window.location.pathname).toBe('/');
    expect(window.location.hostname).not.toBe('evil.example');
  });

  describe('sessão existente', () => {
    function sessao(idToken: string, refreshToken: string | null = 'refresh-1') {
      sessionStorage.setItem('sisgares.auth.sessao', JSON.stringify({ idToken, refreshToken }));
    }

    it('restaura a sessão da aba após F5 sem ir ao Cognito', async () => {
      const t = jwt({ exp: emSegundos(3600_000) });
      sessao(t);
      await auth.iniciar();
      expect(irPara).not.toHaveBeenCalled();
      expect(await auth.tokenParaApi()).toBe(t);
      expect(fetchMock).not.toHaveBeenCalled();
    });

    it('renova o ID token perto de expirar e guarda o refresh token rotacionado', async () => {
      sessao(jwt({ exp: emSegundos(30_000) })); // faltam 30 s: dentro da margem
      const novo = jwt({ exp: emSegundos(3600_000) });
      fetchMock.mockResolvedValue(resposta(200, { id_token: novo, refresh_token: 'refresh-2' }));

      await auth.iniciar();

      expect(await auth.tokenParaApi()).toBe(novo);
      const corpo = new URLSearchParams((fetchMock.mock.calls[0][1] as RequestInit).body as URLSearchParams);
      expect(corpo.get('grant_type')).toBe('refresh_token');
      expect(corpo.get('refresh_token')).toBe('refresh-1');
      expect(sessionStorage.getItem('sisgares.auth.sessao')).toContain('refresh-2');
    });

    it('requisições simultâneas compartilham uma única renovação (rotação invalida o refresh antigo)', async () => {
      sessao(jwt({ exp: emSegundos(-10_000) })); // já expirado
      const novo = jwt({ exp: emSegundos(3600_000) });
      (auth as unknown as { restaurar: () => void }).restaurar();
      fetchMock.mockImplementation(async () => {
        await tick();
        return resposta(200, { id_token: novo, refresh_token: 'refresh-2' });
      });

      const [a, b, c] = await Promise.all([auth.tokenParaApi(), auth.tokenParaApi(), auth.tokenParaApi()]);
      expect([a, b, c]).toEqual([novo, novo, novo]);
      expect(fetchMock).toHaveBeenCalledTimes(1);
    });

    it('refresh recusado (400) descarta a sessão e devolve null', async () => {
      sessao(jwt({ exp: emSegundos(-10_000) }));
      (auth as unknown as { restaurar: () => void }).restaurar();
      fetchMock.mockResolvedValue(resposta(400, { error: 'invalid_grant' }));
      expect(await auth.tokenParaApi()).toBeNull();
      expect(sessionStorage.getItem('sisgares.auth.sessao')).toBeNull();
    });

    it('falha de rede ao renovar propaga o erro e mantém a sessão', async () => {
      sessao(jwt({ exp: emSegundos(-10_000) }));
      (auth as unknown as { restaurar: () => void }).restaurar();
      fetchMock.mockRejectedValue(new TypeError('Failed to fetch'));
      await expect(auth.tokenParaApi()).rejects.toBeTruthy();
      expect(sessionStorage.getItem('sisgares.auth.sessao')).toContain('refresh-1');
    });

    it('sem refresh token e com ID token expirado, descarta a sessão', async () => {
      sessao(jwt({ exp: emSegundos(-10_000) }), null);
      (auth as unknown as { restaurar: () => void }).restaurar();
      expect(await auth.tokenParaApi()).toBeNull();
      expect(fetchMock).not.toHaveBeenCalled();
    });
  });

  describe('401 da API', () => {
    it('logo após o login mostra erro em vez de redirecionar de novo (evita laço)', () => {
      sessionStorage.setItem('sisgares.auth.loginEm', String(Date.now()));
      auth.sessaoRejeitada();
      expect(auth.erro()).toBeTruthy();
      expect(irPara).not.toHaveBeenCalled();
    });

    it('com a sessão antiga, descarta e volta ao login', async () => {
      sessionStorage.setItem('sisgares.auth.loginEm', String(Date.now() - JANELA_ANTI_LACO_MS - 1000));
      sessionStorage.setItem('sisgares.auth.sessao', JSON.stringify({ idToken: jwt({ exp: 1 }), refreshToken: 'r' }));
      auth.sessaoRejeitada();
      await tick();
      await tick();
      expect(sessionStorage.getItem('sisgares.auth.sessao')).toBeNull();
      expect(irPara).toHaveBeenCalledTimes(1);
      expect(String(irPara.mock.calls[0][0])).toContain('/oauth2/authorize');
    });
  });

  it('sair revoga o refresh token, limpa a sessão e passa pelo /logout do Cognito', async () => {
    sessionStorage.setItem('sisgares.auth.sessao', JSON.stringify({ idToken: jwt({ exp: emSegundos(3600_000) }), refreshToken: 'refresh-9' }));
    (auth as unknown as { restaurar: () => void }).restaurar();
    fetchMock.mockResolvedValue(resposta(200));

    await auth.sair();

    const [urlRevoke, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(urlRevoke).toBe(`${CFG.cognitoDomain}/oauth2/revoke`);
    expect(new URLSearchParams(init.body as URLSearchParams).get('token')).toBe('refresh-9');
    expect(sessionStorage.getItem('sisgares.auth.sessao')).toBeNull();
    expect(await auth.tokenParaApi()).toBeNull();

    const url = new URL(irPara.mock.calls[0][0] as string);
    expect(url.pathname).toBe('/logout');
    expect(url.searchParams.get('client_id')).toBe('client-teste');
    expect(url.searchParams.get('logout_uri')).toBe('http://localhost:3000');
  });

  it('sair funciona mesmo se a revogação falhar', async () => {
    sessionStorage.setItem('sisgares.auth.sessao', JSON.stringify({ idToken: jwt({ exp: emSegundos(3600_000) }), refreshToken: 'r' }));
    (auth as unknown as { restaurar: () => void }).restaurar();
    fetchMock.mockRejectedValue(new TypeError('CORS'));
    await auth.sair();
    expect(irPara).toHaveBeenCalledTimes(1);
  });
});
