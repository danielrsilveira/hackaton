package br.mp.mpf.sisgares.dominio.notificacao;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** RN10/RN11: decide quem recebe e-mail (um por setor) e quais vínculos geram pedido no SNP. */
public final class NotificacaoPlanner {

    private NotificacaoPlanner() {
    }

    public record Plano(Map<Long, List<Vinculo>> porSetor, List<Vinculo> pedidosSnp) {
    }

    public static Plano planejar(List<Vinculo> vinculos) {
        Map<Long, List<Vinculo>> porSetor = new LinkedHashMap<>();
        List<Vinculo> pedidos = new ArrayList<>();
        Set<String> vistos = new HashSet<>();
        for (Vinculo v : vinculos) {
            if (!vistos.add(v.envolvidoId() + "|" + v.origem())) {
                continue;
            }
            porSetor.computeIfAbsent(v.envolvidoId(), k -> new ArrayList<>()).add(v);
            if (v.geraPedidoSnp()) {
                pedidos.add(v);
            }
        }
        return new Plano(porSetor, pedidos);
    }
}
