import { ApplicationInitStatus } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { appConfig } from './app.config';
import { Autenticacao } from './auth/autenticacao';
import { ConfigRuntime } from './auth/config';

/** Exercita o inicializador real da aplicação (injeção de contexto, ordem config -> autenticação). */
describe('appConfig (inicializador)', () => {
  beforeEach(() => {
    sessionStorage.clear();
    window.history.replaceState(null, '', '/');
  });

  afterEach(() => vi.unstubAllGlobals());

  function jwtComExp(minutos: number): string {
    const b64 = (o: unknown) => btoa(JSON.stringify(o)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
    return `${b64({ alg: 'RS256' })}.${b64({ exp: Math.floor(Date.now() / 1000) + minutos * 60 })}.x`;
  }

  it('sem /config.json (ou resposta que não é JSON) sobe no modo simulado', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('<html></html>', { status: 200 })));
    TestBed.configureTestingModule({ providers: appConfig.providers });
    await TestBed.inject(ApplicationInitStatus).donePromise;
    expect(TestBed.inject(Autenticacao).ativo()).toBe(false);
    expect(TestBed.inject(ConfigRuntime).erro()).toBeNull();
  });

  it('modo cognito com sessão da aba sobe sem redirecionar', async () => {
    sessionStorage.setItem('sisgares.auth.sessao', JSON.stringify({ idToken: jwtComExp(10), refreshToken: 'r' }));
    const config = { authModo: 'cognito', cognitoDomain: 'https://sisgares-t.auth.us-east-1.amazoncognito.com', clientId: 'abc' };
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify(config), { status: 200 })));
    TestBed.configureTestingModule({ providers: appConfig.providers });
    await TestBed.inject(ApplicationInitStatus).donePromise;
    const auth = TestBed.inject(Autenticacao);
    expect(auth.ativo()).toBe(true);
    expect(auth.erro()).toBeNull();
    expect(await auth.tokenParaApi()).toBeTruthy();
  });

  it('modo cognito com configuração incompleta mostra erro em vez de cair em simulado', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ authModo: 'cognito' }), { status: 200 })));
    TestBed.configureTestingModule({ providers: appConfig.providers });
    await TestBed.inject(ApplicationInitStatus).donePromise;
    const auth = TestBed.inject(Autenticacao);
    expect(auth.ativo()).toBe(true);
    expect(auth.erro()).toContain('Configuração de autenticação incompleta');
  });
});
