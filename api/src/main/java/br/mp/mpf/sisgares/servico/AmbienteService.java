package br.mp.mpf.sisgares.servico;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import br.mp.mpf.sisgares.dominio.AmbienteInput;
import br.mp.mpf.sisgares.dominio.AmbienteValidator;
import br.mp.mpf.sisgares.dominio.DadosAmbiente;
import br.mp.mpf.sisgares.dominio.Erro;
import br.mp.mpf.sisgares.dominio.RegraException;
import br.mp.mpf.sisgares.infra.CadastroRepository;
import br.mp.mpf.sisgares.infra.CadastroRepository.Ambiente;

/**
 * Cadastro de ambientes (F9/RF01). Não há exclusão: reservas e vínculos referenciam o ambiente,
 * então ele é inativado. A escrita usa a mesma {@link TravaEscrita} das reservas, porque a
 * hierarquia alimenta a verificação de conflito RN6.
 */
@Service
public class AmbienteService {

    private final CadastroRepository cadastros;
    private final DadosAmbiente dados;
    private final AmbienteValidator validator;
    private final TransactionTemplate tx;
    private final TravaEscrita trava;

    public AmbienteService(CadastroRepository cadastros, DadosAmbiente dados, AmbienteValidator validator,
            TransactionTemplate tx, TravaEscrita trava) {
        this.cadastros = cadastros;
        this.dados = dados;
        this.validator = validator;
        this.tx = tx;
        this.trava = trava;
    }

    public Ambiente criar(long unidadeId, AmbienteInput in) {
        return trava.executar(() -> tx.execute(s -> {
            validar(null, in, unidadeId);
            long id = cadastros.inserirAmbiente(unidadeId, in.descricao().strip(), in.idPai(), ativo(in));
            return cadastros.ambiente(id, unidadeId).orElseThrow();
        }));
    }

    public Ambiente alterar(long id, long unidadeId, AmbienteInput in) {
        return trava.executar(() -> tx.execute(s -> {
            cadastros.ambiente(id, unidadeId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ambiente não encontrado."));
            validar(id, in, unidadeId);
            cadastros.atualizarAmbiente(id, unidadeId, in.descricao().strip(), in.idPai(), ativo(in));
            return cadastros.ambiente(id, unidadeId).orElseThrow();
        }));
    }

    private void validar(Long id, AmbienteInput in, long unidadeId) {
        List<Erro> erros = validator.validar(id, in, unidadeId, dados);
        if (!erros.isEmpty()) {
            throw new RegraException(erros);
        }
    }

    private static boolean ativo(AmbienteInput in) {
        return in.ativo() == null || in.ativo();
    }
}
