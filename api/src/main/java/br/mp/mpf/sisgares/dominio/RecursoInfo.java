package br.mp.mpf.sisgares.dominio;

import java.util.Set;

/** Dados de um recurso necessários às regras RN8 e RN9. */
public record RecursoInfo(long id, String descricao, boolean limitado, int disponibilidade, boolean ativo,
        Long unidadeId, Set<Long> ambientesVinculados) {
}
