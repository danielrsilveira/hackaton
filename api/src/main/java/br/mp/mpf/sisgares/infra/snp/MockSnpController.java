package br.mp.mpf.sisgares.infra.snp;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

/** Sistema Nacional de Pedidos SIMULADO (fora do escopo a integração real). */
@RestController
@RequestMapping("/mock-snp/pedidos")
public class MockSnpController {

    private final JdbcClient jdbc;
    private final Map<String, SnpClient.PedidoSnpRequest> pedidos = new ConcurrentHashMap<>();

    public MockSnpController(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @PostMapping
    public SnpClient.PedidoSnpCriado criar(@RequestBody SnpClient.PedidoSnpRequest pedido) {
        String numero = String.valueOf(jdbc.sql("select nextval('snp_numero_seq')").query(Long.class).single());
        pedidos.put(numero, pedido);
        return new SnpClient.PedidoSnpCriado(numero, "/mock-snp/pedidos/" + numero);
    }

    @GetMapping(value = "/{numero}", produces = MediaType.TEXT_HTML_VALUE)
    public String ver(@PathVariable String numero) {
        var p = pedidos.get(numero);
        String corpo = p == null
                ? "<p>Pedido não encontrado na memória do simulador (o mock reinicia com a aplicação).</p>"
                : "<dl><dt>Serviço</dt><dd>%s</dd><dt>Setor</dt><dd>%s</dd><dt>Reserva</dt><dd>#%d</dd><dt>Descrição</dt><dd>%s</dd></dl>"
                        .formatted(HtmlUtils.htmlEscape(p.codServico()), HtmlUtils.htmlEscape(p.setor()), p.reservaId(),
                                HtmlUtils.htmlEscape(p.descricao()));
        return """
                <!doctype html><html lang="pt-BR"><head><meta charset="utf-8"><title>SNP %s (simulado)</title></head>
                <body style="font-family:sans-serif;max-width:40rem;margin:2rem auto">
                <h1>Pedido SNP nº %s</h1><p><strong>Sistema Nacional de Pedidos – SIMULADO</strong></p>%s</body></html>"""
                .formatted(HtmlUtils.htmlEscape(numero), HtmlUtils.htmlEscape(numero), corpo);
    }
}
