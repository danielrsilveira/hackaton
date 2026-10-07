package br.mp.mpf.sisgares.dominio;

import java.util.List;

/** Dados informados pelo solicitante ao incluir ou alterar uma reserva. ambienteId null = local próprio. */
public record ReservaInput(
        Long ambienteId,
        String complementoAmbiente,
        String finalidade,
        Integer qtdParticipantes,
        Long disposicaoId,
        List<Periodo> periodos,
        List<ItemRecurso> recursos) {
}
