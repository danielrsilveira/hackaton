package br.mp.mpf.sisgares.dominio;

/** Vínculo de um setor envolvido com um ambiente (RF03). {@code codServicoSnp} vazio = só e-mail (RN11). */
public record VinculoSetorInput(Long setorId, String codServicoSnp) {
}
