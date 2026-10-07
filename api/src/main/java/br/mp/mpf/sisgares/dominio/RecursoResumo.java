package br.mp.mpf.sisgares.dominio;

/** Recurso como visto pelas regras do cadastro (RF06). {@code unidadeId} nulo = oferecido em todas as unidades. */
public record RecursoResumo(long id, String descricao, long grupoId, boolean limitado, int disponibilidade, boolean ativo,
        Long unidadeId) {
}
