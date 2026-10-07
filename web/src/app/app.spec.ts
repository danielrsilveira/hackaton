import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';

import { App } from './app';
import { ConfigRuntime } from './auth/config';
import { datasConsecutivas, isoData } from './datas';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
  });

  it('cria o shell com título e link para pular ao conteúdo', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('h1')?.textContent).toContain('SISGARES');
    expect(el.querySelector('a.pular')?.textContent).toContain('Pular para o conteúdo');
    expect(el.querySelector('label[for="usuario-atual"]')).toBeTruthy();
  });
});

describe('App (modo cognito)', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
    TestBed.inject(ConfigRuntime).valor.set({
      authModo: 'cognito',
      cognitoDomain: 'https://sisgares-teste.auth.us-east-1.amazoncognito.com',
      clientId: 'client-teste',
      redirectUri: 'http://localhost:4200',
      logoutUri: 'http://localhost:4200',
      scopes: 'openid email',
    });
    http = TestBed.inject(HttpTestingController);
  });

  it('busca o perfil em /api/me, mostra o botão Sair e não oferece o seletor de usuário', async () => {
    const fixture = TestBed.createComponent(App);
    http.expectNone('/api/usuarios');
    http.expectOne('/api/me').flush({
      id: 3, nome: 'Carla Mendes – Administradora (fictícia)', email: 'carla.mendes@exemplo.gov.br',
      perfil: 'ADMIN', unidadeId: 1, envolvidoId: null,
    });
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('#usuario-atual')).toBeNull();
    expect(Array.from(el.querySelectorAll('button')).some((b) => b.textContent?.includes('Sair'))).toBe(true);
    expect(el.querySelector('.perfil-nome')?.textContent).toContain('Carla Mendes');
    expect(el.textContent).not.toContain('sem autenticação real');
  });

  it('403 em /api/me mostra a tela de acesso não autorizado, com Sair', async () => {
    const fixture = TestBed.createComponent(App);
    http.expectOne('/api/me').flush({}, { status: 403, statusText: 'Forbidden' });
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[role="alert"] h2')?.textContent).toContain('Acesso não autorizado');
    expect(Array.from(el.querySelectorAll('[role="alert"] button')).some((b) => b.textContent?.includes('Sair'))).toBe(true);
  });
});

describe('datasConsecutivas', () => {
  it('pula sábados e domingos quando solicitado', () => {
    // 09/10/2026 é sexta-feira.
    const ds = datasConsecutivas('2026-10-09', 2, false).map(isoData);
    expect(ds).toEqual(['2026-10-09', '2026-10-12']);
  });

  it('inclui finais de semana quando solicitado', () => {
    const ds = datasConsecutivas('2026-10-09', 3, true).map(isoData);
    expect(ds).toEqual(['2026-10-09', '2026-10-10', '2026-10-11']);
  });
});
