package br.mp.mpf.sisgares.dominio;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Porta de leitura usada pelo {@link RecursoValidator}. Implementada com JDBC e, nos testes, em memória. */
public interface DadosRecurso {

    /** Recursos oferecidos na unidade (os da unidade e os sem unidade), ativos e inativos. */
    List<RecursoResumo> recursos(long unidadeId);

    Map<Long, GrupoInfo> grupos();

    /** Ícones que podem ser escolhidos: os do sistema e os enviados pelo administrador. */
    Set<String> icones();

    /** Todos os ambientes da unidade, ativos e inativos. */
    List<AmbienteInfo> ambientes(long unidadeId);

    /** Períodos de reservas não canceladas que pedem o recurso e terminam depois de {@code agora}. */
    List<UsoRecurso> usosNaoTranscorridos(long recursoId, LocalDateTime agora);
}
