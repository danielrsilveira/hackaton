import { ApplicationConfig, inject, provideAppInitializer, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import { provideRouter, withComponentInputBinding } from '@angular/router';

import { routes } from './app.routes';
import { Autenticacao } from './auth/autenticacao';
import { ConfigRuntime } from './auth/config';
import { autenticacaoInterceptor } from './auth/interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideHttpClient(withFetch(), withInterceptors([autenticacaoInterceptor])),
    provideRouter(routes, withComponentInputBinding()),
    // Antes de renderizar: lê /config.json e, no modo cognito, garante a sessão (ou redireciona ao login).
    provideAppInitializer(async () => {
      // inject() só funciona de forma síncrona: obter os dois antes de qualquer await.
      const config = inject(ConfigRuntime);
      const auth = inject(Autenticacao);
      await config.carregar();
      await auth.iniciar();
    }),
  ],
};
