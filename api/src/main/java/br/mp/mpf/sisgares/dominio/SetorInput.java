package br.mp.mpf.sisgares.dominio;

/**
 * Dados enviados no cadastro de setor envolvido (F9/RF01).
 *
 * @param email        caixa postal padrão do setor
 * @param emailsLista  lista opcional de e-mails (separados por ";", "," ou espaço) que substitui a padrão (RN10)
 */
public record SetorInput(String descricao, String email, String emailsLista, Boolean ativo) {
}
