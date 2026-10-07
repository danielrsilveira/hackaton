package br.mp.mpf.sisgares.dominio;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/** Porta de leitura usada pelo {@link AmbienteValidator}. Implementada com JDBC e, nos testes, em memória. */
public interface DadosAmbiente {

    /** Todos os ambientes da unidade, ativos e inativos. */
    List<AmbienteInfo> ambientes(long unidadeId);

    /** Períodos de reservas não canceladas desses ambientes que terminam depois de {@code agora}. */
    List<PeriodoOcupado> periodosNaoTranscorridos(Set<Long> ambienteIds, LocalDateTime agora);
}
