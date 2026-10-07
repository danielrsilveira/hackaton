package br.mp.mpf.sisgares.dominio;

/** Ambiente como visto pelas regras (cadastro RF02 e reserva). */
public record AmbienteInfo(long id, String descricao, Long idPai, boolean ativo, long unidadeId) {
}
