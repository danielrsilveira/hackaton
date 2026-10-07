package br.mp.mpf.sisgares.dominio;

/** Violação de uma regra de negócio (ex.: regra "RN5"). */
public record Erro(String regra, String mensagem) {
}
