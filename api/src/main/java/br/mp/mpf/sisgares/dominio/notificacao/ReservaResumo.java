package br.mp.mpf.sisgares.dominio.notificacao;

/** Versão textual da reserva usada no e-mail e na comparação entre versões (RN12). */
public record ReservaResumo(
        long id,
        String status,
        String solicitante,
        String ambiente,
        String disposicao,
        String finalidade,
        String participantes,
        String periodos,
        String recursos) {
}
