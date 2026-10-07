package br.mp.mpf.sisgares.infra.snp;

/** Integração com o Sistema Nacional de Pedidos (simulado na demonstração). */
public interface SnpClient {

    record PedidoSnpRequest(String codServico, long reservaId, String setor, String descricao) {
    }

    record PedidoSnpCriado(String numero, String url) {
    }

    PedidoSnpCriado registrar(String endpoint, PedidoSnpRequest pedido);
}
