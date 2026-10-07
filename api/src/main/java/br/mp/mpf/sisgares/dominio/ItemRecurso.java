package br.mp.mpf.sisgares.dominio;

/** Recurso/serviço pedido numa reserva. Quantidade só se aplica a recursos limitados. */
public record ItemRecurso(Long recursoId, Integer quantidade) {
}
