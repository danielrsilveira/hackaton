package br.mp.mpf.sisgares.dominio;

import java.util.List;

/** Lançada quando uma operação viola regras de negócio. Vira HTTP 422. */
public class RegraException extends RuntimeException {

    private final transient List<Erro> erros;

    public RegraException(List<Erro> erros) {
        super(erros.toString());
        this.erros = List.copyOf(erros);
    }

    public RegraException(String regra, String mensagem) {
        this(List.of(new Erro(regra, mensagem)));
    }

    public List<Erro> getErros() {
        return erros;
    }
}
