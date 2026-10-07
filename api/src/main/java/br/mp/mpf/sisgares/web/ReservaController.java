package br.mp.mpf.sisgares.web;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.time.Clock;
import java.time.LocalDate;

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

import br.mp.mpf.sisgares.dominio.DadosValidacao;
import br.mp.mpf.sisgares.dominio.Erro;
import br.mp.mpf.sisgares.dominio.Periodo;
import br.mp.mpf.sisgares.dominio.ReservaInput;
import br.mp.mpf.sisgares.dominio.StatusReserva;
import br.mp.mpf.sisgares.infra.CadastroRepository;
import br.mp.mpf.sisgares.infra.ReservaRepository;
import br.mp.mpf.sisgares.infra.ReservaRepository.Notificacao;
import br.mp.mpf.sisgares.infra.ReservaRepository.PedidoSnp;
import br.mp.mpf.sisgares.infra.ReservaRepository.RecursoDaReserva;
import br.mp.mpf.sisgares.infra.Usuario;
import br.mp.mpf.sisgares.servico.ReservaService;
import br.mp.mpf.sisgares.servico.ReservaService.ReservaDetalhe;

@RestController
@RequestMapping("/api")
public class ReservaController {

    private final ReservaService service;
    private final ReservaRepository repo;
    private final CadastroRepository cadastros;
    private final DadosValidacao dados;
    private final UsuarioAtual usuarios;
    private final Clock clock;

    public ReservaController(ReservaService service, ReservaRepository repo, CadastroRepository cadastros,
            DadosValidacao dados, UsuarioAtual usuarios, Clock clock) {
        this.service = service;
        this.repo = repo;
        this.cadastros = cadastros;
        this.dados = dados;
        this.usuarios = usuarios;
        this.clock = clock;
    }

    // ---- Reserva (F1–F4) ----

    @PostMapping("/reservas/validar")
    public List<Erro> validar(@RequestBody ReservaInput in, @RequestParam(required = false) Long reservaId,
            @RequestHeader(value = UsuarioAtual.HEADER, required = false) Long uid) {
        return service.validarPrevia(in, reservaId, usuarios.de(uid));
    }

    @PostMapping("/reservas")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Long> criar(@RequestBody ReservaInput in,
            @RequestHeader(value = UsuarioAtual.HEADER, required = false) Long uid) {
        return Map.of("id", service.criar(in, usuarios.de(uid)));
    }

    @GetMapping("/reservas/{id}")
    public ReservaDetalhe detalhe(@PathVariable long id,
            @RequestHeader(value = UsuarioAtual.HEADER, required = false) Long uid) {
        return service.detalhe(id, usuarios.de(uid));
    }

    @PutMapping("/reservas/{id}")
    public ReservaDetalhe alterar(@PathVariable long id, @RequestBody ReservaInput in,
            @RequestHeader(value = UsuarioAtual.HEADER, required = false) Long uid) {
        Usuario u = usuarios.de(uid);
        service.alterar(id, in, u);
        return service.detalhe(id, u);
    }

    @PostMapping("/reservas/{id}/cancelar")
    public ReservaDetalhe cancelar(@PathVariable long id,
            @RequestHeader(value = UsuarioAtual.HEADER, required = false) Long uid) {
        Usuario u = usuarios.de(uid);
        service.cancelar(id, u);
        return service.detalhe(id, u);
    }

    public record MinhaReserva(long id, String finalidade, String ambiente, LocalDateTime inicio,
            LocalDateTime termino, StatusReserva status) {
    }

    /** Acompanhamento das reservas do próprio solicitante. */
    @GetMapping("/reservas/minhas")
    public List<MinhaReserva> minhas(@RequestHeader(value = UsuarioAtual.HEADER, required = false) Long uid) {
        Usuario u = usuarios.de(uid);
        LocalDateTime agora = LocalDateTime.now(clock);
        List<MinhaReserva> out = new ArrayList<>();
        for (long id : repo.idsDoSolicitante(u.id())) {
            var c = repo.cabecalho(id).orElseThrow();
            List<Periodo> ps = repo.periodos(id);
            out.add(new MinhaReserva(id, c.finalidade(), c.ambienteTexto(), ps.get(0).inicio(),
                    ps.get(ps.size() - 1).termino(), StatusReserva.calcular(c.cancelada(), ps, agora)));
        }
        return out;
    }

    // ---- Painel do solicitante (F7 / RF16) ----

    public record ItemAgenda(long reservaId, long ambienteId, String ambiente, LocalDateTime inicio,
            LocalDateTime termino, boolean propria, boolean podeAbrir, String finalidade) {
    }

    public record Agenda(LocalDateTime agora, int antecedenciaMin, LocalTime horaMin, LocalTime horaMax,
            List<ItemAgenda> periodos) {
    }

    /** Períodos do ambiente e de seus pais/filhos. Finalidade só para o dono ou gestor (LGPD). */
    @GetMapping("/agenda")
    public Agenda agenda(@RequestParam long ambienteId, @RequestParam LocalDate inicio, @RequestParam int dias,
            @RequestHeader(value = UsuarioAtual.HEADER, required = false) Long uid) {
        Usuario u = usuarios.de(uid);
        var cfg = cadastros.regras(u.unidadeId());
        int n = Math.clamp(dias, 1, 31);
        List<Long> ids = new ArrayList<>(dados.ambientesRelacionados(ambienteId));
        var itens = repo.agenda(ids, inicio.atStartOfDay(), inicio.plusDays(n).atStartOfDay()).stream()
                .map(p -> {
                    boolean propria = p.solicitanteId() == u.id();
                    boolean pode = propria || u.gestor();
                    return new ItemAgenda(p.reservaId(), p.ambienteId(), p.ambiente(), p.inicio(), p.termino(),
                            propria, pode, pode ? p.finalidade() : null);
                }).toList();
        return new Agenda(LocalDateTime.now(clock), cfg.antecedenciaMin(), cfg.horaMin(), cfg.horaMax(), itens);
    }

    // ---- Painel do atendente (F8 / RF17) ----

    public record Card(long reservaId, StatusReserva status, String finalidade, String solicitante, String ambiente,
            int qtdParticipantes, List<Periodo> periodos, List<RecursoDaReserva> recursos, List<PedidoSnp> pedidosSnp) {
    }

    @GetMapping("/painel-atendente")
    public List<Card> painelAtendente(@RequestParam LocalDate inicio, @RequestParam int dias,
            @RequestParam(required = false) Long setorId,
            @RequestHeader(value = UsuarioAtual.HEADER, required = false) Long uid) {
        Usuario u = usuarios.gestor(uid);
        LocalDateTime agora = LocalDateTime.now(clock);
        int n = Math.clamp(dias, 1, 31);
        List<Card> cards = new ArrayList<>();
        for (long id : repo.idsNoIntervalo(inicio.atStartOfDay(), inicio.plusDays(n).atStartOfDay(), setorId, u.unidadeId())) {
            var c = repo.cabecalho(id).orElseThrow();
            List<Periodo> ps = repo.periodos(id);
            cards.add(new Card(id, StatusReserva.calcular(c.cancelada(), ps, agora), c.finalidade(), c.solicitante(),
                    c.ambienteTexto(), c.qtdParticipantes(), ps, repo.recursos(id), repo.pedidos(id)));
        }
        cards.sort(Comparator.comparing(c -> c.periodos().get(0).inicio()));
        return cards;
    }

    // ---- Notificações e pedidos SNP (F5/F6) ----

    /** Administrador vê todas; atendente vê só as do próprio setor. */
    @GetMapping("/notificacoes")
    public List<Notificacao> notificacoes(@RequestParam(required = false) Long reservaId,
            @RequestHeader(value = UsuarioAtual.HEADER, required = false) Long uid) {
        Usuario u = usuarios.gestor(uid);
        return repo.notificacoes(reservaId, u.admin() ? null : u.envolvidoId());
    }

    @GetMapping("/pedidos-snp")
    public List<PedidoSnp> pedidos(@RequestHeader(value = UsuarioAtual.HEADER, required = false) Long uid) {
        usuarios.gestor(uid);
        return repo.pedidos(null);
    }
}
