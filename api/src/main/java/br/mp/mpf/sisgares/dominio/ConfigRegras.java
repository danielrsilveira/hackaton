package br.mp.mpf.sisgares.dominio;

import java.time.LocalTime;

/** Configuração efetiva para a unidade (faixa da unidade já aplicada sobre a global). */
public record ConfigRegras(int antecedenciaMin, LocalTime horaMin, LocalTime horaMax) {
}
