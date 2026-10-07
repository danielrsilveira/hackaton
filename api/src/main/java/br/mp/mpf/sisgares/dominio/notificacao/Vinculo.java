package br.mp.mpf.sisgares.dominio.notificacao;

/**
 * Vínculo de um setor envolvido com o ambiente ou com um recurso da reserva.
 *
 * @param destinatarios  lista de e-mails do setor, ou a caixa postal padrão (RN10)
 * @param codServicoSnp  código de serviço no catálogo do SNP; null = só e-mail (RN11)
 * @param origem         o que gerou o vínculo (ex.: "Recurso: Projetor")
 */
public record Vinculo(long envolvidoId, String setor, String destinatarios, String codServicoSnp, String origem) {

    public boolean geraPedidoSnp() {
        return codServicoSnp != null && !codServicoSnp.isBlank();
    }
}
