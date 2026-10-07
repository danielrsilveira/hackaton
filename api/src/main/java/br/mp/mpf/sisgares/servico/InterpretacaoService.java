package br.mp.mpf.sisgares.servico;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import br.mp.mpf.sisgares.dominio.InterpretacaoReserva;
import br.mp.mpf.sisgares.dominio.RegraException;
import br.mp.mpf.sisgares.infra.CadastroRepository;
import br.mp.mpf.sisgares.infra.Usuario;
import br.mp.mpf.sisgares.infra.bedrock.InterpretadorReserva;
import br.mp.mpf.sisgares.infra.bedrock.InterpretadorReserva.Catalogo;
import br.mp.mpf.sisgares.infra.bedrock.InterpretadorReserva.OpcaoAmbiente;
import br.mp.mpf.sisgares.infra.bedrock.InterpretadorReserva.OpcaoDisposicao;
import br.mp.mpf.sisgares.infra.bedrock.InterpretadorReserva.OpcaoRecurso;

/**
 * F-IA: lê a descrição em linguagem natural e sugere os campos da reserva. Nada é gravado;
 * o solicitante confere e ajusta no formulário. O interpretador é opcional: quando o Bedrock
 * está desabilitado, o recurso fica indisponível (o front esconde o campo).
 */
@Service
public class InterpretacaoService {

    private final Optional<InterpretadorReserva> interpretador;
    private final CadastroRepository cadastros;
    private final Clock clock;

    public InterpretacaoService(Optional<InterpretadorReserva> interpretador, CadastroRepository cadastros,
            Clock clock) {
        this.interpretador = interpretador;
        this.cadastros = cadastros;
        this.clock = clock;
    }

    /** true quando há um interpretador habilitado (controla a exibição do campo no front). */
    public boolean disponivel() {
        return interpretador.map(InterpretadorReserva::habilitado).orElse(false);
    }

    public InterpretacaoReserva interpretar(String descricao, Usuario usuario) {
        InterpretadorReserva ia = interpretador
                .orElseThrow(() -> new RegraException("IA", "Preenchimento automático indisponível no momento."));

        long unidade = usuario.unidadeId();
        List<OpcaoAmbiente> ambientes = cadastros.ambientes(unidade).stream()
                .map(a -> new OpcaoAmbiente(a.id(), a.descricao())).toList();
        List<OpcaoDisposicao> disposicoes = cadastros.disposicoes().stream()
                .map(d -> new OpcaoDisposicao(d.id(), d.descricao())).toList();
        // Catálogo completo da unidade (sem filtrar por ambiente): o modelo escolhe ambiente e recurso juntos.
        List<OpcaoRecurso> recursos = cadastros.recursosDisponiveis(null, unidade).stream()
                .map(r -> new OpcaoRecurso(r.id(), r.descricao(), r.limitado(), r.grupo())).toList();

        return ia.interpretar(descricao, LocalDateTime.now(clock), new Catalogo(ambientes, disposicoes, recursos));
    }
}
