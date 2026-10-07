package br.mp.mpf.sisgares.dominio.notificacao;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Monta o HTML do e-mail para o setor (RN10). Em alterações, destaca cada campo alterado
 * com o valor anterior riscado e o novo em destaque (RN12). Todo texto do usuário é escapado.
 */
public final class NotificacaoHtml {

    private NotificacaoHtml() {
    }

    private record Campo(String rotulo, Function<ReservaResumo, String> valor) {
    }

    private static final List<Campo> CAMPOS = List.of(
            new Campo("Status", ReservaResumo::status),
            new Campo("Solicitante", ReservaResumo::solicitante),
            new Campo("Ambiente", ReservaResumo::ambiente),
            new Campo("Disposição", ReservaResumo::disposicao),
            new Campo("Finalidade", ReservaResumo::finalidade),
            new Campo("Participantes", ReservaResumo::participantes),
            new Campo("Períodos", ReservaResumo::periodos),
            new Campo("Recursos e serviços", ReservaResumo::recursos));

    public static String assunto(TipoNotificacao tipo, ReservaResumo r) {
        String acao = switch (tipo) {
            case NOVA -> "Nova reserva";
            case ALTERADA -> "Reserva alterada";
            case CANCELADA -> "Reserva cancelada";
        };
        String primeiro = r.periodos() == null ? "" : r.periodos().lines().findFirst().orElse("");
        return "[SISGARES] %s #%d – %s – %s".formatted(acao, r.id(), r.ambiente(), primeiro);
    }

    public static String montar(TipoNotificacao tipo, ReservaResumo atual, ReservaResumo anterior,
            String setor, List<Vinculo> vinculosDoSetor) {
        boolean comparar = tipo == TipoNotificacao.ALTERADA && anterior != null;
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"font-family:Arial,Helvetica,sans-serif;color:#1a1a1a;line-height:1.4\">");
        sb.append("<h2 style=\"margin:0 0 8px\">").append(esc(assunto(tipo, atual))).append("</h2>");
        sb.append("<p>Setor: <strong>").append(esc(setor)).append("</strong><br>Motivo: ")
                .append(esc(vinculosDoSetor.stream().map(Vinculo::origem).distinct().collect(Collectors.joining("; "))))
                .append("</p>");
        if (tipo == TipoNotificacao.CANCELADA) {
            sb.append("<p style=\"background:#fde2e2;color:#7a1010;padding:8px;font-weight:bold\">")
                    .append("Esta reserva foi CANCELADA. Nenhum atendimento é necessário.</p>");
        }
        if (comparar) {
            sb.append("<p>Campos alterados estão marcados com <strong>[ALTERADO]</strong>: ")
                    .append("valor anterior riscado e novo valor em destaque.</p>");
        }
        sb.append("<table style=\"border-collapse:collapse;min-width:420px\" border=\"1\" cellpadding=\"6\">");
        sb.append("<tr><th scope=\"row\" style=\"text-align:left\">Reserva</th><td>#").append(atual.id()).append("</td></tr>");
        for (Campo c : CAMPOS) {
            String novo = c.valor().apply(atual);
            String antigo = comparar ? c.valor().apply(anterior) : null;
            boolean mudou = comparar && !Objects.equals(novo, antigo);
            sb.append(mudou ? "<tr style=\"background:#fff4c2\">" : "<tr>");
            sb.append("<th scope=\"row\" style=\"text-align:left;vertical-align:top\">").append(esc(c.rotulo()));
            if (mudou) {
                sb.append(" <strong>[ALTERADO]</strong>");
            }
            sb.append("</th><td>");
            if (mudou) {
                sb.append("<del style=\"color:#8a1c1c\">").append(valor(antigo)).append("</del><br>")
                        .append("<ins style=\"color:#0f4d1f;background:#d9f2dd;font-weight:bold;text-decoration:none\">")
                        .append(valor(novo)).append("</ins>");
            } else {
                sb.append(valor(novo));
            }
            sb.append("</td></tr>");
        }
        sb.append("</table>");
        sb.append("<p style=\"font-size:12px;color:#444\">Mensagem automática do SISGARES (ambiente de demonstração, dados fictícios).</p>");
        sb.append("</div>");
        return sb.toString();
    }

    private static String valor(String s) {
        return s == null || s.isBlank() ? "—" : esc(s).replace("\n", "<br>");
    }

    static String esc(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(s.length());
        for (char ch : s.toCharArray()) {
            switch (ch) {
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '&' -> out.append("&amp;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> out.append(ch);
            }
        }
        return out.toString();
    }
}
