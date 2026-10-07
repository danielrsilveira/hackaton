import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { Agenda, Ambiente, Api, ItemAgenda } from '../api';
import { Icone } from '../icone';
import { cabecalhoDia, datasConsecutivas, hoje, hora, isoData, isoDataHora, minutos, paraData } from '../datas';

type TipoCelula = 'reservado' | 'margem' | 'passado' | 'antecedencia' | 'livre';

interface Celula {
  tipo: TipoCelula;
  inicio: string;
  item?: ItemAgenda;
  primeira?: boolean;
  ultima?: boolean;
  fds?: boolean;
}

const MARGEM_MS = 30 * 60 * 1000;
const SLOT_MS = 30 * 60 * 1000;

/** F7 / RF16: grade de datas x horários (30 em 30 min) do ambiente selecionado. */
@Component({
  selector: 'app-painel',
  imports: [FormsModule, RouterLink, Icone],
  templateUrl: './painel.html',
  styleUrl: './painel.css',
})
export class Painel {
  private readonly api = inject(Api);

  protected readonly ambientes = signal<Ambiente[]>([]);
  protected readonly ambienteId = signal<number | null>(null);
  protected readonly referencia = signal(hoje());
  protected readonly colunas = signal(7);
  protected readonly fimDeSemana = signal(false);
  protected readonly agenda = signal<Agenda | null>(null);
  protected readonly carregando = signal(false);
  protected readonly hora = hora;
  protected readonly cabecalhoDia = cabecalhoDia;

  protected readonly ambienteNome = computed(
    () => this.ambientes().find((a) => a.id === this.ambienteId())?.descricao ?? '',
  );

  protected readonly datas = computed(() =>
    datasConsecutivas(this.referencia(), Math.min(Math.max(this.colunas(), 1), 14), this.fimDeSemana()),
  );

  protected readonly linhas = computed(() => {
    const ag = this.agenda();
    if (!ag) {
      return [];
    }
    const agora = paraData(ag.agora).getTime();
    const limite = agora + ag.antecedenciaMin * 60 * 1000;
    const periodos = ag.periodos.map((p) => ({ p, ini: paraData(p.inicio).getTime(), fim: paraData(p.termino).getTime() }));
    const out: { hora: string; celulas: Celula[] }[] = [];
    for (let m = minutos(ag.horaMin); m + 30 <= minutos(ag.horaMax); m += 30) {
      const rotulo = `${String(Math.floor(m / 60)).padStart(2, '0')}:${String(m % 60).padStart(2, '0')}`;
      const celulas = this.datas().map((d): Celula => {
        const s = new Date(d);
        s.setHours(Math.floor(m / 60), m % 60, 0, 0);
        const ini = s.getTime();
        const fim = ini + SLOT_MS;
        const inicio = isoDataHora(s);
        const fds = d.getDay() === 0 || d.getDay() === 6;
        const ocupado = periodos.find((x) => x.ini < fim && ini < x.fim);
        if (ocupado) {
          return {
            tipo: 'reservado', inicio, item: ocupado.p, fds,
            primeira: ocupado.ini >= ini || m === minutos(ag.horaMin),
            ultima: ocupado.fim <= fim || m + 60 > minutos(ag.horaMax),
          };
        }
        if (periodos.some((x) => x.ini - MARGEM_MS < fim && ini < x.fim + MARGEM_MS)) {
          return { tipo: 'margem', inicio, fds };
        }
        if (ini < agora) {
          return { tipo: 'passado', inicio, fds };
        }
        if (ini < limite) {
          return { tipo: 'antecedencia', inicio, fds };
        }
        return { tipo: 'livre', inicio, fds };
      });
      out.push({ hora: rotulo, celulas });
    }
    return out;
  });

  constructor() {
    this.api.ambientes().subscribe((as) => {
      this.ambientes.set(as);
      if (as.length) {
        this.ambienteId.set(as.find((a) => a.id === 1)?.id ?? as[0].id);
        this.carregar();
      }
    });
  }

  protected carregar(): void {
    const id = this.ambienteId();
    const datas = this.datas();
    if (id == null || !datas.length) {
      return;
    }
    const ultimo = datas[datas.length - 1];
    const dias = Math.round((ultimo.getTime() - paraData(isoData(datas[0])).getTime()) / 86400000) + 1;
    this.carregando.set(true);
    this.api.agenda(id, isoData(datas[0]), dias).subscribe({
      next: (a) => {
        this.agenda.set(a);
        this.carregando.set(false);
      },
      error: () => this.carregando.set(false),
    });
  }

  protected alterar(campo: 'ambiente' | 'referencia' | 'colunas' | 'fimDeSemana', valor: unknown): void {
    if (campo === 'ambiente') this.ambienteId.set(Number(valor));
    if (campo === 'referencia' && valor) this.referencia.set(String(valor));
    if (campo === 'colunas') this.colunas.set(Number(valor) || 1);
    if (campo === 'fimDeSemana') this.fimDeSemana.set(Boolean(valor));
    this.carregar();
  }

  /** Navegação rápida: avança/recua o número de colunas exibido, ou volta para hoje. */
  protected navegar(direcao: -1 | 0 | 1): void {
    if (direcao === 0) {
      this.referencia.set(hoje());
    } else {
      const d = paraData(this.referencia());
      d.setDate(d.getDate() + direcao * (this.fimDeSemana() ? this.colunas() : Math.ceil((this.colunas() * 7) / 5)));
      this.referencia.set(isoData(d));
    }
    this.carregar();
  }

  protected ehHoje(d: Date): boolean {
    return isoData(d) === hoje();
  }

  protected ehFimDeSemana(d: Date): boolean {
    const dia = d.getDay();
    return dia === 0 || dia === 6;
  }

  protected descricaoCurta(c: Celula): string {
    const i = c.item!;
    return `${hora(i.inicio)}–${hora(i.termino)}`;
  }

  protected descricaoReserva(c: Celula): string {
    const i = c.item!;
    const outro = i.ambienteId !== this.ambienteId() ? ` – ${i.ambiente}` : '';
    const fin = i.finalidade ? `: ${i.finalidade}` : '';
    return `Reservado ${hora(i.inicio)}–${hora(i.termino)}${outro}${fin}`;
  }
}
