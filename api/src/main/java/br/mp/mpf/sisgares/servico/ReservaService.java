package br.mp.mpf.sisgares.servico;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import br.mp.mpf.sisgares.dominio.DadosValidacao;
import br.mp.mpf.sisgares.dominio.Erro;
import br.mp.mpf.sisgares.dominio.Periodo;
import br.mp.mpf.sisgares.dominio.RegraException;
import br.mp.mpf.sisgares.dominio.ReservaInput;
import br.mp.mpf.sisgares.dominio.ReservaValidator;
import br.mp.mpf.sisgares.dominio.StatusReserva;
import br.mp.mpf.sisgares.dominio.notificacao.ReservaResumo;
import br.mp.mpf.sisgares.dominio.notificacao.TipoNotificacao;
import br.mp.mpf.sisgares.dominio.notificacao.Vinculo;
import br.mp.mpf.sisgares.infra.CadastroRepository;
import br.mp.mpf.sisgares.infra.ReservaRepository;
import br.mp.mpf.sisgares.infra.ReservaRepository.Cabecalho;
import br.mp.mpf.sisgares.infra.ReservaRepository.PedidoSnp;
import br.mp.mpf.sisgares.infra.ReservaRepository.RecursoDaReserva;
import br.mp.mpf.sisgares.infra.Usuario;

/**
 * Inclusão, alteração e cancelamento de reservas. Os métodos de escrita são serializados
 * (synchronized + transação dentro do lock) para que a revalidação ao salvar (RN7) não tenha corrida.
 * Para várias instâncias na AWS, trocar por lock no banco (ex.: pg_advisory_xact_lock).
 */
@Service
public class ReservaService {

    public record ReservaDetalhe(long id, long solicitanteId, String solicitante, StatusReserva status,
            LocalDateTime ultimaAlteracao, int versao, boolean podeEditar, boolean podeCancelar, Long ambienteId,
            String ambiente, String complementoAmbiente, String finalidade, int qtdParticipantes, Long disposicaoId,
            String disposicao, List<Periodo> periodos, List<RecursoDaReserva> recursos, List<PedidoSnp> pedidosSnp) {
    }

    private final ReservaRepository repo;
    private final CadastroRepository cadastros;
    private final DadosValidacao dados;
    private final ReservaValidator validator;
    private final NotificacaoService notificacoes;
    private final TransactionTemplate tx;
    private final Clock clock;

    public ReservaService(ReservaRepository repo, CadastroRepository cadastros, DadosValidacao dados,
            ReservaValidator validator, NotificacaoService notificacoes, TransactionTemplate tx, Clock clock) {
        this.repo = repo;
        this.cadastros = cadastros;
        this.dados = dados;
        this.validator = validator;
        this.notificacoes = notificacoes;
        this.tx = tx;
        this.clock = clock;
    }

    private LocalDateTime agora() {
        return LocalDateTime.now(clock);
    }

    /** F2: verificação antecipada, ao escolher cada período. Não grava nada. */
    public List<Erro> validarPrevia(ReservaInput in, Long reservaId, Usuario u) {
        List<Periodo> originais = reservaId == null ? List.of() : repo.periodos(reservaId);
        long unidade = reservaId == null ? u.unidadeId()
                : repo.cabecalho(reservaId).map(Cabecalho::unidadeId).orElse(u.unidadeId());
        return validator.validar(in, cadastros.regras(unidade), unidade, dados, reservaId, originais);
    }

    public synchronized long criar(ReservaInput in, Usuario u) {
        Long id = tx.execute(s -> {
            List<Erro> erros = validator.validar(in, cadastros.regras(u.unidadeId()), u.unidadeId(), dados, null, List.of());
            if (!erros.isEmpty()) {
                throw new RegraException(erros);
            }
            return repo.inserir(in, u.id(), u.unidadeId(), agora());
        });
        notificacoes.notificar(id, TipoNotificacao.NOVA, null, List.of());
        return id;
    }

    public synchronized void alterar(long id, ReservaInput in, Usuario u) {
        var antes = tx.execute(s -> {
            Cabecalho c = carregarParaEscrita(id, u);
            List<Periodo> atuais = repo.periodos(id);
            List<Erro> erros = new ArrayList<>(validator.validarEdicao(c.cancelada(), atuais));
            if (erros.isEmpty()) {
                erros.addAll(validator.validar(in, cadastros.regras(c.unidadeId()), c.unidadeId(), dados, id, atuais));
            }
            if (!erros.isEmpty()) {
                throw new RegraException(erros);
            }
            var anterior = new Antes(repo.resumo(id, agora()), repo.vinculos(id));
            repo.atualizar(id, in, agora());
            return anterior;
        });
        notificar(id, TipoNotificacao.ALTERADA, antes);
    }

    public synchronized void cancelar(long id, Usuario u) {
        var antes = tx.execute(s -> {
            Cabecalho c = carregarParaEscrita(id, u);
            List<Erro> erros = validator.validarCancelamento(c.cancelada(), repo.periodos(id), cadastros.regras(c.unidadeId()));
            if (!erros.isEmpty()) {
                throw new RegraException(erros);
            }
            var anterior = new Antes(repo.resumo(id, agora()), repo.vinculos(id));
            repo.cancelar(id, agora());
            return anterior;
        });
        notificar(id, TipoNotificacao.CANCELADA, antes);
    }

    /** Versão anterior da reserva, para destacar alterações e avisar setores que deixaram de ser envolvidos. */
    private record Antes(ReservaResumo resumo, List<Vinculo> vinculos) {
    }

    private void notificar(long id, TipoNotificacao tipo, Antes antes) {
        notificacoes.notificar(id, tipo, antes.resumo(), antes.vinculos());
    }

    private Cabecalho carregarParaEscrita(long id, Usuario u) {
        Cabecalho c = repo.cabecalho(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (c.solicitanteId() != u.id() && !u.admin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Apenas o solicitante ou o administrador podem alterar a reserva.");
        }
        return c;
    }

    /** LGPD: detalhes só para o solicitante, administrador e atendentes. */
    public ReservaDetalhe detalhe(long id, Usuario u) {
        Cabecalho c = repo.cabecalho(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        boolean dono = c.solicitanteId() == u.id();
        if (!dono && !u.gestor()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Reserva de outro solicitante.");
        }
        List<Periodo> periodos = repo.periodos(id);
        StatusReserva status = StatusReserva.calcular(c.cancelada(), periodos, agora());
        boolean pode = dono || u.admin();
        boolean podeEditar = pode && validator.validarEdicao(c.cancelada(), periodos).isEmpty();
        boolean podeCancelar = pode
                && validator.validarCancelamento(c.cancelada(), periodos, cadastros.regras(c.unidadeId())).isEmpty();
        return new ReservaDetalhe(id, c.solicitanteId(), c.solicitante(), status, c.dthrUltimaAlteracao(), c.versao(),
                podeEditar, podeCancelar, c.ambienteId(), c.ambiente(), c.complementoAmbiente(), c.finalidade(),
                c.qtdParticipantes(), c.disposicaoId(), c.disposicao(), periodos, repo.recursos(id), repo.pedidos(id));
    }
}
