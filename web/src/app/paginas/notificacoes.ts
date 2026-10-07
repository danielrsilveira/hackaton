import { Component, computed, inject, signal } from '@angular/core';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { RouterLink } from '@angular/router';

import { Api, Notificacao, PedidoSnp } from '../api';
import { dataHora } from '../datas';
import { Icone } from '../icone';

const TIPO_ROTULO: Record<string, string> = { NOVA: 'Nova', ALTERADA: 'Alterada', CANCELADA: 'Cancelada' };

/** F5/F6: caixa de saída simulada dos e-mails aos setores e pedidos registrados no SNP simulado. */
@Component({
  selector: 'app-notificacoes',
  imports: [RouterLink, Icone],
  template: `
    <div class="cabecalho-pagina">
      <div>
        <h2>Notificações e SNP</h2>
        <p>E-mails enviados aos setores envolvidos (simulados) e pedidos registrados no SNP (simulado).</p>
      </div>
      <div class="resumo-num" aria-label="Totais">
        <span><strong>{{ notificacoes().length }}</strong> e-mails</span>
        <span><strong>{{ pedidos().length }}</strong> pedidos SNP</span>
      </div>
    </div>
    @if (erro()) { <p class="erros" role="alert"><app-icone nome="alerta" [tamanho]="20" /> {{ erro() }}</p> }

    <div class="layout">
      <section class="cartao caixa" aria-labelledby="t-emails">
        <h3 id="t-emails" class="cartao-titulo"><app-icone nome="email" /> Caixa de saída</h3>
        <ul class="lista">
          @for (n of notificacoes(); track n.id) {
            <li>
              <button type="button" class="item" [class.sel]="selecionada()?.id === n.id"
                      [attr.aria-pressed]="selecionada()?.id === n.id" (click)="abrir(n)">
                <span class="linha1">
                  <span [class]="'tipo ' + n.tipo">{{ tipo[n.tipo] ?? n.tipo }}</span>
                  <time>{{ dataHora(n.dthr) }}</time>
                </span>
                <span class="assunto">{{ n.assunto }}</span>
                <span class="para">Para: {{ n.setor }}</span>
              </button>
            </li>
          } @empty { <li class="vazio">Nenhuma notificação ainda.</li> }
        </ul>
      </section>

      <section class="cartao" aria-labelledby="t-email">
        <h3 id="t-email" class="cartao-titulo">Conteúdo do e-mail</h3>
        @if (selecionada(); as n) {
          <dl class="cab-email">
            <dt>Para</dt><dd>{{ n.setor }} &lt;{{ n.destinatarios }}&gt;</dd>
            <dt>Reserva</dt><dd><a [routerLink]="['/reserva', n.reservaId]">#{{ n.reservaId }}</a></dd>
          </dl>
          <iframe sandbox="" [srcdoc]="html()" [title]="'E-mail: ' + n.assunto"></iframe>
        } @else {
          <p class="vazio">Selecione um e-mail na lista.</p>
        }
      </section>
    </div>

    <section class="cartao" aria-labelledby="t-snp">
      <h3 id="t-snp" class="cartao-titulo"><app-icone nome="pacote" /> Pedidos registrados no SNP</h3>
      <div class="rolagem">
        <table class="dados">
          <thead><tr><th scope="col">Número</th><th scope="col">Reserva</th><th scope="col">Setor</th>
            <th scope="col">Serviço</th><th scope="col">Origem</th><th scope="col">Data</th></tr></thead>
          <tbody>
            @for (p of pedidos(); track p.id) {
              <tr>
                <td><a [href]="p.url" target="_blank" rel="noopener">{{ p.numero }}<span class="visualmente-oculto"> (abre em nova aba)</span></a></td>
                <td><a [routerLink]="['/reserva', p.reservaId]">#{{ p.reservaId }}</a></td>
                <td>{{ p.setor }}</td><td><span class="etiqueta">{{ p.codServico }}</span></td><td>{{ p.origem }}</td><td>{{ dataHora(p.dthr) }}</td>
              </tr>
            } @empty { <tr><td colspan="6" class="vazio">Nenhum pedido ainda.</td></tr> }
          </tbody>
        </table>
      </div>
    </section>
  `,
  styles: `
    .resumo-num { display: flex; gap: 0.5rem; }
    .resumo-num span { background: var(--superficie); border: 1px solid var(--linha); border-radius: 999px; padding: 0.3rem 0.8rem; font-size: 0.85rem; color: var(--suave); }
    .resumo-num strong { color: var(--tinta); }
    .layout { display: grid; grid-template-columns: minmax(0, 2fr) minmax(0, 3fr); gap: 1rem; align-items: start; }
    .caixa { padding-bottom: 0.5rem; }
    .lista { list-style: none; padding: 0; margin: 0 -0.5rem; max-height: 36rem; overflow-y: auto; }
    .item { display: flex; flex-direction: column; align-items: stretch; gap: 0.2rem; width: 100%; text-align: left; white-space: normal;
            background: transparent; color: var(--texto); border: 0; border-radius: 10px; padding: 0.7rem 0.75rem; min-height: 0; font-weight: 400; }
    .item:hover { background: #f1f5f9; color: var(--texto); }
    .item.sel { background: var(--primaria-50); box-shadow: inset 3px 0 0 var(--primaria); }
    .linha1 { display: flex; justify-content: space-between; align-items: center; }
    time { font-size: 0.75rem; color: var(--suave); }
    .assunto { font-weight: 600; font-size: 0.88rem; color: var(--tinta); }
    .para { font-size: 0.8rem; color: var(--suave); }
    .tipo { font-weight: 700; font-size: 0.72rem; text-transform: uppercase; letter-spacing: 0.05em; padding: 0.1rem 0.45rem; border-radius: 5px; }
    .tipo.NOVA { color: #0c326f; background: var(--primaria-100); }
    .tipo.ALTERADA { color: #7a2e0e; background: #fef0c7; }
    .tipo.CANCELADA { color: #912018; background: #fee4e2; }
    .cab-email { display: grid; grid-template-columns: auto 1fr; gap: 0.3rem 0.75rem; margin: 0 0 0.75rem; font-size: 0.88rem; }
    .cab-email dt { color: var(--suave); font-weight: 600; }
    .cab-email dd { margin: 0; }
    iframe { width: 100%; min-height: 34rem; border: 1px solid var(--linha); border-radius: 10px; background: #fff; }
    .vazio { color: var(--suave); padding: 1rem; }
    @media (max-width: 960px) { .layout { grid-template-columns: 1fr; } }
  `,
})
export class Notificacoes {
  private readonly api = inject(Api);
  private readonly sanitizer = inject(DomSanitizer);
  protected readonly dataHora = dataHora;
  protected readonly tipo = TIPO_ROTULO;
  protected readonly notificacoes = signal<Notificacao[]>([]);
  protected readonly pedidos = signal<PedidoSnp[]>([]);
  protected readonly selecionada = signal<Notificacao | null>(null);
  protected readonly erro = signal('');
  // HTML gerado pelo servidor com todo texto do usuário escapado; exibido em iframe sandbox (sem scripts).
  protected readonly html = computed<SafeHtml>(() => this.sanitizer.bypassSecurityTrustHtml(this.selecionada()?.html ?? ''));

  constructor() {
    this.api.notificacoes(null).subscribe({
      next: (ns) => {
        this.notificacoes.set(ns);
        if (ns.length) this.abrir(ns[0]);
      },
      error: () => this.erro.set('Restrito a administrador e atendente.'),
    });
    this.api.pedidosSnp().subscribe({ next: (p) => this.pedidos.set(p), error: () => {} });
  }

  protected abrir(n: Notificacao): void {
    this.selecionada.set(n);
  }
}
