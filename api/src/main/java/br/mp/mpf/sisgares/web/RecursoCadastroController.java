package br.mp.mpf.sisgares.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.mp.mpf.sisgares.dominio.IconesRecurso;
import br.mp.mpf.sisgares.dominio.RecursoInput;
import br.mp.mpf.sisgares.dominio.VinculoSetorInput;
import br.mp.mpf.sisgares.infra.CadastroRepository.VinculoSetor;
import br.mp.mpf.sisgares.infra.RecursoRepository;
import br.mp.mpf.sisgares.infra.RecursoRepository.AmbienteDoRecurso;
import br.mp.mpf.sisgares.infra.RecursoRepository.Grupo;
import br.mp.mpf.sisgares.infra.RecursoRepository.RecursoCadastro;
import br.mp.mpf.sisgares.servico.RecursoService;

/**
 * F9: cadastro de recursos (RF06) e vínculos com setores (RF07) e ambientes (RF08). Somente administrador.
 * A lista para reserva (RN9) continua em {@code GET /api/recursos}, no {@link CadastroController}.
 */
@RestController
@RequestMapping("/api")
public class RecursoCadastroController {

    private final RecursoService service;
    private final RecursoRepository recursos;
    private final UsuarioAtual usuarios;

    public RecursoCadastroController(RecursoService service, RecursoRepository recursos, UsuarioAtual usuarios) {
        this.service = service;
        this.recursos = recursos;
        this.usuarios = usuarios;
    }

    @GetMapping("/recursos/cadastro")
    public List<RecursoCadastro> listar() {
        return service.listar(usuarios.admin().unidadeId());
    }

    @GetMapping("/recursos/icones")
    public List<String> icones() {
        usuarios.admin();
        return IconesRecurso.DISPONIVEIS;
    }

    @GetMapping("/grupos-recurso")
    public List<Grupo> grupos() {
        usuarios.admin();
        return recursos.grupos();
    }

    @PostMapping("/recursos")
    @ResponseStatus(HttpStatus.CREATED)
    public RecursoCadastro criar(@RequestBody RecursoInput in) {
        return service.criar(usuarios.admin().unidadeId(), in);
    }

    /** Inativar = enviar {@code ativo: false}. */
    @PutMapping("/recursos/{id}")
    public RecursoCadastro alterar(@PathVariable long id, @RequestBody RecursoInput in) {
        return service.alterar(id, usuarios.admin().unidadeId(), in);
    }

    @GetMapping("/recursos/{id}/setores")
    public List<VinculoSetor> setores(@PathVariable long id) {
        return service.setores(id, usuarios.admin().unidadeId());
    }

    /** RF07: substitui a lista completa de setores. */
    @PutMapping("/recursos/{id}/setores")
    public List<VinculoSetor> salvarSetores(@PathVariable long id, @RequestBody List<VinculoSetorInput> vinculos) {
        return service.salvarSetores(id, usuarios.admin().unidadeId(), vinculos);
    }

    @GetMapping("/recursos/{id}/ambientes")
    public List<AmbienteDoRecurso> ambientes(@PathVariable long id) {
        return service.ambientes(id, usuarios.admin().unidadeId());
    }

    /** RF08: substitui os ambientes da unidade; lista vazia = sem restrição de ambiente. */
    @PutMapping("/recursos/{id}/ambientes")
    public List<AmbienteDoRecurso> salvarAmbientes(@PathVariable long id, @RequestBody List<Long> ambienteIds) {
        return service.salvarAmbientes(id, usuarios.admin().unidadeId(), ambienteIds);
    }
}
