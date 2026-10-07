package br.mp.mpf.sisgares.dominio;

/** Setor envolvido como visto pelas regras. {@code unidadeId} nulo = vale para todas as unidades. */
public record SetorInfo(long id, String descricao, boolean ativo, Long unidadeId) {
}
