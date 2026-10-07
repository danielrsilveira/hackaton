package br.mp.mpf.sisgares.dominio;

/** Dados enviados no cadastro de ambiente (F9/RF01). {@code idPai} nulo = ambiente raiz. */
public record AmbienteInput(String descricao, Long idPai, Boolean ativo) {
}
