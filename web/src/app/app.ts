import { Component, ElementRef, computed, inject, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';

import { Api, Sessao, Usuario } from './api';
import { Autenticacao } from './auth/autenticacao';
import { Icone } from './icone';

interface ItemMenu { rota: string; rotulo: string; icone: string; quando: 'todos' | 'gestor' | 'admin'; }

const MENU: ItemMenu[] = [
  { rota: '/painel', rotulo: 'Painel de horários', icone: 'calendario', quando: 'todos' },
  { rota: '/reserva/nova', rotulo: 'Nova reserva', icone: 'mais', quando: 'todos' },
  { rota: '/minhas', rotulo: 'Minhas reservas', icone: 'lista', quando: 'todos' },
  { rota: '/atendimento', rotulo: 'Painel do atendente', icone: 'quadro', quando: 'gestor' },
  { rota: '/notificacoes', rotulo: 'Notificações e SNP', icone: 'sino', quando: 'gestor' },
  { rota: '/cadastros/ambientes', rotulo: 'Ambientes', icone: 'local', quando: 'admin' },
  { rota: '/config', rotulo: 'Configurações', icone: 'ajustes', quando: 'admin' },
];

const PERFIL_ROTULO: Record<string, string> = { SOLICITANTE: 'Solicitante', ADMIN: 'Administrador', ATENDENTE: 'Atendente' };

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, FormsModule, Icone],
  templateUrl: './app.html',
  styleUrl: './app.css',
  host: { '(document:keydown.escape)': 'menuAberto.set(false)' },
})
export class App {
  private readonly api = inject(Api);
  protected readonly sessao = inject(Sessao);
  protected readonly auth = inject(Autenticacao);
  protected readonly usuarios = signal<Usuario[]>([]);
  protected readonly menuAberto = signal(false);
  protected readonly perfilRotulo = PERFIL_ROTULO;
  private readonly main = viewChild.required<ElementRef<HTMLElement>>('principal');

  protected readonly menu = computed(() =>
    this.auth.acessoNegado() ? [] : MENU.filter((m) => m.quando === 'todos' || (m.quando === 'gestor' && this.sessao.gestor()) || (m.quando === 'admin' && this.sessao.admin())),
  );

  protected readonly iniciais = computed(() => {
    const nome = this.sessao.usuario()?.nome ?? '?';
    return nome.split(/\s+/).filter((p) => /^[A-ZÀ-Ú]/.test(p)).slice(0, 2).map((p) => p[0]).join('');
  });

  constructor() {
    if (!this.auth.ativo()) {
      // Modo simulado: seletor de perfil da demonstração.
      this.api.usuarios().subscribe((us) => {
        this.usuarios.set(us);
        this.sessao.usuario.set(us.find((u) => u.id === this.sessao.usuarioId()) ?? us[0] ?? null);
      });
    } else if (!this.auth.erro()) {
      // Modo cognito: o perfil vem do banco, pelo e-mail do token (GET /api/me).
      this.api.me().subscribe({
        next: (u) => this.sessao.usuario.set(u),
        error: (e) => {
          if (e?.status === 403) {
            this.auth.acessoNegado.set(true);
          } else if (e?.status !== 401) {
            // 401 já foi tratado pelo interceptor (volta ao login ou mostra o erro de acesso).
            this.auth.erro.set('Não foi possível carregar o seu perfil. Tente novamente.');
          }
        },
      });
    }
    inject(Router).events.pipe(filter((e) => e instanceof NavigationEnd)).subscribe(() => this.menuAberto.set(false));
  }

  protected trocarUsuario(id: number): void {
    this.sessao.trocar(Number(id));
    // Recarrega para que cada tela busque os dados com o novo perfil.
    window.location.reload();
  }

  protected entrarDeNovo(): void {
    this.auth.erro.set(null);
    void this.auth.entrar();
  }

  protected sair(): void {
    void this.auth.sair();
  }

  protected pularParaConteudo(ev: Event): void {
    ev.preventDefault();
    this.main().nativeElement.focus();
  }

  /** Remove o sufixo "(fictícia)" e o papel para caber no seletor. */
  protected nomeCurto(u: Usuario): string {
    return u.nome.replace(/\s*\(fictíci[oa]\)/, '').split(' – ')[0];
  }
}
