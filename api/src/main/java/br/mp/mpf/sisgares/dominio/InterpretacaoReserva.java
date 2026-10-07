package br.mp.mpf.sisgares.dominio;

import java.util.List;

/**
 * Resultado da leitura de uma descrição em linguagem natural (F-IA): os campos da reserva
 * sugeridos pelo modelo mais um resumo do que foi entendido e eventuais avisos (ex.: trecho
 * que não pôde ser mapeado para um ambiente/recurso existente). Nada aqui é gravado: o
 * solicitante confere e ajusta no formulário antes de salvar.
 */
public record InterpretacaoReserva(ReservaInput reserva, String resumo, List<String> avisos) {

    public InterpretacaoReserva {
        avisos = avisos == null ? List.of() : List.copyOf(avisos);
    }
}
