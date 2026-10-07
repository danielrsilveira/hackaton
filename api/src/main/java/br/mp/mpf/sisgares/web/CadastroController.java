package br.mp.mpf.sisgares.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.mp.mpf.sisgares.infra.CadastroRepository;
import br.mp.mpf.sisgares.infra.CadastroRepository.Ambiente;
import br.mp.mpf.sisgares.infra.CadastroRepository.Config;
import br.mp.mpf.sisgares.infra.CadastroRepository.Disposicao;
import br.mp.mpf.sisgares.infra.CadastroRepository.Recurso;
import br.mp.mpf.sisgares.infra.CadastroRepository.Setor;
import br.mp.mpf.sisgares.infra.Usuario;
import br.mp.mpf.sisgares.dominio.RegraException;

@RestController
@RequestMapping("/api")
public class CadastroController {

    private final CadastroRepository cadastros;
    private final UsuarioAtual usuarios;

    public CadastroController(CadastroRepository cadastros, UsuarioAtual usuarios) {
        this.cadastros = cadastros;
        this.usuarios = usuarios;
    }

    /** Lista de usuários fictícios para o seletor de perfil da demonstração. */
    @GetMapping("/usuarios")
    public List<Usuario> usuarios() {
        return cadastros.usuarios();
    }

    @GetMapping("/ambientes")
    public List<Ambiente> ambientes(@RequestHeader(value = UsuarioAtual.HEADER, required = false) Long uid) {
        return cadastros.ambientes(usuarios.de(uid).unidadeId());
    }

    @GetMapping("/disposicoes")
    public List<Disposicao> disposicoes() {
        return cadastros.disposicoes();
    }

    /** RN9: só os recursos que podem ser pedidos para o ambiente informado. */
    @GetMapping("/recursos")
    public List<Recurso> recursos(@RequestParam(required = false) Long ambienteId,
            @RequestHeader(value = UsuarioAtual.HEADER, required = false) Long uid) {
        return cadastros.recursosDisponiveis(ambienteId, usuarios.de(uid).unidadeId());
    }

    @GetMapping("/setores")
    public List<Setor> setores() {
        return cadastros.setores();
    }

    @GetMapping("/config")
    public Config config(@RequestHeader(value = UsuarioAtual.HEADER, required = false) Long uid) {
        return cadastros.config(usuarios.de(uid).unidadeId());
    }

    /** F10/RF09: somente administrador. */
    @PutMapping("/config")
    public Config salvarConfig(@RequestBody Config c,
            @RequestHeader(value = UsuarioAtual.HEADER, required = false) Long uid) {
        Usuario u = usuarios.admin(uid);
        if (c.antecedenciaMin() < 0 || c.horaMin() == null || c.horaMax() == null || !c.horaMax().isAfter(c.horaMin())) {
            throw new RegraException("RN3", "Informe antecedência ≥ 0 e uma faixa global válida (máximo após o mínimo).");
        }
        if ((c.unidadeHoraMin() == null) != (c.unidadeHoraMax() == null)
                || (c.unidadeHoraMin() != null && !c.unidadeHoraMax().isAfter(c.unidadeHoraMin()))) {
            throw new RegraException("RN3", "A faixa da unidade deve ter mínimo e máximo, com máximo após o mínimo (ou ficar vazia).");
        }
        if (c.snpEndpoint() == null || !c.snpEndpoint().matches("^https?://\\S+$")) {
            throw new RegraException("RF09", "Endpoint do SNP deve ser uma URL http(s).");
        }
        cadastros.salvarConfig(u.unidadeId(), c);
        return cadastros.config(u.unidadeId());
    }
}
