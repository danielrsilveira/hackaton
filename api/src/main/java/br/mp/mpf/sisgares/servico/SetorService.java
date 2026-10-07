package br.mp.mpf.sisgares.servico;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import br.mp.mpf.sisgares.dominio.Erro;
import br.mp.mpf.sisgares.dominio.RegraException;
import br.mp.mpf.sisgares.dominio.SetorInput;
import br.mp.mpf.sisgares.dominio.SetorValidator;
import br.mp.mpf.sisgares.infra.CadastroRepository;
import br.mp.mpf.sisgares.infra.CadastroRepository.SetorCadastro;

/**
 * Cadastro de setores envolvidos (F9/RF01), restrito aos setores da unidade do administrador.
 * Não há exclusão: notificações, vínculos e atendentes referenciam o setor, então ele é inativado
 * (setor inativo deixa de receber e-mails e pedidos SNP).
 */
@Service
public class SetorService {

    private final CadastroRepository cadastros;
    private final TransactionTemplate tx;

    public SetorService(CadastroRepository cadastros, TransactionTemplate tx) {
        this.cadastros = cadastros;
        this.tx = tx;
    }

    public List<SetorCadastro> listar(long unidadeId) {
        return cadastros.setoresDaUnidade(unidadeId);
    }

    public SetorCadastro criar(long unidadeId, SetorInput in) {
        return tx.execute(s -> {
            validar(null, in, unidadeId);
            long id = cadastros.inserirSetor(unidadeId, in.descricao().strip(), in.email().strip(),
                    SetorValidator.listaNormalizada(in.emailsLista()), ativo(in));
            return cadastros.setor(id, unidadeId).orElseThrow();
        });
    }

    public SetorCadastro alterar(long id, long unidadeId, SetorInput in) {
        return tx.execute(s -> {
            cadastros.setor(id, unidadeId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Setor não encontrado."));
            validar(id, in, unidadeId);
            cadastros.atualizarSetor(id, unidadeId, in.descricao().strip(), in.email().strip(),
                    SetorValidator.listaNormalizada(in.emailsLista()), ativo(in));
            return cadastros.setor(id, unidadeId).orElseThrow();
        });
    }

    private void validar(Long id, SetorInput in, long unidadeId) {
        List<Erro> erros = SetorValidator.validar(id, in, cadastros.setoresInfo(unidadeId));
        if (!erros.isEmpty()) {
            throw new RegraException(erros);
        }
    }

    private static boolean ativo(SetorInput in) {
        return in.ativo() == null || in.ativo();
    }
}
