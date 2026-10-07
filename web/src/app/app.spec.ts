import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';

import { App } from './app';
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
