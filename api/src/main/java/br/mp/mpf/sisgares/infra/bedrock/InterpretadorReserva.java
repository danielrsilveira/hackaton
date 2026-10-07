package br.mp.mpf.sisgares.infra.bedrock;

import java.time.LocalDateTime;
import java.util.List;

import br.mp.mpf.sisgares.dominio.InterpretacaoReserva;

/**
 * Lê uma descrição em linguagem natural e devolve os campos de uma reserva (F-IA).
 * Simulado atrás de interface, no mesmo padrão de SNP e e-mail: trocável por outro provedor.
 */
public interface InterpretadorReserva {

    /** Opção de ambiente oferecida ao modelo (só os ativos da unidade do usuário). */
    record OpcaoAmbiente(long id, String descricao) {
    }

    /** Opção de disposição de ambiente. */
    record OpcaoDisposicao(long id, String descricao) {
    }

    /** Opção de recurso/serviço; limitado indica que aceita quantidade. */
    record OpcaoRecurso(long id, String descricao, boolean limitado, String grupo) {
    }

    /** Catálogo válido para ancorar a resposta e evitar que o modelo invente ids. */
    record Catalogo(List<OpcaoAmbiente> ambientes, List<OpcaoDisposicao> disposicoes,
            List<OpcaoRecurso> recursos) {
    }

    /** true quando o recurso está configurado e habilitado (do contrário o front esconde o campo). */
    boolean habilitado();

    /**
     * @param descricao texto livre do solicitante
     * @param agora     momento atual (datas relativas como "amanhã" são resolvidas a partir daqui)
     * @param catalogo  ambientes/disposições/recursos válidos
     */
    InterpretacaoReserva interpretar(String descricao, LocalDateTime agora, Catalogo catalogo);
}
