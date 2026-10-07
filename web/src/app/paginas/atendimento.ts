import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { Api, Card, STATUS_ROTULO, Sessao, Setor } from '../api';
import { cabecalhoDia, datasConsecutivas, hoje, hora, isoData, paraData } from '../datas';
import { Icone } from '../icone';
import { iconeUrl } from '../icones-recurso';

/** F8 / RF17: colunas de datas com um card por reserva que tenha período no dia. */
@Component({
  selector: 'app-atendimento',
  imports: [FormsModule, RouterLink, Icone],
  templateUrl: './atendimento.html',
  styleUrl: './atendimento.css',
})
export class Atendimento {
  protected readonly iconeUrl = iconeUrl;
  private readonly api = inject(Api);
  private readonly sessao = inject(Sessao);
  protected readonly STATUS = STATUS_ROTULO;
  protected readonly cabecalhoDia = cabecalhoDia;

  protected readonly setores = signal<Setor[]>([]);
  protected readonly setorId = signal<number | null>(null);
  protected readonly referencia = signal(hoje());
  protected readonly colunas = signal(5);
  protected readonly fimDeSemana = signal(false);
  protected readonly cards = signal<Card[]>([]);
  protected readonly erro = signal('');

  protected readonly datas = computed(() =>
    datasConsecutivas(this.referencia(), Math.min(Math.max(this.colunas(), 1), 14), this.fimDeSemana()),
  );

  protected readonly colunasCards = computed(() =>
    this.datas().map((d) => {
      const ini = d.getTime();
      const fim = ini + 86400000;
      const doDia = this.cards()
        .map((c) => ({
          card: c,
          periodos: c.periodos.filter((p) => paraData(p.inicio).getTime() < fim && ini < paraData(p.termino).getTime()),
        }))
        .filter((x) => x.periodos.length)
        .sort((a, b) => a.periodos[0].inicio.localeCompare(b.periodos[0].inicio));
      return { data: d, itens: doDia };
    }),
  );

  constructor() {
    this.api.setores().subscribe((s) => this.setores.set(s));
    // Atendente começa filtrado pelo próprio setor.
    const u = this.sessao.usuario();
    if (u?.perfil === 'ATENDENTE') {
      this.setorId.set(u.envolvidoId);
    }
    this.carregar();
  }

  protected carregar(): void {
    const ds = this.datas();
    const dias = Math.round((ds[ds.length - 1].getTime() - ds[0].getTime()) / 86400000) + 1;
    this.api.painelAtendente(isoData(ds[0]), dias, this.setorId()).subscribe({
      next: (c) => {
        this.cards.set(c);
        this.erro.set('');
      },
      error: () => this.erro.set('Painel restrito a administrador e atendente.'),
    });
  }

  protected alterar(campo: 'setor' | 'referencia' | 'colunas' | 'fimDeSemana', valor: unknown): void {
    if (campo === 'setor') this.setorId.set(valor == null ? null : Number(valor));
    if (campo === 'referencia' && valor) this.referencia.set(String(valor));
    if (campo === 'colunas') this.colunas.set(Number(valor) || 1);
    if (campo === 'fimDeSemana') this.fimDeSemana.set(Boolean(valor));
    this.carregar();
  }

  protected ehHoje(d: Date): boolean {
    return isoData(d) === hoje();
  }

  protected faixa(inicio: string, termino: string, dia: Date): string {
    const d = isoData(dia);
    const i = inicio.startsWith(d) ? hora(inicio) : 'antes';
    const f = termino.startsWith(d) ? hora(termino) : 'depois';
    return `${i} – ${f}`;
  }
}
