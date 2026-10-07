import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { Api, Config, Erro, Sessao, errosDaResposta } from '../api';
import { Icone } from '../icone';

/** F10 / RF09: antecedência mínima, faixa de horário (global e da unidade) e endpoint do SNP. */
@Component({
  selector: 'app-config',
  imports: [FormsModule, Icone],
  template: `
    <div class="cabecalho-pagina">
      <div>
        <h2>Configurações</h2>
        <p>Regras aplicadas a todas as reservas da unidade, sem alterar código.</p>
      </div>
    </div>
    @if (erros().length) {
      <div class="erros" role="alert"><app-icone nome="alerta" [tamanho]="20" />
        <ul>@for (e of erros(); track $index) { <li>{{ e.mensagem }}</li> }</ul></div>
    }
    @if (ok()) { <p class="sucesso" role="status"><app-icone nome="ok" [tamanho]="20" /> Configurações salvas.</p> }
    @if (carregado() && cfg) {
      <form (ngSubmit)="salvar()">
        <fieldset [disabled]="!sessao.admin()">
          <legend class="visualmente-oculto">Configurações da unidade</legend>
          <div class="grade">
            <section class="cartao">
              <h3 class="cartao-titulo"><app-icone nome="relogio" /> Antecedência</h3>
              <label for="c-ant">Antecedência mínima (minutos)</label>
              <input id="c-ant" name="ant" type="number" min="0" [(ngModel)]="cfg.antecedenciaMin" required aria-describedby="h-ant" />
              <span id="h-ant" class="ajuda">Tempo mínimo entre agora e o início da reserva (também vale para o cancelamento).</span>
            </section>
            <section class="cartao">
              <h3 class="cartao-titulo"><app-icone nome="calendario" /> Faixa de horário</h3>
              <div class="par">
                <div><label for="c-hmin">Mínimo global</label><input id="c-hmin" name="hmin" type="time" [(ngModel)]="cfg.horaMin" required /></div>
                <div><label for="c-hmax">Máximo global</label><input id="c-hmax" name="hmax" type="time" [(ngModel)]="cfg.horaMax" required /></div>
                <div><label for="c-umin">Mínimo da unidade</label><input id="c-umin" name="umin" type="time" [(ngModel)]="cfg.unidadeHoraMin" aria-describedby="h-uni" /></div>
                <div><label for="c-umax">Máximo da unidade</label><input id="c-umax" name="umax" type="time" [(ngModel)]="cfg.unidadeHoraMax" aria-describedby="h-uni" /></div>
              </div>
              <span id="h-uni" class="ajuda">Opcional. Quando preenchida, a faixa da unidade prevalece sobre a global.</span>
            </section>
            <section class="cartao largo">
              <h3 class="cartao-titulo"><app-icone nome="pacote" /> Integração com o SNP</h3>
              <label for="c-snp">Endpoint da API do Sistema Nacional de Pedidos</label>
              <input id="c-snp" name="snp" type="url" [(ngModel)]="cfg.snpEndpoint" required />
            </section>
          </div>
          @if (sessao.admin()) {
            <div class="acoes"><button type="submit"><app-icone nome="ok" /> Salvar configurações</button></div>
          } @else {
            <p class="aviso"><app-icone nome="alerta" [tamanho]="20" /> Somente o administrador pode alterar as configurações.</p>
          }
        </fieldset>
      </form>
    }
  `,
  styles: `
    .grade { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0 1rem; }
    .largo { grid-column: 1 / -1; }
    .par { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0.75rem; }
    .acoes { display: flex; justify-content: flex-end; }
    @media (max-width: 760px) { .grade { grid-template-columns: 1fr; } }
  `,
})
export class Configuracoes {
  private readonly api = inject(Api);
  protected readonly sessao = inject(Sessao);
  protected cfg: Config | null = null;
  protected readonly erros = signal<Erro[]>([]);
  protected readonly ok = signal(false);
  protected readonly carregado = signal(false);

  constructor() {
    this.api.config().subscribe((c) => {
      this.cfg = c;
      this.carregado.set(true);
    });
  }

  protected salvar(): void {
    if (!this.cfg) return;
    const c = { ...this.cfg, unidadeHoraMin: this.cfg.unidadeHoraMin || null, unidadeHoraMax: this.cfg.unidadeHoraMax || null };
    this.erros.set([]);
    this.ok.set(false);
    this.api.salvarConfig(c).subscribe({
      next: (nc) => {
        this.cfg = nc;
        this.ok.set(true);
      },
      error: (e) => this.erros.set(errosDaResposta(e)),
    });
  }
}
