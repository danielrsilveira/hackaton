import { HttpClient, HttpInterceptorFn } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';

// ---- Modelos (espelham os records da API) ----

export type Perfil = 'SOLICITANTE' | 'ADMIN' | 'ATENDENTE';
export type Status = 'PREVISTA' | 'EM_ANDAMENTO' | 'TRANSCORRIDA' | 'CANCELADA';

export interface Usuario {
  id: number;
  nome: string;
  email: string;
  perfil: Perfil;
  unidadeId: number;
  envolvidoId: number | null;
}
export interface Ambiente { id: number; descricao: string; idPai: number | null; ativo: boolean; }
export interface Disposicao { id: number; descricao: string; iconeArquivo: string; }
export interface Recurso {
  id: number; descricao: string; limitado: boolean; disponibilidade: number;
  iconeArquivo: string; grupo: string; grupoOrdem: number | null;
}
export interface Setor { id: number; descricao: string; email: string; emailsLista: string | null; }
export interface Config {
  antecedenciaMin: number; horaMin: string; horaMax: string; snpEndpoint: string;
  unidadeHoraMin: string | null; unidadeHoraMax: string | null;
}
export interface Periodo { inicio: string; termino: string; }
export interface ItemRecurso { recursoId: number; quantidade: number | null; }
export interface ReservaInput {
  ambienteId: number | null; complementoAmbiente: string | null; finalidade: string;
  qtdParticipantes: number | null; disposicaoId: number | null; periodos: Periodo[]; recursos: ItemRecurso[];
}
export interface Erro { regra: string; mensagem: string; }
export interface InterpretacaoReserva { reserva: ReservaInput; resumo: string; avisos: string[]; }
export interface RecursoDaReserva {
  recursoId: number; descricao: string; iconeArquivo: string; limitado: boolean; quantidade: number | null; grupo: string;
}
export interface PedidoSnp {
  id: number; reservaId: number; setor: string; codServico: string; origem: string; numero: string; url: string; dthr: string;
}
export interface ReservaDetalhe {
  id: number; solicitanteId: number; solicitante: string; status: Status; ultimaAlteracao: string; versao: number;
  podeEditar: boolean; podeCancelar: boolean; ambienteId: number | null; ambiente: string | null;
  complementoAmbiente: string | null; finalidade: string; qtdParticipantes: number; disposicaoId: number | null;
  disposicao: string | null; periodos: Periodo[]; recursos: RecursoDaReserva[]; pedidosSnp: PedidoSnp[];
}
export interface ItemAgenda {
  reservaId: number; ambienteId: number; ambiente: string; inicio: string; termino: string;
  propria: boolean; podeAbrir: boolean; finalidade: string | null;
}
export interface Agenda { agora: string; antecedenciaMin: number; horaMin: string; horaMax: string; periodos: ItemAgenda[]; }
export interface Card {
  reservaId: number; status: Status; finalidade: string; solicitante: string; ambiente: string; qtdParticipantes: number;
  periodos: Periodo[]; recursos: RecursoDaReserva[]; pedidosSnp: PedidoSnp[];
}
export interface Notificacao {
  id: number; reservaId: number; setor: string; destinatarios: string; tipo: string; assunto: string; html: string; dthr: string;
}
export interface MinhaReserva { id: number; finalidade: string; ambiente: string; inicio: string; termino: string; status: Status; }

export const STATUS_ROTULO: Record<Status, string> = {
  PREVISTA: 'Prevista',
  EM_ANDAMENTO: 'Em andamento',
  TRANSCORRIDA: 'Transcorrida',
  CANCELADA: 'Cancelada',
};

// ---- Sessão simulada (sem autenticação real) ----

const CHAVE_USUARIO = 'sisgares.usuarioId';

@Injectable({ providedIn: 'root' })
export class Sessao {
  readonly usuarioId = signal<number>(Number(localStorage.getItem(CHAVE_USUARIO) ?? '1'));
  readonly usuario = signal<Usuario | null>(null);
  readonly gestor = computed(() => ['ADMIN', 'ATENDENTE'].includes(this.usuario()?.perfil ?? ''));
  readonly admin = computed(() => this.usuario()?.perfil === 'ADMIN');

  trocar(id: number): void {
    localStorage.setItem(CHAVE_USUARIO, String(id));
    this.usuarioId.set(id);
  }
}

/** Envia o usuário simulado em todas as chamadas à API. */
export const usuarioInterceptor: HttpInterceptorFn = (req, next) => {
  const sessao = inject(Sessao);
  return next(req.clone({ setHeaders: { 'X-Usuario-Id': String(sessao.usuarioId()) } }));
};

// ---- Cliente HTTP (rotas relativas: proxy local / CloudFront na AWS) ----

@Injectable({ providedIn: 'root' })
export class Api {
  private readonly http = inject(HttpClient);

  usuarios() { return this.http.get<Usuario[]>('/api/usuarios'); }
  ambientes() { return this.http.get<Ambiente[]>('/api/ambientes'); }
  disposicoes() { return this.http.get<Disposicao[]>('/api/disposicoes'); }
  recursos(ambienteId: number | null) {
    const q = ambienteId == null ? '' : `?ambienteId=${ambienteId}`;
    return this.http.get<Recurso[]>(`/api/recursos${q}`);
  }
  setores() { return this.http.get<Setor[]>('/api/setores'); }
  config() { return this.http.get<Config>('/api/config'); }
  salvarConfig(c: Config) { return this.http.put<Config>('/api/config', c); }

  validar(r: ReservaInput, reservaId: number | null) {
    const q = reservaId == null ? '' : `?reservaId=${reservaId}`;
    return this.http.post<Erro[]>(`/api/reservas/validar${q}`, r);
  }
  criar(r: ReservaInput) { return this.http.post<{ id: number }>('/api/reservas', r); }
  interpretacaoDisponivel() {
    return this.http.get<{ disponivel: boolean }>('/api/reservas/interpretacao-disponivel');
  }
  interpretar(texto: string) {
    return this.http.post<InterpretacaoReserva>('/api/reservas/interpretar', { texto });
  }
  reserva(id: number) { return this.http.get<ReservaDetalhe>(`/api/reservas/${id}`); }
  alterar(id: number, r: ReservaInput) { return this.http.put<ReservaDetalhe>(`/api/reservas/${id}`, r); }
  cancelar(id: number) { return this.http.post<ReservaDetalhe>(`/api/reservas/${id}/cancelar`, {}); }
  minhas() { return this.http.get<MinhaReserva[]>('/api/reservas/minhas'); }

  agenda(ambienteId: number, inicio: string, dias: number) {
    return this.http.get<Agenda>(`/api/agenda?ambienteId=${ambienteId}&inicio=${inicio}&dias=${dias}`);
  }
  painelAtendente(inicio: string, dias: number, setorId: number | null) {
    const s = setorId == null ? '' : `&setorId=${setorId}`;
    return this.http.get<Card[]>(`/api/painel-atendente?inicio=${inicio}&dias=${dias}${s}`);
  }
  notificacoes(reservaId: number | null) {
    const q = reservaId == null ? '' : `?reservaId=${reservaId}`;
    return this.http.get<Notificacao[]>(`/api/notificacoes${q}`);
  }
  pedidosSnp() { return this.http.get<PedidoSnp[]>('/api/pedidos-snp'); }
}

/** Erros 422 vêm como [{regra, mensagem}]; outros viram uma mensagem genérica. */
export function errosDaResposta(e: { status?: number; error?: unknown }): Erro[] {
  if (Array.isArray(e.error)) {
    return e.error as Erro[];
  }
  if (e.status === 403) {
    return [{ regra: 'ACESSO', mensagem: 'Seu perfil não tem permissão para esta ação.' }];
  }
  return [{ regra: 'ERRO', mensagem: 'Não foi possível concluir a operação. Tente novamente.' }];
}
