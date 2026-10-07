package br.mp.mpf.sisgares.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.mp.mpf.sisgares.dominio.AmbienteInput;
import br.mp.mpf.sisgares.dominio.VinculoSetorInput;
import br.mp.mpf.sisgares.infra.CadastroRepository.VinculoSetor;
import br.mp.mpf.sisgares.servico.AmbienteService;

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
    private final AmbienteService ambientes;

    public CadastroController(CadastroRepository cadastros, UsuarioAtual usuarios, AmbienteService ambientes) {
        this.cadastros = cadastros;
        this.usuarios = usuarios;
        this.ambientes = ambientes;
    }

    /** Lista de usuários fictícios para o seletor de perfil da demonstração. */
    @GetMapping("/usuarios")
    public List<Usuario> usuarios() {
        return cadastros.usuarios();
    }

    /** Ambientes ativos da unidade. Com {@code todos=true} (só administrador), inclui os inativos. */
    @GetMapping("/ambientes")
    public List<Ambiente> ambientes(@RequestParam(defaultValue = "false") boolean todos,
            @RequestHeader(value = UsuarioAtual.HEADER, required = false) Long uid) {
        if (todos) {
            return cadastros.ambientesTodos(usuarios.admin(uid).unidadeId());
        }
        return cadastros.ambientes(usuarios.de(uid).unidadeId());
    }

    /** F9/RF02: somente administrador. */
    @PostMapping("/ambientes")
    @ResponseStatus(HttpStatus.CREATED)
    public Ambiente criarAmbiente(@RequestBody AmbienteInput in,
            @RequestHeader(value = UsuarioAtual.HEADER, required = false) Long uid) {
        return ambientes.criar(usuarios.admin(uid).unidadeId(), in);
    }

    /** F9/RF02: somente administrador. Inativar = enviar {@code ativo: false}. */
    @PutMapping("/ambientes/{id}")
    public Ambiente alterarAmbiente(@PathVariable long id, @RequestBody AmbienteInput in,
            @RequestHeader(value = UsuarioAtual.HEADER, required = false) Long uid) {
        return ambientes.alterar(id, usuarios.admin(uid).unidadeId(), in);
    }

    /** RF03: setores notificados nas reservas do ambiente (somente administrador). */
    @GetMapping("/ambientes/{id}/setores")
    public List<VinculoSetor> setoresDoAmbiente(@PathVariable long id,
            @RequestHeader(value = UsuarioAtual.HEADER, required = false) Long uid) {
        return ambientes.setores(id, usuarios.admin(uid).unidadeId());
    }

    /** RF03: substitui a lista completa de setores vinculados (somente administrador). */
    @PutMapping("/ambientes/{id}/setores")
    public List<VinculoSetor> salvarSetoresDoAmbiente(@PathVariable long id, @RequestBody List<VinculoSetorInput> vinculos,
            @RequestHeader(value = UsuarioAtual.HEADER, required = false) Long uid) {
        return ambientes.salvarSetores(id, usuarios.admin(uid).unidadeId(), vinculos);
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
