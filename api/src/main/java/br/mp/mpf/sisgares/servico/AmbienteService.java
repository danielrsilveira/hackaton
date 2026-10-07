package br.mp.mpf.sisgares.servico;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import br.mp.mpf.sisgares.dominio.AmbienteInput;
import br.mp.mpf.sisgares.dominio.AmbienteValidator;
import br.mp.mpf.sisgares.dominio.DadosAmbiente;
import br.mp.mpf.sisgares.dominio.Erro;
import br.mp.mpf.sisgares.dominio.RegraException;
import br.mp.mpf.sisgares.dominio.VinculoSetorInput;
import br.mp.mpf.sisgares.dominio.VinculoSetorValidator;
import br.mp.mpf.sisgares.infra.CadastroRepository;
import br.mp.mpf.sisgares.infra.CadastroRepository.Ambiente;
import br.mp.mpf.sisgares.infra.CadastroRepository.VinculoSetor;

/**
 * Cadastro de ambientes (F9/RF02) e de seus setores envolvidos (RF03). Não há exclusão: reservas e vínculos referenciam o ambiente,
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
            existente(id, unidadeId);
            validar(id, in, unidadeId);
            cadastros.atualizarAmbiente(id, unidadeId, in.descricao().strip(), in.idPai(), ativo(in));
            return cadastros.ambiente(id, unidadeId).orElseThrow();
        }));
    }

    /** RF03: setores notificados nas reservas do ambiente. */
    public List<VinculoSetor> setores(long ambienteId, long unidadeId) {
        existente(ambienteId, unidadeId);
        return cadastros.setoresDoAmbiente(ambienteId);
    }

    /**
     * RF03: substitui os setores vinculados. Vale para as notificações a partir de agora; reservas já
     * gravadas só geram e-mail para os novos setores quando forem alteradas ou canceladas.
     */
    public List<VinculoSetor> salvarSetores(long ambienteId, long unidadeId, List<VinculoSetorInput> vinculos) {
        return tx.execute(s -> {
            existente(ambienteId, unidadeId);
            Set<Long> atuais = cadastros.setoresDoAmbiente(ambienteId).stream().map(VinculoSetor::setorId)
                    .collect(Collectors.toSet());
            List<Erro> erros = VinculoSetorValidator.validar(vinculos, unidadeId, cadastros.setoresInfo(), atuais);
            if (!erros.isEmpty()) {
                throw new RegraException(erros);
            }
            cadastros.substituirSetoresDoAmbiente(ambienteId, vinculos.stream()
                    .map(v -> new VinculoSetorInput(v.setorId(), VinculoSetorValidator.codigo(v.codServicoSnp())))
                    .toList());
            return cadastros.setoresDoAmbiente(ambienteId);
        });
    }

    private Ambiente existente(long id, long unidadeId) {
        return cadastros.ambiente(id, unidadeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ambiente não encontrado."));
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
