package br.mp.mpf.sisgares.infra.snp;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Chama o endpoint configurado (RF09). Localmente aponta para o mock em /mock-snp/pedidos. */
@Component
public class HttpSnpClient implements SnpClient {

    private final RestClient rest = RestClient.create();

    @Override
    public PedidoSnpCriado registrar(String endpoint, PedidoSnpRequest pedido) {
        PedidoSnpCriado criado = rest.post().uri(endpoint).contentType(MediaType.APPLICATION_JSON).body(pedido)
                .retrieve().body(PedidoSnpCriado.class);
        if (criado == null || criado.numero() == null) {
            throw new IllegalStateException("SNP não retornou o número do pedido");
        }
        return criado;
    }
}
