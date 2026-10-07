package br.mp.mpf.sisgares.web;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.mp.mpf.sisgares.dominio.IconeValidator;
import br.mp.mpf.sisgares.dominio.RecursoInput;
import br.mp.mpf.sisgares.dominio.RecursoValidator;
import br.mp.mpf.sisgares.dominio.RegraException;
import br.mp.mpf.sisgares.infra.Usuario;
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
    public List<RecursoCadastro> listar(@RequestHeader(value = UsuarioAtual.HEADER, required = false) Long uid) {
        return service.listar(usuarios.admin(uid).unidadeId());
    }

    @GetMapping("/recursos/icones")
    public List<String> icones(@RequestHeader(value = UsuarioAtual.HEADER, required = false) Long uid) {
        usuarios.admin(uid);
        return service.icones();
    }

    /** RF06: envia um novo ícone (multipart, campo {@code arquivo}); devolve {@code {"arquivo":"up-<id>"}}. */
    @PostMapping(path = "/recursos/icones", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> enviarIcone(@RequestParam("arquivo") MultipartFile arquivo,
            @RequestHeader(value = UsuarioAtual.HEADER, required = false) Long uid) throws IOException {
        Usuario u = usuarios.admin(uid);
        if (arquivo.getSize() > IconeValidator.TAMANHO_MAXIMO) {
            throw new RegraException(RecursoValidator.REGRA,
                    "O ícone deve ter até %d KB.".formatted(IconeValidator.TAMANHO_MAXIMO / 1024));
        }
        return Map.of("arquivo", service.enviarIcone(arquivo.getBytes(), u.id()));
    }

    /**
     * Imagem de um ícone enviado. Público, como os ícones estáticos em /img/recurso: aparece também
     * para solicitantes e atendentes. O tipo vem do banco (detectado no envio) e o navegador não pode adivinhar outro.
     */
    @GetMapping("/icones-recurso/{arquivo}")
    public ResponseEntity<byte[]> icone(@PathVariable String arquivo) {
        return recursos.icone(arquivo)
                .map(i -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(i.tipo()))
                        .header("X-Content-Type-Options", "nosniff")
                        .header("Content-Security-Policy", "default-src 'none'")
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic().immutable())
                        .body(i.conteudo()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/grupos-recurso")
    public List<Grupo> grupos(@RequestHeader(value = UsuarioAtual.HEADER, required = false) Long uid) {
        usuarios.admin(uid);
        return recursos.grupos();
    }

    @PostMapping("/recursos")
    @ResponseStatus(HttpStatus.CREATED)
    public RecursoCadastro criar(@RequestBody RecursoInput in,
            @RequestHeader(value = UsuarioAtual.HEADER, required = false) Long uid) {
        return service.criar(usuarios.admin(uid).unidadeId(), in);
    }

    /** Inativar = enviar {@code ativo: false}. */
    @PutMapping("/recursos/{id}")
    public RecursoCadastro alterar(@PathVariable long id, @RequestBody RecursoInput in,
            @RequestHeader(value = UsuarioAtual.HEADER, required = false) Long uid) {
        return service.alterar(id, usuarios.admin(uid).unidadeId(), in);
    }

    @GetMapping("/recursos/{id}/setores")
    public List<VinculoSetor> setores(@PathVariable long id,
            @RequestHeader(value = UsuarioAtual.HEADER, required = false) Long uid) {
        return service.setores(id, usuarios.admin(uid).unidadeId());
    }

    /** RF07: substitui a lista completa de setores. */
    @PutMapping("/recursos/{id}/setores")
    public List<VinculoSetor> salvarSetores(@PathVariable long id, @RequestBody List<VinculoSetorInput> vinculos,
            @RequestHeader(value = UsuarioAtual.HEADER, required = false) Long uid) {
        return service.salvarSetores(id, usuarios.admin(uid).unidadeId(), vinculos);
    }

    @GetMapping("/recursos/{id}/ambientes")
    public List<AmbienteDoRecurso> ambientes(@PathVariable long id,
            @RequestHeader(value = UsuarioAtual.HEADER, required = false) Long uid) {
        return service.ambientes(id, usuarios.admin(uid).unidadeId());
    }

    /** RF08: substitui os ambientes da unidade; lista vazia = sem restrição de ambiente. */
    @PutMapping("/recursos/{id}/ambientes")
    public List<AmbienteDoRecurso> salvarAmbientes(@PathVariable long id, @RequestBody List<Long> ambienteIds,
            @RequestHeader(value = UsuarioAtual.HEADER, required = false) Long uid) {
        return service.salvarAmbientes(id, usuarios.admin(uid).unidadeId(), ambienteIds);
    }
}
