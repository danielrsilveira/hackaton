package br.mp.mpf.sisgares.dominio;

/**
 * Dados enviados no cadastro de recurso (F9/RF06).
 *
 * @param disponibilidade quantidade disponível na unidade; só vale para recurso limitado (RN8)
 * @param unidadeId       unidade macro em que o recurso é oferecido; nulo = todas (RN9)
 */
public record RecursoInput(String descricao, Long grupoId, Boolean limitado, Integer disponibilidade, String iconeArquivo,
        Long unidadeId, Boolean ativo) {
}
