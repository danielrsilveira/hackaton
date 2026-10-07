import { Component, ElementRef, computed, inject, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import {
  Ambiente, Api, Disposicao, Erro, Recurso, ReservaDetalhe, ReservaInput, STATUS_ROTULO, Sessao, errosDaResposta,
} from '../api';
import { dataHora, isoDataHora, paraData } from '../datas';
import { Icone } from '../icone';

interface PeriodoForm { inicio: string; termino: string; }
interface Selecao { marcado: boolean; quantidade: number | null; }

/** Regras exibidas já na escolha dos períodos/recursos (F2); as demais aparecem ao salvar. */
const REGRAS_PREVIA = ['RN1', 'RN3', 'RN4', 'RN5', 'RN6', 'RN8', 'RN9'];

/** F1–F4 / RF10, RF15: inclusão, alteração e cancelamento de reserva. */
@Component({
  selector: 'app-reserva-form',
  imports: [FormsModule, RouterLink, Icone],
  templateUrl: './reserva-form.html',
  styleUrl: './reserva-form.css',
})
export class ReservaForm {
  private readonly api = inject(Api);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  protected readonly sessao = inject(Sessao);
  private readonly resumoErros = viewChild<ElementRef<HTMLElement>>('resumoErros');
  private readonly resumoSucesso = viewChild<ElementRef<HTMLElement>>('resumoSucesso');

  protected readonly STATUS = STATUS_ROTULO;
  protected readonly dataHora = dataHora;

  protected readonly ambientes = signal<Ambiente[]>([]);
  protected readonly disposicoes = signal<Disposicao[]>([]);
  protected readonly recursos = signal<Recurso[]>([]);
  protected readonly detalhe = signal<ReservaDetalhe | null>(null);
  /** Reserva antiga de um ambiente hoje inativo: mantém o nome visível no seletor. */
  protected readonly ambienteInativo = computed(() => {
    const d = this.detalhe();
    return d?.ambienteId != null && this.ambientes().length > 0 && !this.ambientes().some((a) => a.id === d.ambienteId);
  });
  protected readonly erros = signal<Erro[]>([]);
  protected readonly errosPrevia = signal<Erro[]>([]);
  protected readonly mensagem = signal('');
  protected readonly salvando = signal(false);
  protected readonly pronto = signal(false);

  // Modelo do formulário (mutado pelos eventos do template).
  protected ambienteId: number | null = null;
  protected complemento = '';
  protected finalidade = '';
  protected participantes: number | null = null;
  protected disposicaoId: number | null = null;
  protected periodos: PeriodoForm[] = [];
  protected selecao: Record<number, Selecao> = {};

  protected readonly somenteLeitura = computed(() => {
    const d = this.detalhe();
    return d != null && !d.podeEditar;
  });

  protected readonly grupos = computed(() => {
    const mapa = new Map<string, Recurso[]>();
    for (const r of this.recursos()) {
      mapa.set(r.grupo, [...(mapa.get(r.grupo) ?? []), r]);
    }
    return [...mapa.entries()].map(([grupo, itens]) => ({ grupo, itens }));
  });

  constructor() {
    this.api.ambientes().subscribe((a) => this.ambientes.set(a));
    this.api.disposicoes().subscribe((d) => this.disposicoes.set(d));
    this.route.paramMap.subscribe((pm) => {
      const id = pm.get('id');
      this.erros.set([]);
      this.errosPrevia.set([]);
      if (id) {
        this.carregarReserva(Number(id), this.route.snapshot.queryParamMap.get('salvo') === '1');
      } else {
        this.novaReserva();
      }
    });
  }

  private novaReserva(): void {
    const q = this.route.snapshot.queryParamMap;
    this.detalhe.set(null);
    this.mensagem.set('');
    this.ambienteId = q.get('ambiente') ? Number(q.get('ambiente')) : null;
    this.complemento = '';
    this.finalidade = '';
    this.participantes = null;
    this.disposicaoId = null;
    this.selecao = {};
    const inicio = q.get('inicio');
    if (inicio) {
      const fim = paraData(inicio);
      fim.setHours(fim.getHours() + 1);
      this.periodos = [{ inicio, termino: isoDataHora(fim) }];
    } else {
      this.periodos = [{ inicio: '', termino: '' }];
    }
    this.carregarRecursos();
    this.pronto.set(true);
    if (inicio) {
      this.previa();
    }
  }

  private carregarReserva(id: number, salvo: boolean): void {
    this.api.reserva(id).subscribe({
      next: (d) => {
        this.preencher(d);
        if (salvo) {
          this.mensagem.set(`Reserva #${d.id} salva. Os setores envolvidos foram notificados por e-mail (simulado).`);
          setTimeout(() => this.resumoSucesso()?.nativeElement.focus());
        }
      },
      error: (e) => {
        this.pronto.set(true);
        this.erros.set(errosDaResposta(e));
      },
    });
  }

  private preencher(d: ReservaDetalhe): void {
    this.detalhe.set(d);
    this.ambienteId = d.ambienteId;
    this.complemento = d.complementoAmbiente ?? '';
    this.finalidade = d.finalidade;
    this.participantes = d.qtdParticipantes;
    this.disposicaoId = d.disposicaoId;
    this.periodos = d.periodos.map((p) => ({ inicio: p.inicio.substring(0, 16), termino: p.termino.substring(0, 16) }));
    this.selecao = {};
    for (const r of d.recursos) {
      this.selecao[r.recursoId] = { marcado: true, quantidade: r.quantidade };
    }
    this.carregarRecursos();
    this.pronto.set(true);
  }

  protected aoTrocarAmbiente(): void {
    if (this.ambienteId == null) {
      this.disposicaoId = null;
    }
    this.carregarRecursos();
    this.previa();
  }

  /** RN9: a lista de recursos depende do ambiente escolhido. */
  private carregarRecursos(): void {
    this.api.recursos(this.ambienteId).subscribe((rs) => {
      this.recursos.set(rs);
      const ids = new Set(rs.map((r) => r.id));
      for (const k of Object.keys(this.selecao)) {
        if (!ids.has(Number(k))) {
          delete this.selecao[Number(k)];
        }
      }
    });
  }

  protected sel(r: Recurso): Selecao {
    return (this.selecao[r.id] ??= { marcado: false, quantidade: null });
  }

  protected marcar(r: Recurso, marcado: boolean): void {
    const s = this.sel(r);
    s.marcado = marcado;
    if (marcado && r.limitado && !s.quantidade) {
      s.quantidade = 1;
    }
    this.previa();
  }

  protected adicionarPeriodo(): void {
    const ultimo = this.periodos[this.periodos.length - 1];
    this.periodos = [...this.periodos, { inicio: ultimo?.inicio ?? '', termino: ultimo?.termino ?? '' }];
  }

  protected removerPeriodo(i: number): void {
    this.periodos = this.periodos.filter((_, idx) => idx !== i);
    this.previa();
  }

  protected aoMudarInicio(p: PeriodoForm): void {
    if (p.inicio && (!p.termino || p.termino <= p.inicio)) {
      const fim = paraData(p.inicio);
      fim.setHours(fim.getHours() + 1);
      p.termino = isoDataHora(fim);
    }
    this.previa();
  }

  private montar(): ReservaInput {
    return {
      ambienteId: this.ambienteId,
      complementoAmbiente: this.complemento || null,
      finalidade: this.finalidade,
      qtdParticipantes: this.participantes,
      disposicaoId: this.ambienteId == null ? null : this.disposicaoId,
      periodos: this.periodos.map((p) => ({ inicio: p.inicio || (null as unknown as string), termino: p.termino || (null as unknown as string) })),
      recursos: this.recursos()
        .filter((r) => this.selecao[r.id]?.marcado)
        .map((r) => ({ recursoId: r.id, quantidade: r.limitado ? this.selecao[r.id].quantidade : null })),
    };
  }

  /** F2: verificação antecipada de conflitos a cada escolha de período ou recurso. */
  protected previa(): void {
    if (this.somenteLeitura() || !this.periodos.some((p) => p.inicio && p.termino)) {
      this.errosPrevia.set([]);
      return;
    }
    this.api.validar(this.montar(), this.detalhe()?.id ?? null).subscribe({
      next: (es) => this.errosPrevia.set(es.filter((e) => REGRAS_PREVIA.includes(e.regra))),
      error: () => this.errosPrevia.set([]),
    });
  }

  protected errosDoPeriodo(i: number): Erro[] {
    const prefixos = [`Período ${i + 1}:`, `Período ${i + 1} `, `no período ${i + 1} `];
    return this.errosPrevia().filter((e) => prefixos.some((p) => e.mensagem.includes(p)));
  }

  protected errosGerais(): Erro[] {
    return this.errosPrevia().filter((e) => !this.periodos.some((_, i) => this.errosDoPeriodo(i).includes(e)));
  }

  /** RN7: o servidor refaz toda a verificação ao salvar. */
  protected salvar(): void {
    this.salvando.set(true);
    this.erros.set([]);
    this.mensagem.set('');
    const d = this.detalhe();
    const req = d ? this.api.alterar(d.id, this.montar()) : this.api.criar(this.montar());
    req.subscribe({
      next: (r) => {
        this.salvando.set(false);
        this.router.navigate(['/reserva', r.id], { queryParams: { salvo: 1 } });
        if (d) {
          this.carregarReserva(d.id, true);
        }
      },
      error: (e) => this.falhou(e),
    });
  }

  protected cancelarReserva(): void {
    const d = this.detalhe();
    if (!d || !window.confirm(`Confirma o cancelamento da reserva #${d.id}? Os setores envolvidos serão avisados.`)) {
      return;
    }
    this.salvando.set(true);
    this.erros.set([]);
    this.api.cancelar(d.id).subscribe({
      next: (nd) => {
        this.salvando.set(false);
        this.preencher(nd);
        this.mensagem.set(`Reserva #${nd.id} cancelada. Os setores envolvidos foram notificados.`);
        setTimeout(() => this.resumoSucesso()?.nativeElement.focus());
      },
      error: (e) => this.falhou(e),
    });
  }

  private falhou(e: { status?: number; error?: unknown }): void {
    this.salvando.set(false);
    this.erros.set(errosDaResposta(e));
    setTimeout(() => this.resumoErros()?.nativeElement.focus());
  }

  protected imagemDisposicao(): Disposicao | undefined {
    return this.disposicoes().find((d) => d.id === this.disposicaoId);
  }
}
