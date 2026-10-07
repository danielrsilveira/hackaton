import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { Sessao } from '../api';
import { Autenticacao } from './autenticacao';
import { autenticacaoInterceptor } from './interceptor';

const tick = () => new Promise<void>((r) => setTimeout(r));

function configurar(auth: Partial<Autenticacao>) {
  TestBed.configureTestingModule({
    providers: [
      provideHttpClient(withInterceptors([autenticacaoInterceptor])),
      provideHttpClientTesting(),
      { provide: Autenticacao, useValue: auth },
    ],
  });
  return { http: TestBed.inject(HttpClient), ctrl: TestBed.inject(HttpTestingController) };
}

describe('autenticacaoInterceptor', () => {
  it('modo cognito: envia Bearer e nunca o X-Usuario-Id', async () => {
    const { http, ctrl } = configurar({ ativo: (() => true) as never, tokenParaApi: vi.fn().mockResolvedValue('id-token-1') });
    TestBed.inject(Sessao).trocar(3); // mesmo com um usuário "simulado" escolhido
    http.get('/api/reservas/minhas').subscribe();
    await tick();
    const req = ctrl.expectOne('/api/reservas/minhas');
    expect(req.request.headers.get('Authorization')).toBe('Bearer id-token-1');
    expect(req.request.headers.has('X-Usuario-Id')).toBe(false);
    req.flush([]);
    localStorage.clear();
  });

  it('modo cognito: não manda o token para URLs que não são da API', async () => {
    const tokenParaApi = vi.fn().mockResolvedValue('id-token-1');
    const { http, ctrl } = configurar({ ativo: (() => true) as never, tokenParaApi });
    http.get('https://outro-site.example/dados').subscribe();
    await tick();
    const req = ctrl.expectOne('https://outro-site.example/dados');
    expect(req.request.headers.has('Authorization')).toBe(false);
    expect(tokenParaApi).not.toHaveBeenCalled();
    req.flush({});
  });

  it('modo cognito: 401 aciona o retorno ao login', async () => {
    const sessaoRejeitada = vi.fn();
    const { http, ctrl } = configurar({ ativo: (() => true) as never, tokenParaApi: vi.fn().mockResolvedValue('t'), sessaoRejeitada });
    let status = 0;
    http.get('/api/me').subscribe({ error: (e) => (status = e.status) });
    await tick();
    ctrl.expectOne('/api/me').flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(status).toBe(401);
    expect(sessaoRejeitada).toHaveBeenCalledTimes(1);
  });

  it('modo cognito: 403 chega à tela sem redirecionar', async () => {
    const sessaoRejeitada = vi.fn();
    const { http, ctrl } = configurar({ ativo: (() => true) as never, tokenParaApi: vi.fn().mockResolvedValue('t'), sessaoRejeitada });
    let status = 0;
    http.get('/api/config').subscribe({ error: (e) => (status = e.status) });
    await tick();
    ctrl.expectOne('/api/config').flush({}, { status: 403, statusText: 'Forbidden' });
    expect(status).toBe(403);
    expect(sessaoRejeitada).not.toHaveBeenCalled();
  });

  it('modo cognito: sem token disponível vira 401 e volta ao login, sem chamar a API', async () => {
    const sessaoRejeitada = vi.fn();
    const { http, ctrl } = configurar({ ativo: (() => true) as never, tokenParaApi: vi.fn().mockResolvedValue(null), sessaoRejeitada });
    let status = 0;
    http.get('/api/me').subscribe({ error: (e) => (status = e.status) });
    await tick();
    ctrl.expectNone('/api/me');
    expect(status).toBe(401);
    expect(sessaoRejeitada).toHaveBeenCalledTimes(1);
  });

  it('modo simulado: continua enviando X-Usuario-Id e nada de Authorization', () => {
    localStorage.setItem('sisgares.usuarioId', '4');
    const { http, ctrl } = configurar({ ativo: (() => false) as never });
    http.get('/api/reservas/minhas').subscribe();
    const req = ctrl.expectOne('/api/reservas/minhas');
    expect(req.request.headers.get('X-Usuario-Id')).toBe('4');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush([]);
    localStorage.clear();
  });
});
