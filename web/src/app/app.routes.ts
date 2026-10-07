import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'painel' },
  { path: 'painel', title: 'Painel de horários – SISGARES', loadComponent: () => import('./paginas/painel').then((m) => m.Painel) },
  { path: 'reserva/nova', title: 'Nova reserva – SISGARES', loadComponent: () => import('./paginas/reserva-form').then((m) => m.ReservaForm) },
  { path: 'reserva/:id', title: 'Reserva – SISGARES', loadComponent: () => import('./paginas/reserva-form').then((m) => m.ReservaForm) },
  { path: 'minhas', title: 'Minhas reservas – SISGARES', loadComponent: () => import('./paginas/minhas').then((m) => m.Minhas) },
  { path: 'atendimento', title: 'Painel do atendente – SISGARES', loadComponent: () => import('./paginas/atendimento').then((m) => m.Atendimento) },
  { path: 'notificacoes', title: 'Notificações – SISGARES', loadComponent: () => import('./paginas/notificacoes').then((m) => m.Notificacoes) },
  { path: 'cadastros/ambientes', title: 'Ambientes – SISGARES', loadComponent: () => import('./paginas/ambientes').then((m) => m.Ambientes) },
  { path: 'cadastros/setores', title: 'Setores – SISGARES', loadComponent: () => import('./paginas/setores').then((m) => m.Setores) },
  { path: 'cadastros/recursos', title: 'Recursos – SISGARES', loadComponent: () => import('./paginas/recursos').then((m) => m.Recursos) },
  { path: 'config', title: 'Configurações – SISGARES', loadComponent: () => import('./paginas/config').then((m) => m.Configuracoes) },
  { path: '**', redirectTo: 'painel' },
];
