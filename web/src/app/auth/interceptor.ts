import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, from, switchMap, throwError } from 'rxjs';

import { Sessao } from '../api';
import { Autenticacao } from './autenticacao';

/**
 * Identifica o usuário nas chamadas à API:
 * - modo cognito: Authorization: Bearer <ID token> (renovado quando necessário); o header X-Usuario-Id
 *   não é enviado. 401 volta ao login; 403 segue para a tela, que mostra a mensagem de permissão.
 * - modo simulado: header X-Usuario-Id do seletor de perfil (comportamento da demonstração).
 */
export const autenticacaoInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(Autenticacao);

  if (!auth.ativo()) {
    const sessao = inject(Sessao);
    return next(req.clone({ setHeaders: { 'X-Usuario-Id': String(sessao.usuarioId()) } }));
  }

  // O token só vai para a própria API (rotas relativas), nunca para outras origens.
  if (!req.url.startsWith('/api/')) {
    return next(req);
  }

  return from(auth.tokenParaApi()).pipe(
    switchMap((token) =>
      token
        ? next(req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }))
        : throwError(() => new HttpErrorResponse({ status: 401, url: req.url })),
    ),
    catchError((e: unknown) => {
      if (e instanceof HttpErrorResponse && e.status === 401) {
        auth.sessaoRejeitada();
      }
      return throwError(() => e);
    }),
  );
};
