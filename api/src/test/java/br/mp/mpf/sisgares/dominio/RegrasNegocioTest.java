package br.mp.mpf.sisgares.dominio;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import br.mp.mpf.sisgares.dominio.notificacao.NotificacaoHtml;
import br.mp.mpf.sisgares.dominio.notificacao.NotificacaoPlanner;
import br.mp.mpf.sisgares.dominio.notificacao.ReservaResumo;
import br.mp.mpf.sisgares.dominio.notificacao.TipoNotificacao;
import br.mp.mpf.sisgares.dominio.notificacao.Vinculo;

/** Casos de teste = exemplos da tabela de regras de negócio (seção 6 do caso de uso). */
class RegrasNegocioTest {

    static final ZoneId FUSO = ZoneId.of("America/Fortaleza");
    static final long UNIDADE = 1L;
    static final long AUDITORIO = 1L;
    static final long SALA_A = 5L;
    static final long SALA_1 = 3L;
    static final long SALA_2 = 7L;
    static final ConfigRegras CFG = new ConfigRegras(60, LocalTime.of(7, 0), LocalTime.of(20, 0));

    /** "Agora" padrão dos testes: 01/11/2026 10:00, bem antes dos períodos usados. */
    static ReservaValidator validador(LocalDateTime agora) {
        return new ReservaValidator(Clock.fixed(agora.atZone(FUSO).toInstant(), FUSO));
    }

    static final ReservaValidator V = validador(dt("01/11 10:00"));

    static LocalDateTime dt(String ddMMhhmm) {
        String[] p = ddMMhhmm.split("[/ :]");
        return LocalDateTime.of(2026, Integer.parseInt(p[1]), Integer.parseInt(p[0]),
                Integer.parseInt(p[2]), Integer.parseInt(p[3]));
    }

    static Periodo per(String ini, String fim) {
        return new Periodo(dt(ini), dt(fim));
    }

    static ReservaInput reserva(Long ambiente, List<ItemRecurso> recursos, Periodo... periodos) {
        return new ReservaInput(ambiente, null, "Reunião de alinhamento", 10, null, List.of(periodos), recursos);
    }

    static DadosEmMemoria dados() {
        return new DadosEmMemoria()
                .ambiente(AUDITORIO, "Auditório (Completo)", null)
                .ambiente(SALA_A, "Auditório (Parte A)", AUDITORIO)
                .ambiente(6L, "Auditório (Parte B)", AUDITORIO)
                .ambiente(SALA_1, "Sala 1", null)
                .ambiente(SALA_2, "Sala 2", null);
    }

    static Set<String> regras(List<Erro> erros) {
        return erros.stream().map(Erro::regra).collect(java.util.stream.Collectors.toSet());
    }

    List<Erro> validar(ReservaInput in, DadosEmMemoria d) {
        return V.validar(in, CFG, UNIDADE, d, null, List.of());
    }

    @Nested
    @DisplayName("RN1 – períodos")
    class Rn1 {
        @Test
        void terminoAntesDoInicioBloqueia() {
            assertThat(regras(validar(reserva(SALA_1, List.of(), per("10/11 14:00", "10/11 13:00")), dados())))
                    .contains("RN1");
        }

        @Test
        void periodoQueVaiParaOutroDiaEhAceito() {
            assertThat(validar(reserva(SALA_1, List.of(), per("10/11 18:00", "11/11 09:00")), dados())).isEmpty();
        }

        @Test
        void semPeriodoBloqueia() {
            assertThat(regras(validar(reserva(SALA_1, List.of()), dados()))).contains("RN1");
        }
    }

    @Nested
    @DisplayName("RN2 – campos obrigatórios")
    class Rn2 {
        @Test
        void soAguaECafeSemAmbienteESemComplementoBloqueia() {
            var in = new ReservaInput(null, null, "Café com parceiros", 8, null,
                    List.of(per("10/11 09:00", "10/11 10:00")), List.of(new ItemRecurso(3L, null)));
            var d = dados().recurso(new RecursoInfo(3, "Água e Café", false, 0, true, null, Set.of()));
            assertThat(regras(validar(in, d))).containsExactly("RN2");
        }

        @Test
        void localProprioComComplementoEhAceito() {
            var in = new ReservaInput(null, "Gabinete 701", "Café com parceiros", 8, null,
                    List.of(per("10/11 09:00", "10/11 10:00")), List.of());
            assertThat(validar(in, dados())).isEmpty();
        }

        @Test
        void finalidadeEParticipantesObrigatorios() {
            var in = new ReservaInput(SALA_1, null, " ", null, null, List.of(per("10/11 09:00", "10/11 10:00")), List.of());
            assertThat(validar(in, dados())).hasSize(2).allMatch(e -> e.regra().equals("RN2"));
        }
    }

    @Nested
    @DisplayName("RN3 – faixa de horário")
    class Rn3 {
        @Test
        void periodoAlemDaFaixaBloqueia() {
            assertThat(regras(validar(reserva(SALA_1, List.of(), per("10/11 18:00", "10/11 21:00")), dados())))
                    .containsExactly("RN3");
        }

        @Test
        void faixaDaUnidadePrevaleceSobreAGlobal() {
            // A faixa efetiva (já resolvida) da unidade é 08:00–18:00.
            var cfgUnidade = new ConfigRegras(60, LocalTime.of(8, 0), LocalTime.of(18, 0));
            var erros = V.validar(reserva(SALA_1, List.of(), per("10/11 07:30", "10/11 09:00")), cfgUnidade, UNIDADE,
                    dados(), null, List.of());
            assertThat(regras(erros)).containsExactly("RN3");
        }
    }

    @Nested
    @DisplayName("RN4 – antecedência mínima")
    class Rn4 {
        @Test
        void inicioSemAntecedenciaBloqueia() {
            var v = validador(dt("10/11 10:00"));
            var cfg = new ConfigRegras(120, LocalTime.of(7, 0), LocalTime.of(20, 0));
            var erros = v.validar(reserva(SALA_1, List.of(), per("10/11 11:00", "10/11 12:00")), cfg, UNIDADE, dados(),
                    null, List.of());
            assertThat(regras(erros)).containsExactly("RN4");
        }

        @Test
        void inicioComAntecedenciaEhAceito() {
            var v = validador(dt("10/11 10:00"));
            var cfg = new ConfigRegras(120, LocalTime.of(7, 0), LocalTime.of(20, 0));
            var erros = v.validar(reserva(SALA_1, List.of(), per("10/11 12:00", "10/11 13:00")), cfg, UNIDADE, dados(),
                    null, List.of());
            assertThat(erros).isEmpty();
        }
    }

    @Nested
    @DisplayName("RN5 – conflito no mesmo ambiente, com margem de 30 min")
    class Rn5 {
        DadosEmMemoria d = dados().reservar(100, AUDITORIO, per("10/11 09:00", "10/11 11:00"));

        @Test
        void inicioAMenosDe30MinDoFimBloqueia() {
            assertThat(regras(validar(reserva(AUDITORIO, List.of(), per("10/11 11:20", "10/11 12:00")), d)))
                    .containsExactly("RN5");
        }

        @Test
        void inicioExatamente30MinDepoisEhAceito() {
            assertThat(validar(reserva(AUDITORIO, List.of(), per("10/11 11:30", "10/11 12:00")), d)).isEmpty();
        }

        @Test
        void terminoAMenosDe30MinDoInicioBloqueia() {
            assertThat(regras(validar(reserva(AUDITORIO, List.of(), per("10/11 07:30", "10/11 08:45")), d)))
                    .containsExactly("RN5");
        }

        @Test
        void naEdicaoAPropriaReservaNaoConflita() {
            var erros = V.validar(reserva(AUDITORIO, List.of(), per("10/11 09:30", "10/11 11:00")), CFG, UNIDADE, d,
                    100L, List.of());
            assertThat(erros).isEmpty();
        }
    }

    @Nested
    @DisplayName("RN6 – ambiente pai x filho")
    class Rn6 {
        @Test
        void reservarPaiConflitaComFilho() {
            var d = dados().reservar(200, SALA_A, per("10/11 14:00", "10/11 16:00"));
            assertThat(regras(validar(reserva(AUDITORIO, List.of(), per("10/11 15:00", "10/11 17:00")), d)))
                    .containsExactly("RN6");
        }

        @Test
        void reservarFilhoConflitaComPai() {
            var d = dados().reservar(201, AUDITORIO, per("10/11 14:00", "10/11 16:00"));
            assertThat(regras(validar(reserva(SALA_A, List.of(), per("10/11 16:10", "10/11 17:00")), d)))
                    .containsExactly("RN6");
        }

        @Test
        void filhosIrmaosNaoConflitam() {
            var d = dados().reservar(202, SALA_A, per("10/11 14:00", "10/11 16:00"));
            assertThat(validar(reserva(6L, List.of(), per("10/11 14:00", "10/11 16:00")), d)).isEmpty();
        }
    }

    @Nested
    @DisplayName("RN7 – verificação refeita ao salvar")
    class Rn7 {
        @Test
        void segundoUsuarioAoSalvarEhBloqueado() {
            var d = dados();
            var pedido = reserva(AUDITORIO, List.of(), per("10/11 09:00", "10/11 10:00"));
            // Os dois preenchem ao mesmo tempo: a prévia de ambos está livre.
            assertThat(validar(pedido, d)).isEmpty();
            assertThat(validar(pedido, d)).isEmpty();
            // O primeiro salva...
            d.reservar(300, AUDITORIO, pedido.periodos().get(0));
            // ...e a revalidação no salvar do segundo bloqueia.
            assertThat(regras(validar(pedido, d))).containsExactly("RN5");
        }
    }

    @Nested
    @DisplayName("RN8 – recurso de disponibilidade limitada")
    class Rn8 {
        static final long PROJETOR = 6L;
        DadosEmMemoria d = dados()
                .recurso(new RecursoInfo(PROJETOR, "Projetor", true, 3, true, null, Set.of()))
                .usar(400, PROJETOR, 2, per("10/11 14:00", "10/11 16:00"));

        @Test
        void pedirDoisQuandoSoUmEstaLivreBloqueia() {
            var in = reserva(SALA_1, List.of(new ItemRecurso(PROJETOR, 2)), per("10/11 15:00", "10/11 17:00"));
            assertThat(regras(validar(in, d))).containsExactly("RN8");
        }

        @Test
        void pedirUmEhAceito() {
            var in = reserva(SALA_1, List.of(new ItemRecurso(PROJETOR, 1)), per("10/11 15:00", "10/11 17:00"));
            assertThat(validar(in, d)).isEmpty();
        }

        @Test
        void recursoLimitadoExigeQuantidade() {
            var in = reserva(SALA_1, List.of(new ItemRecurso(PROJETOR, null)), per("10/11 15:00", "10/11 17:00"));
            assertThat(regras(validar(in, d))).containsExactly("RN8");
        }
    }

    @Nested
    @DisplayName("RN9 – recurso restrito a ambiente/unidade")
    class Rn9 {
        DadosEmMemoria d = dados()
                .recurso(new RecursoInfo(11, "Kit de videoconferência", false, 0, true, null, Set.of(SALA_1)))
                .recurso(new RecursoInfo(12, "Recurso de outra unidade", false, 0, true, 99L, Set.of()));

        @Test
        void kitVinculadoASala1NaoPodeSerPedidoNaSala2() {
            var in = reserva(SALA_2, List.of(new ItemRecurso(11L, null)), per("10/11 09:00", "10/11 10:00"));
            assertThat(regras(validar(in, d))).containsExactly("RN9");
        }

        @Test
        void kitVinculadoASala1PodeSerPedidoNaSala1() {
            var in = reserva(SALA_1, List.of(new ItemRecurso(11L, null)), per("10/11 09:00", "10/11 10:00"));
            assertThat(validar(in, d)).isEmpty();
        }

        @Test
        void recursoDeOutraUnidadeBloqueia() {
            var in = reserva(SALA_1, List.of(new ItemRecurso(12L, null)), per("10/11 09:00", "10/11 10:00"));
            assertThat(regras(validar(in, d))).containsExactly("RN9");
        }
    }

    @Nested
    @DisplayName("RN10/RN11 – notificação por setor e pedido SNP")
    class Rn10e11 {
        Vinculo cafeCopa = new Vinculo(1, "Copa", "copa@exemplo.gov.br", null, "Recurso: Água e Café");
        Vinculo projetorTi = new Vinculo(2, "TI", "ti@exemplo.gov.br", "TI-0101", "Recurso: Projetor");
        Vinculo notebookTi = new Vinculo(2, "TI", "ti@exemplo.gov.br", null, "Recurso: Notebook");

        @Test
        void cadaSetorRecebeUmEmail() {
            var plano = NotificacaoPlanner.planejar(List.of(cafeCopa, projetorTi, notebookTi));
            assertThat(plano.porSetor()).containsOnlyKeys(1L, 2L);
            assertThat(plano.porSetor().get(2L)).hasSize(2);
        }

        @Test
        void soVinculoComCodigoDeServicoGeraPedidoSnp() {
            var plano = NotificacaoPlanner.planejar(List.of(cafeCopa, projetorTi, notebookTi));
            assertThat(plano.pedidosSnp()).containsExactly(projetorTi);
        }
    }

    @Nested
    @DisplayName("RN12 – alteração e cancelamento")
    class Rn12 {
        @Test
        void reservaEncerradaNaoPodeSerEditada() {
            var v = validador(dt("10/11 18:00"));
            assertThat(regras(v.validarEdicao(false, List.of(per("10/11 14:00", "10/11 16:00"))))).containsExactly("RN12");
        }

        @Test
        void reservaFuturaPodeSerEditada() {
            assertThat(V.validarEdicao(false, List.of(per("10/11 14:00", "10/11 16:00")))).isEmpty();
        }

        @Test
        void cancelamentoSemAntecedenciaBloqueia() {
            var v = validador(dt("10/11 13:30"));
            assertThat(regras(v.validarCancelamento(false, List.of(per("10/11 14:00", "10/11 16:00")), CFG)))
                    .containsExactly("RN12");
        }

        @Test
        void emailDeAlteracaoMostraHorarioAntigoENovoEmDestaque() {
            var antes = resumo("10/11/2026 14:00 – 16:00");
            var depois = resumo("10/11/2026 15:00 – 17:00");
            String html = NotificacaoHtml.montar(TipoNotificacao.ALTERADA, depois, antes, "TI",
                    List.of(new Vinculo(2, "TI", "ti@exemplo.gov.br", null, "Recurso: Projetor")));
            assertThat(html).contains("[ALTERADO]")
                    .containsPattern("<del[^>]*>10/11/2026 14:00 – 16:00</del>")
                    .containsPattern("<ins[^>]*>10/11/2026 15:00 – 17:00</ins>");
            // Só o campo alterado é destacado.
            assertThat(html.split("\\[ALTERADO]", -1)).hasSize(3); // aviso no topo + 1 campo
        }

        @Test
        void textoDoUsuarioEhEscapadoNoEmail() {
            var r = new ReservaResumo(1, "PREVISTA", "Ana", "Sala", null, "<script>x</script>", "5", "p", "r");
            assertThat(NotificacaoHtml.montar(TipoNotificacao.NOVA, r, null, "TI", List.of()))
                    .doesNotContain("<script>").contains("&lt;script&gt;");
        }

        ReservaResumo resumo(String periodos) {
            return new ReservaResumo(10, "PREVISTA", "Ana Souza", "Auditório", null, "Treinamento", "20", periodos,
                    "Projetor (1)");
        }
    }

    @Nested
    @DisplayName("RN13 – status pelo horário")
    class Rn13 {
        List<Periodo> periodo = List.of(per("10/11 09:00", "10/11 11:00"));

        @Test
        void emAndamento() {
            assertThat(StatusReserva.calcular(false, periodo, dt("10/11 10:00"))).isEqualTo(StatusReserva.EM_ANDAMENTO);
        }

        @Test
        void prevista() {
            assertThat(StatusReserva.calcular(false, periodo, dt("10/11 08:00"))).isEqualTo(StatusReserva.PREVISTA);
        }

        @Test
        void transcorrida() {
            assertThat(StatusReserva.calcular(false, periodo, dt("10/11 11:01"))).isEqualTo(StatusReserva.TRANSCORRIDA);
        }

        @Test
        void cancelada() {
            assertThat(StatusReserva.calcular(true, periodo, dt("10/11 10:00"))).isEqualTo(StatusReserva.CANCELADA);
        }
    }

    @Nested
    @DisplayName("RF02 – reserva só em ambiente ativo da unidade")
    class Rf02Reserva {
        @Test
        void ambienteInativoBloqueia() {
            var d = dados().inativar(SALA_2);
            assertThat(regras(validar(reserva(SALA_2, List.of(), per("10/11 09:00", "10/11 10:00")), d)))
                    .containsExactly("RF02");
        }

        @Test
        void ambienteDeOutraUnidadeBloqueia() {
            var d = dados().naUnidade(SALA_2, 99L);
            assertThat(regras(validar(reserva(SALA_2, List.of(), per("10/11 09:00", "10/11 10:00")), d)))
                    .containsExactly("RF02");
        }

        @Test
        void ambienteInexistenteBloqueia() {
            assertThat(regras(validar(reserva(404L, List.of(), per("10/11 09:00", "10/11 10:00")), dados())))
                    .containsExactly("RF02");
        }
    }

    @Nested
    @DisplayName("RF02 – cadastro de ambientes")
    class Rf02Cadastro {
        final AmbienteValidator av = new AmbienteValidator(Clock.fixed(dt("01/11 10:00").atZone(FUSO).toInstant(), FUSO));

        List<Erro> salvar(Long id, String descricao, Long idPai, boolean ativo, DadosEmMemoria d) {
            return av.validar(id, new AmbienteInput(descricao, idPai, ativo), UNIDADE, d);
        }

        @Test
        void novoAmbienteFilhoDoAuditorioEhAceito() {
            assertThat(salvar(null, "Auditório (Parte C)", AUDITORIO, true, dados())).isEmpty();
        }

        @Test
        void descricaoObrigatoria() {
            assertThat(regras(salvar(null, "  ", null, true, dados()))).containsExactly("RF02");
        }

        @Test
        void descricaoRepetidaEntreAtivosBloqueia() {
            assertThat(regras(salvar(null, " sala 1 ", null, true, dados()))).containsExactly("RF02");
        }

        @Test
        void descricaoDeAmbienteInativoPodeSerReusada() {
            assertThat(salvar(null, "Sala 1", null, true, dados().inativar(SALA_1))).isEmpty();
        }

        @Test
        void paiDeOutraUnidadeBloqueia() {
            assertThat(regras(salvar(null, "Nova sala", SALA_2, true, dados().naUnidade(SALA_2, 99L))))
                    .containsExactly("RF02");
        }

        @Test
        void paiInativoBloqueia() {
            assertThat(regras(salvar(null, "Nova sala", SALA_2, true, dados().inativar(SALA_2))))
                    .containsExactly("RF02");
        }

        @Test
        void ambienteNaoPodeSerPaiDeSiMesmo() {
            assertThat(regras(salvar(SALA_1, "Sala 1", SALA_1, true, dados()))).containsExactly("RF02");
        }

        @Test
        void filhoNaoPodeVirarPaiCiclo() {
            // Auditório → Parte A → Auditório formaria um ciclo
            assertThat(regras(salvar(AUDITORIO, "Auditório (Completo)", SALA_A, true, dados())))
                    .containsExactly("RF02");
        }

        @Test
        void inativarPaiComFilhosAtivosBloqueia() {
            assertThat(regras(salvar(AUDITORIO, "Auditório (Completo)", null, false, dados())))
                    .containsExactly("RF02");
        }

        @Test
        void inativarComReservaPrevistaBloqueia() {
            var d = dados().reservar(100, SALA_1, per("10/11 09:00", "10/11 10:00"));
            var erros = salvar(SALA_1, "Sala 1", null, false, d);
            assertThat(regras(erros)).containsExactly("RF02");
            assertThat(erros.getFirst().mensagem()).contains("#100");
        }

        @Test
        void inativarComReservaJaTranscorridaEhAceito() {
            var d = dados().reservar(100, SALA_1, per("20/10 09:00", "20/10 10:00"));
            assertThat(salvar(SALA_1, "Sala 1", null, false, d)).isEmpty();
        }

        @Test
        void mudarPaiQueCriaConflitoRn6Bloqueia() {
            // Sala 1 14:00–16:00 e Auditório 15:00–17:00 não conflitam hoje; com Sala 1 filha do Auditório, sim.
            var d = dados()
                    .reservar(100, SALA_1, per("10/11 14:00", "10/11 16:00"))
                    .reservar(200, AUDITORIO, per("10/11 15:00", "10/11 17:00"));
            var erros = salvar(SALA_1, "Sala 1", AUDITORIO, true, d);
            assertThat(regras(erros)).containsExactly("RN6");
            assertThat(erros.getFirst().mensagem()).contains("#100", "#200");
        }

        @Test
        void mudarPaiConsideraAMargemDe30Minutos() {
            var d = dados()
                    .reservar(100, SALA_1, per("10/11 14:00", "10/11 15:00"))
                    .reservar(200, AUDITORIO, per("10/11 15:20", "10/11 17:00"));
            assertThat(regras(salvar(SALA_1, "Sala 1", AUDITORIO, true, d))).containsExactly("RN6");
        }

        @Test
        void mudarPaiSemConflitoEhAceito() {
            var d = dados()
                    .reservar(100, SALA_1, per("10/11 09:00", "10/11 10:00"))
                    .reservar(200, AUDITORIO, per("10/11 15:00", "10/11 17:00"));
            assertThat(salvar(SALA_1, "Sala 1", AUDITORIO, true, d)).isEmpty();
        }

        @Test
        void mudarPaiIgnoraReservasJaTranscorridas() {
            var d = dados()
                    .reservar(100, SALA_1, per("20/10 14:00", "20/10 16:00"))
                    .reservar(200, AUDITORIO, per("20/10 15:00", "20/10 17:00"));
            assertThat(salvar(SALA_1, "Sala 1", AUDITORIO, true, d)).isEmpty();
        }

        @Test
        void tirarFilhoDoPaiNaoCriaConflito() {
            var d = dados()
                    .reservar(100, SALA_A, per("10/11 14:00", "10/11 16:00"))
                    .reservar(200, AUDITORIO, per("10/11 20:00", "10/11 21:00"));
            assertThat(salvar(SALA_A, "Auditório (Parte A)", null, true, d)).isEmpty();
        }
    }
}
