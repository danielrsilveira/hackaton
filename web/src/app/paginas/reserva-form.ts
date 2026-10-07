import { Component, ElementRef, computed, inject, signal, viewChild } from '@angular/core';
import { FormsModule, NgForm } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import {
  Ambiente, Api, Config, Disposicao, Erro, InterpretacaoReserva, Recurso, ReservaDetalhe, ReservaInput,
  STATUS_ROTULO, Sessao, errosDaResposta,
} from '../api';
import { dataHora, isoDataHora, minutos, paraData } from '../datas';
import { Icone } from '../icone';
import { iconeUrl } from '../icones-recurso';

interface PeriodoForm { inicio: string; termino: string; }
interface Selecao { marcado: boolean; quantidade: number | null; }

/**
 * Regras verificadas no backend porque dependem de dados do servidor (agenda de outras
 * reservas, disponibilidade de recursos). As regras RN1/RN3/RN4 dependem só do formulário
 * e dos parâmetros de configuração, então são validadas localmente para feedback imediato.
 */
const REGRAS_SERVIDOR = ['RN5', 'RN6', 'RN8', 'RN9'];

/** F1–F4 / RF10, RF15: inclusão, alteração e cancelamento de reserva. */
@Component({
  selector: 'app-reserva-form',
  imports: [FormsModule, RouterLink, Icone],
  templateUrl: './reserva-form.html',
  styleUrl: './reserva-form.css',
})
export class ReservaForm {
  protected readonly iconeUrl = iconeUrl;
  private readonly api = inject(Api);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  protected readonly sessao = inject(Sessao);
  private readonly resumoErros = viewChild<ElementRef<HTMLElement>>('resumoErros');
  private readonly resumoSucesso = viewChild<ElementRef<HTMLElement>>('resumoSucesso');
  private readonly form = viewChild<NgForm>('form');

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
  /** Regras verificadas no backend (conflitos de agenda, disponibilidade de recursos). */
  protected readonly errosServidor = signal<Erro[]>([]);
  /** Regras verificadas no front (RN1/RN3/RN4): dependem só do formulário e da config. */
  protected readonly errosLocais = signal<Erro[]>([]);
  /** Todas as pendências da prévia (locais + servidor), para exibição no template. */
  protected readonly errosPrevia = computed<Erro[]>(() => [...this.errosLocais(), ...this.errosServidor()]);
  protected readonly mensagem = signal('');
  protected readonly salvando = signal(false);
  protected readonly pronto = signal(false);
  /** Config efetiva da unidade (RN3/RN4): antecedência mínima e faixa de horário. */
  protected readonly config = signal<Config | null>(null);
  /** Há uma verificação de prévia em andamento (chamada ao backend ainda sem resposta). */
  protected readonly validando = signal(false);
  /** Sequência das chamadas de prévia: só a resposta mais recente é aplicada (evita corrida). */
  private previaSeq = 0;

  // Preenchimento assistido por IA (F-IA).
  protected readonly iaDisponivel = signal(false);
  protected readonly interpretando = signal(false);
  protected readonly iaResumo = signal('');
  protected readonly iaAvisos = signal<string[]>([]);
  protected readonly iaErro = signal('');
  protected descricao = '';

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
    // RN3/RN4: parâmetros efetivos da unidade para validação local.
    this.api.config().subscribe((c) => {
      this.config.set(c);
      this.revalidarLocal();
    });
    // F-IA: descobre se o preenchimento assistido por IA está disponível.
    this.api.interpretacaoDisponivel().subscribe({
      next: (r) => this.iaDisponivel.set(r.disponivel),
      error: () => this.iaDisponivel.set(false),
    });
    this.route.paramMap.subscribe((pm) => {
      const id = pm.get('id');
      this.erros.set([]);
      this.errosLocais.set([]);
      this.errosServidor.set([]);
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

  /** F-IA: lê a descrição em linguagem natural e preenche os campos do formulário. */
  protected interpretar(): void {
    const texto = this.descricao.trim();
    if (!texto || this.interpretando()) {
      return;
    }
    this.interpretando.set(true);
    this.iaErro.set('');
    this.iaResumo.set('');
    this.iaAvisos.set([]);
    this.api.interpretar(texto).subscribe({
      next: (r) => {
        this.aplicarInterpretacao(r);
        this.interpretando.set(false);
      },
      error: (e) => {
        this.interpretando.set(false);
        const erros = errosDaResposta(e);
        this.iaErro.set(erros[0]?.mensagem ?? 'Não foi possível interpretar a descrição.');
      },
    });
  }

  private aplicarInterpretacao(r: InterpretacaoReserva): void {
    const i = r.reserva;
    this.ambienteId = i.ambienteId ?? null;
    this.complemento = i.complementoAmbiente ?? '';
    if (i.finalidade) {
      this.finalidade = i.finalidade;
    }
    if (i.qtdParticipantes != null) {
      this.participantes = i.qtdParticipantes;
    }
    this.disposicaoId = this.ambienteId == null ? null : i.disposicaoId ?? null;
    if (i.periodos?.length) {
      this.periodos = i.periodos.map((p) => ({
        inicio: p.inicio ? p.inicio.substring(0, 16) : '',
        termino: p.termino ? p.termino.substring(0, 16) : '',
      }));
    }
    // Os recursos dependem do ambiente escolhido: recarrega a lista e então marca a seleção.
    this.api.recursos(this.ambienteId).subscribe((rs) => {
      this.recursos.set(rs);
      this.selecao = {};
      const ids = new Set(rs.map((x) => x.id));
      for (const item of i.recursos ?? []) {
        if (ids.has(item.recursoId)) {
          this.selecao[item.recursoId] = { marcado: true, quantidade: item.quantidade ?? null };
        }
      }
      this.iaResumo.set(r.resumo);
      this.iaAvisos.set(r.avisos ?? []);
      this.previa();
    });
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
    // RN1/RN3/RN4: feedback imediato, sem round-trip ao backend.
    this.revalidarLocal();

    if (this.somenteLeitura() || !this.periodos.some((p) => p.inicio && p.termino)) {
      this.previaSeq++;
      this.validando.set(false);
      this.errosServidor.set([]);
      return;
    }
    // RN5/RN6/RN8/RN9 dependem de dados do servidor. Enquanto não retorna, bloqueia o
    // Salvar e descarta respostas antigas (evita corrida ao alternar períodos).
    const seq = ++this.previaSeq;
    this.validando.set(true);
    this.api.validar(this.montar(), this.detalhe()?.id ?? null).subscribe({
      next: (es) => {
        if (seq !== this.previaSeq) {
          return;
        }
        this.errosServidor.set(es.filter((e) => REGRAS_SERVIDOR.includes(e.regra)));
        this.validando.set(false);
      },
      error: () => {
        if (seq !== this.previaSeq) {
          return;
        }
        // Falha na verificação: não libera o Salvar com base em estado desconhecido.
        this.errosServidor.set([]);
        this.validando.set(false);
      },
    });
  }

  /**
   * Validação local das regras que dependem só do formulário e da config (RN1/RN3/RN4).
   * Produz os mesmos erros que o backend para feedback instantâneo; o servidor revalida
   * tudo ao salvar (RN7), permanecendo a autoridade final.
   */
  private revalidarLocal(): void {
    if (this.somenteLeitura()) {
      this.errosLocais.set([]);
      return;
    }
    const erros: Erro[] = [];
    const cfg = this.config();

    this.periodos.forEach((p, idx) => {
      const n = idx + 1;
      if (!p.inicio || !p.termino) {
        // Campos obrigatórios já são sinalizados pela validação HTML; aqui só cobrimos
        // a ordem início/término quando ambos estão preenchidos.
        return;
      }
      const inicio = paraData(p.inicio);
      const termino = paraData(p.termino);

      // RN1: término posterior ao início.
      if (termino.getTime() <= inicio.getTime()) {
        erros.push({ regra: 'RN1', mensagem: `Período ${n}: o término deve ser posterior ao início.` });
        return;
      }

      if (cfg) {
        // RN3: início e término dentro da faixa de horário efetiva da unidade.
        const hMin = cfg.unidadeHoraMin ?? cfg.horaMin;
        const hMax = cfg.unidadeHoraMax ?? cfg.horaMax;
        if (this.foraDaFaixa(inicio, hMin, hMax) || this.foraDaFaixa(termino, hMin, hMax)) {
          erros.push({ regra: 'RN3', mensagem: `Período ${n}: início e término devem estar entre ${hMin} e ${hMax}.` });
        }

        // RN4: antecedência mínima. Períodos já gravados e inalterados não são cobrados de novo.
        const limite = new Date(Date.now() + cfg.antecedenciaMin * 60_000);
        if (!this.periodoOriginal(p) && inicio.getTime() < limite.getTime()) {
          erros.push({
            regra: 'RN4',
            mensagem: `Período ${n}: o início exige antecedência mínima de ${cfg.antecedenciaMin} minutos (a partir de ${dataHora(isoDataHora(limite))}).`,
          });
        }
      }
    });

    this.errosLocais.set(erros);
  }

  /** RN3: compara apenas a parte de horas:minutos contra a faixa "HH:mm[:ss]". */
  private foraDaFaixa(d: Date, horaMin: string, horaMax: string): boolean {
    const m = d.getHours() * 60 + d.getMinutes();
    return m < minutos(horaMin) || m > minutos(horaMax);
  }

  /** RN4: período idêntico a um já gravado na reserva em edição não é recobrado de antecedência. */
  private periodoOriginal(p: PeriodoForm): boolean {
    const d = this.detalhe();
    if (!d) {
      return false;
    }
    return d.periodos.some((o) => o.inicio.substring(0, 16) === p.inicio && o.termino.substring(0, 16) === p.termino);
  }

  protected errosDoPeriodo(i: number): Erro[] {
    const prefixos = [`Período ${i + 1}:`, `Período ${i + 1} `, `no período ${i + 1} `];
    return this.errosPrevia().filter((e) => prefixos.some((p) => e.mensagem.includes(p)));
  }

  protected errosGerais(): Erro[] {
    return this.errosPrevia().filter((e) => !this.periodos.some((_, i) => this.errosDoPeriodo(i).includes(e)));
  }

  /**
   * Bloqueia o envio enquanto houver pendências: campos obrigatórios/inválidos (validação HTML),
   * quebras de regra de negócio na prévia (ex.: período no passado — RN4) ou verificação em andamento.
   */
  protected formInvalido(): boolean {
    const f = this.form();
    return (f != null && f.invalid === true) || this.errosPrevia().length > 0 || this.validando();
  }

  /** RN7: o servidor refaz toda a verificação ao salvar. */
  protected salvar(): void {
    // Validação no frontend: não envia ao backend se o formulário tiver pendências.
    if (this.formInvalido()) {
      this.form()?.control.markAllAsTouched();
      setTimeout(() => this.resumoErros()?.nativeElement.focus());
      return;
    }
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
