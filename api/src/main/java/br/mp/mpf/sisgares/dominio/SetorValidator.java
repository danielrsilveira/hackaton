package br.mp.mpf.sisgares.dominio;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Regras do cadastro de setores envolvidos (F9/RF01). Classe pura.
 * <ul>
 * <li>Descrição obrigatória (até 200 caracteres) e única entre os setores ativos da unidade.</li>
 * <li>E-mail padrão obrigatório e válido (até 200 caracteres).</li>
 * <li>Lista de e-mails opcional; cada endereço válido, sem repetição; total até 1000 caracteres.
 * Quando preenchida, substitui a caixa postal padrão nas notificações (RN10).</li>
 * </ul>
 * Inativar é sempre permitido: o setor deixa de ser notificado, e os vínculos e o histórico ficam.
 */
public final class SetorValidator {

    public static final String REGRA = "RF01";
    public static final int TAMANHO_DESCRICAO = 200;
    public static final int TAMANHO_EMAIL = 200;
    public static final int TAMANHO_LISTA = 1000;
    /** Separador gravado em {@code emails_lista}. */
    public static final String SEPARADOR = "; ";
    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)+$");

    private SetorValidator() {
    }

    public static boolean emailValido(String email) {
        return email != null && email.length() <= TAMANHO_EMAIL && EMAIL.matcher(email).matches();
    }

    /** Endereços da lista, sem espaços e sem repetição (ignorando maiúsculas), na ordem informada. */
    public static List<String> enderecos(String lista) {
        if (lista == null || lista.isBlank()) {
            return List.of();
        }
        LinkedHashSet<String> vistos = new LinkedHashSet<>();
        List<String> r = new ArrayList<>();
        for (String e : Arrays.stream(lista.split("[;,\\s]+")).filter(s -> !s.isBlank()).toList()) {
            if (vistos.add(e.toLowerCase())) {
                r.add(e);
            }
        }
        return r;
    }

    /** Lista normalizada para gravar: endereços separados por "; ", ou null quando vazia. */
    public static String listaNormalizada(String lista) {
        List<String> es = enderecos(lista);
        return es.isEmpty() ? null : String.join(SEPARADOR, es);
    }

    /**
     * @param id         id do setor em edição ou null na inclusão
     * @param daUnidade  setores da unidade (ativos e inativos)
     */
    public static List<Erro> validar(Long id, SetorInput in, List<SetorInfo> daUnidade) {
        List<Erro> erros = new ArrayList<>();
        boolean ativo = in.ativo() == null || in.ativo();
        String descricao = in.descricao() == null ? "" : in.descricao().strip();
        String email = in.email() == null ? "" : in.email().strip();

        if (descricao.isEmpty()) {
            erros.add(new Erro(REGRA, "Informe a descrição do setor."));
        } else if (descricao.length() > TAMANHO_DESCRICAO) {
            erros.add(new Erro(REGRA, "A descrição deve ter até %d caracteres.".formatted(TAMANHO_DESCRICAO)));
        } else if (ativo && daUnidade.stream().anyMatch(s -> s.ativo() && !Objects.equals(s.id(), id)
                && s.descricao().strip().equalsIgnoreCase(descricao))) {
            erros.add(new Erro(REGRA, "Já existe um setor ativo com a descrição \"%s\".".formatted(descricao)));
        }

        if (email.isEmpty()) {
            erros.add(new Erro(REGRA, "Informe a caixa postal padrão do setor."));
        } else if (!emailValido(email)) {
            erros.add(new Erro(REGRA, "A caixa postal padrão \"%s\" não é um e-mail válido.".formatted(email)));
        }

        List<String> lista = enderecos(in.emailsLista());
        List<String> invalidos = lista.stream().filter(e -> !emailValido(e)).toList();
        if (!invalidos.isEmpty()) {
            erros.add(new Erro(REGRA, "E-mails inválidos na lista: %s.".formatted(String.join(", ", invalidos))));
        } else if (String.join(SEPARADOR, lista).length() > TAMANHO_LISTA) {
            erros.add(new Erro(REGRA, "A lista de e-mails deve ter até %d caracteres.".formatted(TAMANHO_LISTA)));
        }
        return erros;
    }
}
