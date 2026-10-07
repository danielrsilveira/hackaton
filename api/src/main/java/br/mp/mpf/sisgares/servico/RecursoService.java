package br.mp.mpf.sisgares.servico;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import br.mp.mpf.sisgares.dominio.DadosRecurso;
import br.mp.mpf.sisgares.dominio.Erro;
import br.mp.mpf.sisgares.dominio.RecursoInput;
import br.mp.mpf.sisgares.dominio.RecursoValidator;
import br.mp.mpf.sisgares.dominio.RegraException;
import br.mp.mpf.sisgares.dominio.VinculoSetorInput;
import br.mp.mpf.sisgares.dominio.VinculoSetorValidator;
import br.mp.mpf.sisgares.infra.CadastroRepository;
import br.mp.mpf.sisgares.infra.CadastroRepository.VinculoSetor;
import br.mp.mpf.sisgares.infra.RecursoRepository;
import br.mp.mpf.sisgares.infra.RecursoRepository.AmbienteDoRecurso;
import br.mp.mpf.sisgares.infra.RecursoRepository.RecursoCadastro;

/**
 * Cadastro de recursos (F9/RF06) com seus setores (RF07) e ambientes (RF08). Sem exclusão: reservas e
 * vínculos referenciam o recurso, então ele é inativado. Disponibilidade, unidade e ambientes alimentam
 * RN8/RN9, por isso a escrita usa a mesma {@link TravaEscrita} das reservas.
 */
@Service
public class RecursoService {

    private final RecursoRepository recursos;
    private final CadastroRepository cadastros;
    private final DadosRecurso dados;
    private final RecursoValidator validator;
    private final TransactionTemplate tx;
    private final TravaEscrita trava;

    public RecursoService(RecursoRepository recursos, CadastroRepository cadastros, DadosRecurso dados,
            RecursoValidator validator, TransactionTemplate tx, TravaEscrita trava) {
        this.recursos = recursos;
        this.cadastros = cadastros;
        this.dados = dados;
        this.validator = validator;
        this.tx = tx;
        this.trava = trava;
    }

    public List<RecursoCadastro> listar(long unidadeId) {
        return recursos.listar(unidadeId);
    }

    public RecursoCadastro criar(long unidadeId, RecursoInput in) {
        return trava.executar(() -> tx.execute(s -> {
            exigirSemErros(validator.validar(null, in, unidadeId, dados));
            long id = recursos.inserir(in.descricao().strip(), in.grupoId(), Boolean.TRUE.equals(in.limitado()),
                    RecursoValidator.disponibilidade(in), in.iconeArquivo(), in.unidadeId(), ativo(in));
            return recursos.recurso(id, unidadeId).orElseThrow();
        }));
    }

    public RecursoCadastro alterar(long id, long unidadeId, RecursoInput in) {
        return trava.executar(() -> tx.execute(s -> {
            existente(id, unidadeId);
            exigirSemErros(validator.validar(id, in, unidadeId, dados));
            recursos.atualizar(id, in.descricao().strip(), in.grupoId(), Boolean.TRUE.equals(in.limitado()),
                    RecursoValidator.disponibilidade(in), in.iconeArquivo(), in.unidadeId(), ativo(in));
            return recursos.recurso(id, unidadeId).orElseThrow();
        }));
    }

    /** RF07: setores notificados quando o recurso é pedido. */
    public List<VinculoSetor> setores(long id, long unidadeId) {
        existente(id, unidadeId);
        return recursos.setores(id);
    }

    /** RF07: vale para as próximas notificações, como no vínculo com ambientes. */
    public List<VinculoSetor> salvarSetores(long id, long unidadeId, List<VinculoSetorInput> vinculos) {
        return tx.execute(s -> {
            existente(id, unidadeId);
            Set<Long> atuais = recursos.setores(id).stream().map(VinculoSetor::setorId).collect(Collectors.toSet());
            exigirSemErros(VinculoSetorValidator.validar(vinculos, unidadeId, cadastros.setoresInfo(), atuais));
            recursos.substituirSetores(id, vinculos.stream()
                    .map(v -> new VinculoSetorInput(v.setorId(), VinculoSetorValidator.codigo(v.codServicoSnp())))
                    .toList());
            return recursos.setores(id);
        });
    }

    /** RF08: ambientes em que o recurso pode ser pedido (todos, inclusive de outras unidades). */
    public List<AmbienteDoRecurso> ambientes(long id, long unidadeId) {
        existente(id, unidadeId);
        return recursos.ambientes(id);
    }

    /** RF08: substitui os ambientes da unidade; lista vazia = o recurso pode ser pedido em qualquer ambiente. */
    public List<AmbienteDoRecurso> salvarAmbientes(long id, long unidadeId, List<Long> ambienteIds) {
        return trava.executar(() -> tx.execute(s -> {
            existente(id, unidadeId);
            List<AmbienteDoRecurso> atuais = recursos.ambientes(id);
            Set<Long> daUnidade = atuais.stream().filter(a -> a.unidadeId() == unidadeId)
                    .map(AmbienteDoRecurso::ambienteId).collect(Collectors.toSet());
            Set<Long> mantidos = atuais.stream().filter(a -> a.unidadeId() != unidadeId)
                    .map(AmbienteDoRecurso::ambienteId).collect(Collectors.toSet());
            exigirSemErros(validator.validarAmbientes(id, ambienteIds, unidadeId, daUnidade, mantidos, dados));
            recursos.substituirAmbientes(id, unidadeId, ambienteIds);
            return recursos.ambientes(id);
        }));
    }

    private RecursoCadastro existente(long id, long unidadeId) {
        return recursos.recurso(id, unidadeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recurso não encontrado."));
    }

    private static void exigirSemErros(List<Erro> erros) {
        if (!erros.isEmpty()) {
            throw new RegraException(erros);
        }
    }

    private static boolean ativo(RecursoInput in) {
        return in.ativo() == null || in.ativo();
    }
}
