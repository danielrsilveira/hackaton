package br.mp.mpf.sisgares.servico;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import br.mp.mpf.sisgares.dominio.notificacao.NotificacaoHtml;
import br.mp.mpf.sisgares.dominio.notificacao.NotificacaoPlanner;
import br.mp.mpf.sisgares.dominio.notificacao.ReservaResumo;
import br.mp.mpf.sisgares.dominio.notificacao.TipoNotificacao;
import br.mp.mpf.sisgares.dominio.notificacao.Vinculo;
import br.mp.mpf.sisgares.infra.CadastroRepository;
import br.mp.mpf.sisgares.infra.ReservaRepository;
import br.mp.mpf.sisgares.infra.email.EmailSender;
import br.mp.mpf.sisgares.infra.snp.SnpClient;

/** RN10 (e-mail por setor), RN11 (pedido SNP) e RN12 (destaque das alterações). */
@Service
public class NotificacaoService {

    private static final Logger LOG = LoggerFactory.getLogger(NotificacaoService.class);

    private final ReservaRepository repo;
    private final CadastroRepository cadastros;
    private final EmailSender email;
    private final SnpClient snp;
    private final TransactionTemplate tx;
    private final Clock clock;

    public NotificacaoService(ReservaRepository repo, CadastroRepository cadastros, EmailSender email, SnpClient snp,
            TransactionTemplate tx, Clock clock) {
        this.repo = repo;
        this.cadastros = cadastros;
        this.email = email;
        this.snp = snp;
        this.tx = tx;
        this.clock = clock;
    }

    /**
     * @param anterior            versão anterior da reserva (alteração/cancelamento) ou null
     * @param vinculosAnteriores  setores da versão anterior: também são avisados se deixaram de ser envolvidos
     */
    public void notificar(long reservaId, TipoNotificacao tipo, ReservaResumo anterior, List<Vinculo> vinculosAnteriores) {
        LocalDateTime agora = LocalDateTime.now(clock);
        ReservaResumo atual = repo.resumo(reservaId, agora);
        List<Vinculo> atuais = repo.vinculos(reservaId);

        List<Vinculo> todos = new ArrayList<>(atuais);
        todos.addAll(vinculosAnteriores);
        var plano = NotificacaoPlanner.planejar(todos);

        // RN11: pedidos SNP só para vínculos atuais com código de serviço, sem duplicar em alterações.
        if (tipo != TipoNotificacao.CANCELADA) {
            String endpoint = cadastros.snpEndpoint();
            for (Vinculo v : NotificacaoPlanner.planejar(atuais).pedidosSnp()) {
                if (repo.existePedido(reservaId, v.envolvidoId(), v.codServicoSnp(), v.origem())) {
                    continue;
                }
                try {
                    var criado = snp.registrar(endpoint, new SnpClient.PedidoSnpRequest(v.codServicoSnp(), reservaId,
                            v.setor(), v.origem() + " – " + atual.periodos().replace("\n", "; ")));
                    tx.executeWithoutResult(s -> repo.inserirPedido(reservaId, v.envolvidoId(), v.codServicoSnp(),
                            v.origem(), criado.numero(), criado.url(), agora));
                } catch (RuntimeException e) {
                    // A reserva já está gravada: falha no SNP não desfaz a reserva, apenas é registrada.
                    LOG.warn("Falha ao registrar pedido SNP ({}, reserva {}): {}", v.codServicoSnp(), reservaId, e.getMessage());
                }
            }
        }

        for (Map.Entry<Long, List<Vinculo>> e : plano.porSetor().entrySet()) {
            Vinculo primeiro = e.getValue().get(0);
            String assunto = NotificacaoHtml.assunto(tipo, atual);
            String html = NotificacaoHtml.montar(tipo, atual, anterior, primeiro.setor(), e.getValue());
            tx.executeWithoutResult(s -> repo.inserirNotificacao(reservaId, e.getKey(), primeiro.destinatarios(),
                    tipo.name(), assunto, html, agora));
            email.enviar(primeiro.destinatarios(), assunto, html);
        }
    }
}
