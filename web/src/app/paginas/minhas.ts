import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { Api, MinhaReserva, STATUS_ROTULO, Status } from '../api';
import { periodoTexto } from '../datas';
import { Icone } from '../icone';

type Filtro = 'ativas' | 'todas';

/** Acompanhamento das reservas do solicitante. */
@Component({
  selector: 'app-minhas',
  imports: [RouterLink, Icone],
  template: `
    <div class="cabecalho-pagina">
      <div>
        <h2>Minhas reservas</h2>
        <p>Acompanhe, altere ou cancele as reservas que ainda não ocorreram.</p>
      </div>
      <a routerLink="/reserva/nova" class="botao"><app-icone nome="mais" /> Nova reserva</a>
    </div>

    <div class="abas" role="group" aria-label="Filtrar reservas">
      <button type="button" [class.ativa]="filtro() === 'ativas'" [attr.aria-pressed]="filtro() === 'ativas'" (click)="filtro.set('ativas')">
        Próximas e em andamento <span class="cont">{{ ativas().length }}</span>
      </button>
      <button type="button" [class.ativa]="filtro() === 'todas'" [attr.aria-pressed]="filtro() === 'todas'" (click)="filtro.set('todas')">
        Todas <span class="cont">{{ reservas().length }}</span>
      </button>
    </div>

    <div class="cartao sem-padding">
      <div class="rolagem">
        <table class="dados">
          <caption class="visualmente-oculto">Reservas do usuário atual</caption>
          <thead><tr><th scope="col">Reserva</th><th scope="col">Período</th><th scope="col">Local</th>
            <th scope="col">Finalidade</th><th scope="col">Status</th></tr></thead>
          <tbody>
            @for (r of visiveis(); track r.id) {
              <tr>
                <td><a [routerLink]="['/reserva', r.id]"><strong>#{{ r.id }}</strong></a></td>
                <td class="nowrap">{{ periodo(r.inicio, r.termino) }}</td>
                <td>{{ r.ambiente }}</td>
                <td>{{ r.finalidade }}</td>
                <td><span [class]="'status ' + r.status">{{ STATUS[r.status] }}</span></td>
              </tr>
            } @empty { <tr><td colspan="5" class="vazio">Nenhuma reserva.</td></tr> }
          </tbody>
        </table>
      </div>
    </div>
  `,
  styles: `
    .abas { display: inline-flex; gap: 0.25rem; padding: 0.25rem; background: #e9edf4; border-radius: 10px; margin-bottom: 1rem; }
    .abas button { background: transparent; border: 0; color: var(--suave); min-height: 2.2rem; }
    .abas button:hover { background: rgb(255 255 255 / 60%); color: var(--tinta); }
    .abas button.ativa { background: var(--superficie); color: var(--tinta); box-shadow: var(--sombra); }
    .cont { background: #e2e8f0; border-radius: 999px; padding: 0 0.45rem; font-size: 0.75rem; }
    .sem-padding { padding: 0; overflow: hidden; }
    .nowrap { white-space: nowrap; }
    .vazio { color: var(--suave); text-align: center; padding: 2rem !important; }
    /* Celular: cada linha vira um cartão (a tabela continua semântica para leitores de tela). */
    @media (max-width: 640px) {
      .abas { display: flex; }
      .abas button { flex: 1; white-space: normal; }
      table.dados thead { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); }
      table.dados tr { display: grid; grid-template-columns: 1fr auto; gap: 0.15rem 0.75rem; padding: 0.85rem 1rem; border-bottom: 1px solid var(--linha); }
      table.dados td { display: block; padding: 0; border: 0; }
      table.dados td:nth-child(2) { grid-column: 1 / -1; grid-row: 2; font-weight: 600; }
      table.dados td:nth-child(3), table.dados td:nth-child(4) { grid-column: 1 / -1; color: var(--suave); font-size: 0.86rem; }
      table.dados td:nth-child(5) { grid-column: 2; grid-row: 1; }
      .nowrap { white-space: normal; }
    }
  `,
})
export class Minhas {
  protected readonly STATUS = STATUS_ROTULO;
  protected readonly periodo = periodoTexto;
  protected readonly reservas = signal<MinhaReserva[]>([]);
  protected readonly filtro = signal<Filtro>('ativas');
  private static readonly ATIVOS: Status[] = ['PREVISTA', 'EM_ANDAMENTO'];
  protected readonly ativas = computed(() => this.reservas().filter((r) => Minhas.ATIVOS.includes(r.status)));
  protected readonly visiveis = computed(() => (this.filtro() === 'ativas' ? this.ativas() : this.reservas()));

  constructor() {
    inject(Api).minhas().subscribe((r) => this.reservas.set(r));
  }
}
