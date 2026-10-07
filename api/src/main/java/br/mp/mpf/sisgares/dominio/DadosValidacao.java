package br.mp.mpf.sisgares.dominio;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Porta de leitura usada pelo validador. Implementada com JDBC e, nos testes, em memória. */
public interface DadosValidacao {

    /** Ambiente pelo id, ativo ou não, de qualquer unidade. */
    Optional<AmbienteInfo> ambiente(long ambienteId);

    /** O próprio ambiente, seus ancestrais e seus descendentes (RN6). */
    Set<Long> ambientesRelacionados(long ambienteId);

    /** Períodos de reservas não canceladas desses ambientes que cruzam [de, ate), exceto a reserva excluída. */
    List<PeriodoOcupado> periodosOcupados(Set<Long> ambienteIds, LocalDateTime de, LocalDateTime ate,
            Long excluirReservaId);

    Optional<RecursoInfo> recurso(long recursoId);

    /** Soma das quantidades do recurso em outras reservas não canceladas com período que cruza [inicio, termino). */
    int quantidadeReservada(long recursoId, LocalDateTime inicio, LocalDateTime termino, Long excluirReservaId);
}
