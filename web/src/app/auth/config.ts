import { Injectable, signal } from '@angular/core';

/**
 * Configuração em tempo de execução, lida de /config.json (gerado no start do container pelo nginx a
 * partir de variáveis de ambiente; ver web/docker-entrypoint.d). Nada disso é fixado no build.
 */
export interface ConfigApp {
  authModo: 'simulado' | 'cognito';
  /** Domínio do Managed Login, ex.: https://sisgares-xyz.auth.us-east-1.amazoncognito.com */
  cognitoDomain: string;
  clientId: string;
  redirectUri: string;
  logoutUri: string;
  scopes: string;
}

export const CONFIG_SIMULADA: ConfigApp = {
  authModo: 'simulado',
  cognitoDomain: '',
  clientId: '',
  redirectUri: '',
  logoutUri: '',
  scopes: '',
};

export class ConfigInvalidaError extends Error {}

function texto(v: unknown): string {
  return typeof v === 'string' ? v.trim() : '';
}

/** Domínio do Cognito: https obrigatório, exceto localhost (testes locais com emissor falso). */
function dominioValido(valor: string): boolean {
  try {
    const u = new URL(valor);
    const local = u.hostname === 'localhost' || u.hostname === '127.0.0.1';
    return (u.protocol === 'https:' || (local && u.protocol === 'http:')) && !u.search && !u.hash;
  } catch {
    return false;
  }
}

/** Interpreta e valida o JSON. No modo cognito, qualquer campo obrigatório ausente é erro (não cai em simulado). */
export function lerConfig(json: unknown, origem: string): ConfigApp {
  const o = json !== null && typeof json === 'object' ? (json as Record<string, unknown>) : {};
  if (texto(o['authModo']) !== 'cognito') {
    return CONFIG_SIMULADA;
  }
  const dominio = texto(o['cognitoDomain']).replace(/\/+$/, '');
  const clientId = texto(o['clientId']);
  if (!dominioValido(dominio) || !clientId) {
    throw new ConfigInvalidaError('Configuração de autenticação incompleta (cognitoDomain e clientId).');
  }
  return {
    authModo: 'cognito',
    cognitoDomain: dominio,
    clientId,
    // Devem coincidir exatamente com CallbackURLs / LogoutURLs do app client (sem barra final por padrão).
    redirectUri: texto(o['redirectUri']) || origem,
    logoutUri: texto(o['logoutUri']) || origem,
    scopes: texto(o['scopes']) || 'openid email',
  };
}

@Injectable({ providedIn: 'root' })
export class ConfigRuntime {
  readonly valor = signal<ConfigApp>(CONFIG_SIMULADA);
  /** Preenchido quando o modo cognito foi pedido mas a configuração é inválida. */
  readonly erro = signal<string | null>(null);

  async carregar(): Promise<void> {
    let json: unknown = null;
    try {
      const r = await fetch('/config.json', { cache: 'no-store' });
      if (r.ok) {
        json = await r.json();
      }
    } catch {
      // Sem config.json (ex.: ng serve sem o arquivo) ou resposta que não é JSON: modo simulado.
    }
    try {
      this.valor.set(lerConfig(json, window.location.origin));
    } catch (e) {
      this.valor.set({ ...CONFIG_SIMULADA, authModo: 'cognito' });
      this.erro.set(e instanceof Error ? e.message : 'Configuração de autenticação inválida.');
    }
  }
}
