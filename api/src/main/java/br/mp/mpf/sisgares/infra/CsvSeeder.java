package br.mp.mpf.sisgares.infra;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Carga inicial a partir dos CSV de docs/dados (copiados para classpath:seed). Só roda com o profile
 * "seed" e com o banco vazio. Não existe CSV de reservas: elas são geradas com dados fictícios a partir
 * dos períodos e solicitações. Também cria algumas reservas relativas a "hoje" para a demonstração.
 */
@Component
@Profile("seed")
public class CsvSeeder implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(CsvSeeder.class);
    private static final DateTimeFormatter DATA_CSV = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final long UNIDADE = 1L;

    /** RN11: códigos de serviço do catálogo do SNP (fictícios; não existem nos CSV). */
    private static final Map<String, String> SNP_RECURSO = Map.of(
            "2|6", "TI-0101",   // SEART x Projetor Multimídia Portátil
            "2|8", "TI-0102",   // SEART x Notebook
            "2|96", "TI-0202"); // SEART x Videoconferência
    private static final Map<String, String> SNP_AMBIENTE = Map.of(
            "23|1", "SEG-0401"); // SESOT x Auditório (Completo)

    private static final String[] FINALIDADES = {
            "Reunião de alinhamento da equipe", "Audiência pública", "Treinamento interno",
            "Sessão de videoconferência", "Seminário institucional", "Reunião com órgãos parceiros",
            "Oficina de capacitação" };

    private final JdbcClient jdbc;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final String snpEndpoint;

    public CsvSeeder(JdbcClient jdbc, TransactionTemplate tx, Clock clock,
            @Value("${app.snp.endpoint}") String snpEndpoint) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.clock = clock;
        this.snpEndpoint = snpEndpoint;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (jdbc.sql("select count(*) from unidade").query(Integer.class).single() > 0) {
            LOG.info("Seed ignorado: banco já possui dados.");
            return;
        }
        tx.executeWithoutResult(s -> carregar());
        LOG.info("Seed concluído.");
    }

    private void carregar() {
        jdbc.sql("insert into unidade (id, sigla, descricao) values (1, 'PR/CE', 'Procuradoria da República no Ceará (fictícia)')").update();
        jdbc.sql("insert into configuracao (id, antecedencia_min, hora_min, hora_max, snp_endpoint) values (1, 60, '07:00', '20:00', :snp)")
                .param("snp", snpEndpoint).update();

        for (var r : ler("dados-envolvido.csv")) {
            jdbc.sql("insert into envolvido (id, descricao, email, ativo, unidade_id) values (:id, :d, :e, :a, 1)")
                    .param("id", id(r.get("ENVO_ID"))).param("d", r.get("ENVO_DESC")).param("e", r.get("ENVO_EMAIL"))
                    .param("a", sim(r.get("ENVO_ST_ATIVO"))).update();
        }

        // Usuários fictícios (LGPD: nenhum dado real).
        Object[][] usuarios = {
                { 1, "Ana Souza (fictícia)", "ana.souza@exemplo.gov.br", "SOLICITANTE", null },
                { 2, "Bruno Lima (fictício)", "bruno.lima@exemplo.gov.br", "SOLICITANTE", null },
                { 3, "Carla Mendes – Administradora (fictícia)", "carla.mendes@exemplo.gov.br", "ADMIN", null },
                { 4, "Diego Rocha – Atendente SEART (fictício)", "diego.rocha@exemplo.gov.br", "ATENDENTE", 2L },
                { 5, "Elisa Prado – Atendente SMSG (fictícia)", "elisa.prado@exemplo.gov.br", "ATENDENTE", 1L } };
        for (Object[] u : usuarios) {
            jdbc.sql("insert into usuario (id, nome, email, perfil, unidade_id, envolvido_id) values (:id, :n, :e, :p, 1, :env)")
                    .param("id", u[0]).param("n", u[1]).param("e", u[2]).param("p", u[3]).param("env", u[4]).update();
        }

        // Ambientes: pais antes dos filhos.
        List<Map<String, String>> ambientes = new ArrayList<>(ler("dados-ambiente.csv"));
        ambientes.sort((a, b) -> Boolean.compare(!a.get("AMBI_ID_PAI").isBlank(), !b.get("AMBI_ID_PAI").isBlank()));
        Set<Long> ambientesAtivos = new HashSet<>();
        for (var r : ambientes) {
            long id = id(r.get("AMBI_ID"));
            if (sim(r.get("AMBI_ST_ATIVO"))) {
                ambientesAtivos.add(id);
            }
            jdbc.sql("insert into ambiente (id, descricao, ativo, id_pai, unidade_id) values (:id, :d, :a, :p, 1)")
                    .param("id", id).param("d", r.get("AMBI_DESC")).param("a", sim(r.get("AMBI_ST_ATIVO")))
                    .param("p", id(r.get("AMBI_ID_PAI"))).update();
        }

        for (var r : ler("dados-disposicao.csv")) {
            jdbc.sql("insert into disposicao (id, descricao, ativo, icone_arquivo) values (:id, :d, :a, :i)")
                    .param("id", id(r.get("DISP_ID"))).param("d", r.get("DISP_DESC"))
                    .param("a", sim(r.get("DISP_ST_ATIVO"))).param("i", r.get("DISP_ICONE_ARQUIVO")).update();
        }
        for (var r : ler("dados-grupo-recurso.csv")) {
            jdbc.sql("insert into grupo_recurso (id, descricao, ordem, ativo) values (:id, :d, :o, :a)")
                    .param("id", id(r.get("GREC_ID"))).param("d", r.get("GREC_DESC"))
                    .param("o", Integer.valueOf(r.get("GREC_ORDEM"))).param("a", sim(r.get("GREC_ST_ATIVO"))).update();
        }
        Map<Long, Boolean> limitado = new HashMap<>();
        for (var r : ler("dados-recurso.csv")) {
            long id = id(r.get("RECU_ID"));
            limitado.put(id, sim(r.get("RECU_ST_LIMITADO")));
            jdbc.sql("""
                    insert into recurso (id, descricao, grec_id, limitado, disponibilidade, ativo, icone_arquivo)
                    values (:id, :d, :g, :l, :disp, :a, :i)""")
                    .param("id", id).param("d", r.get("RECU_DESC")).param("g", id(r.get("RECU_GREC_ID")))
                    .param("l", sim(r.get("RECU_ST_LIMITADO"))).param("disp", Integer.valueOf(r.get("RECU_DISPONIBILIDADE")))
                    .param("a", sim(r.get("RECU_ST_ATIVO"))).param("i", r.get("RECU_ICONE_ARQUIVO")).update();
        }
        for (var r : ler("dados-envolvido-ambiente.csv")) {
            String chave = id(r.get("EAMB_ENVO_ID")) + "|" + id(r.get("EAMB_AMBI_ID"));
            jdbc.sql("insert into envolvido_ambiente (id, envo_id, ambi_id, cod_servico_snp) values (:id, :e, :a, :c)")
                    .param("id", id(r.get("EAMB_ID"))).param("e", id(r.get("EAMB_ENVO_ID")))
                    .param("a", id(r.get("EAMB_AMBI_ID"))).param("c", SNP_AMBIENTE.get(chave)).update();
        }
        for (var r : ler("dados-envolvido-recurso.csv")) {
            String chave = id(r.get("EREC_ENVO_ID")) + "|" + id(r.get("EREC_RECU_ID"));
            jdbc.sql("insert into envolvido_recurso (id, envo_id, recu_id, cod_servico_snp) values (:id, :e, :r, :c)")
                    .param("id", id(r.get("EREC_ID"))).param("e", id(r.get("EREC_ENVO_ID")))
                    .param("r", id(r.get("EREC_RECU_ID"))).param("c", SNP_RECURSO.get(chave)).update();
        }
        Map<Long, Set<Long>> vinculos = new HashMap<>();
        for (var r : ler("dados-vinculo-recurso.csv")) {
            long recu = id(r.get("VREC_RECU_ID"));
            long ambi = id(r.get("VREC_AMBI_ID"));
            vinculos.computeIfAbsent(recu, k -> new HashSet<>()).add(ambi);
            jdbc.sql("insert into vinculo_recurso (id, recu_id, ambi_id) values (:id, :r, :a)")
                    .param("id", id(r.get("VREC_ID"))).param("r", recu).param("a", ambi).update();
        }

        carregarReservas(ambientesAtivos, vinculos, limitado);
        criarReservasDeDemonstracao();

        for (String t : List.of("envolvido", "usuario", "ambiente", "envolvido_ambiente", "disposicao", "grupo_recurso",
                "recurso", "envolvido_recurso", "vinculo_recurso", "reserva", "periodo_reserva", "solicitacao")) {
            jdbc.sql("select setval(pg_get_serial_sequence('" + t + "', 'id'), (select coalesce(max(id), 1) from " + t + "))")
                    .query(Long.class).single();
        }
    }

    /** Reservas históricas: reconstruídas a partir dos períodos e solicitações dos CSV. */
    private void carregarReservas(Set<Long> ambientesAtivos, Map<Long, Set<Long>> vinculos, Map<Long, Boolean> limitado) {
        record P(long id, LocalDateTime ini, LocalDateTime fim) {
        }
        Map<Long, List<P>> periodos = new TreeMap<>();
        for (var r : ler("dados-periodo-reserva.csv")) {
            LocalDateTime ini = LocalDateTime.parse(r.get("PRES_DTHR_INICIO"), DATA_CSV);
            LocalDateTime fim = LocalDateTime.parse(r.get("PRES_DTHR_TERMINO"), DATA_CSV);
            if (!fim.isAfter(ini)) {
                continue;
            }
            periodos.computeIfAbsent(id(r.get("PRES_RESE_ID")), k -> new ArrayList<>())
                    .add(new P(id(r.get("PRES_ID")), ini, fim));
        }
        Map<Long, List<long[]>> solicitacoes = new HashMap<>();
        for (var r : ler("dados-solicitacao.csv")) {
            Long qtd = id(r.get("SOLI_QTD"));
            solicitacoes.computeIfAbsent(id(r.get("SOLI_RESE_ID")), k -> new ArrayList<>())
                    .add(new long[] { id(r.get("SOLI_ID")), id(r.get("SOLI_RECU_ID")), qtd == null ? 0 : qtd });
        }

        List<Long> ativos = ambientesAtivos.stream().sorted().toList();
        int i = 0;
        for (var e : periodos.entrySet()) {
            long rid = e.getKey();
            List<long[]> sols = solicitacoes.getOrDefault(rid, List.of());
            // Recursos vinculados a ambientes restringem a escolha do ambiente (RN9).
            List<Long> candidatos = new ArrayList<>(ativos);
            for (long[] s : sols) {
                Set<Long> v = vinculos.get(s[1]);
                if (v != null) {
                    candidatos.retainAll(v);
                }
            }
            Long ambiente = candidatos.isEmpty() || (rid % 6 == 0 && sols.stream().noneMatch(s -> vinculos.containsKey(s[1])))
                    ? null : candidatos.get(i % candidatos.size());
            LocalDateTime inicio = e.getValue().stream().map(P::ini).min(LocalDateTime::compareTo).orElseThrow();
            jdbc.sql("""
                    insert into reserva (id, unidade_id, solicitante_id, ambi_id, complemento_ambiente, finalidade,
                                         qtd_participantes, disp_id, dthr_criacao, dthr_ultima_alteracao)
                    values (:id, 1, :sol, :ambi, :compl, :fin, :qtd, :disp, :cri, :cri)""")
                    .param("id", rid).param("sol", rid % 2 == 0 ? 1L : 2L).param("ambi", ambiente)
                    .param("compl", ambiente == null ? "Gabinete 701 (local próprio)" : null)
                    .param("fin", FINALIDADES[(int) (rid % FINALIDADES.length)])
                    .param("qtd", (int) (5 + (rid * 7) % 40)).param("disp", ambiente == null ? null : 1 + rid % 7)
                    .param("cri", inicio.minusDays(7)).update();
            for (P p : e.getValue()) {
                jdbc.sql("insert into periodo_reserva (id, rese_id, dthr_inicio, dthr_termino) values (:id, :r, :i, :f)")
                        .param("id", p.id()).param("r", rid).param("i", p.ini()).param("f", p.fim()).update();
            }
            Set<Long> recursosVistos = new HashSet<>();
            for (long[] s : sols) {
                if (!limitado.containsKey(s[1]) || !recursosVistos.add(s[1])) {
                    continue;
                }
                Integer qtd = limitado.get(s[1]) ? (int) Math.max(1, s[2]) : null;
                jdbc.sql("insert into solicitacao (id, rese_id, recu_id, qtd) values (:id, :r, :rc, :q)")
                        .param("id", s[0]).param("r", rid).param("rc", s[1]).param("q", qtd).update();
            }
            i++;
        }
    }

    /** Reservas relativas à data atual, preparadas para o roteiro da demonstração (RN5, RN6, RN8). */
    private void criarReservasDeDemonstracao() {
        LocalDate d1 = proximoDiaUtil(LocalDate.now(clock));
        LocalDate d2 = proximoDiaUtil(d1);
        long base = 900000;
        demo(base + 1, 2L, 5L, "Treinamento da equipe de atendimento (Parte A)", d1, 14, 16, null);
        demo(base + 2, 1L, 1L, "Audiência pública sobre mobilidade urbana", d2, 9, 11, null);
        demo(base + 3, 2L, 3L, "Reunião com órgãos parceiros", d1, 14, 16, 6L);
    }

    private void demo(long id, long solicitante, long ambiente, String finalidade, LocalDate dia, int hIni, int hFim,
            Long projetor) {
        LocalDateTime agora = LocalDateTime.now(clock);
        jdbc.sql("""
                insert into reserva (id, unidade_id, solicitante_id, ambi_id, finalidade, qtd_participantes, disp_id,
                                     dthr_criacao, dthr_ultima_alteracao)
                values (:id, 1, :sol, :ambi, :fin, 30, 2, :agora, :agora)""")
                .param("id", id).param("sol", solicitante).param("ambi", ambiente).param("fin", finalidade)
                .param("agora", agora).update();
        jdbc.sql("insert into periodo_reserva (rese_id, dthr_inicio, dthr_termino) values (:r, :i, :f)")
                .param("r", id).param("i", dia.atTime(hIni, 0)).param("f", dia.atTime(hFim, 0)).update();
        if (projetor != null) {
            jdbc.sql("insert into solicitacao (rese_id, recu_id, qtd) values (:r, :rc, 1)")
                    .param("r", id).param("rc", projetor).update();
        }
    }

    private static LocalDate proximoDiaUtil(LocalDate d) {
        LocalDate n = d.plusDays(1);
        while (n.getDayOfWeek() == DayOfWeek.SATURDAY || n.getDayOfWeek() == DayOfWeek.SUNDAY) {
            n = n.plusDays(1);
        }
        return n;
    }

    // ---- CSV ----

    private static List<Map<String, String>> ler(String arquivo) {
        try (var in = new ClassPathResource("seed/" + arquivo).getInputStream();
                var br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            List<String> linhas = br.lines().filter(l -> !l.isBlank()).toList();
            List<String> cab = dividir(linhas.get(0).replace("\uFEFF", ""));
            List<Map<String, String>> out = new ArrayList<>();
            for (String linha : linhas.subList(1, linhas.size())) {
                List<String> v = dividir(linha);
                Map<String, String> m = new LinkedHashMap<>();
                for (int i = 0; i < cab.size(); i++) {
                    m.put(cab.get(i), i < v.size() ? v.get(i).trim() : "");
                }
                out.add(m);
            }
            return out;
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao ler seed " + arquivo, e);
        }
    }

    static List<String> dividir(String linha) {
        List<String> out = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean aspas = false;
        for (int i = 0; i < linha.length(); i++) {
            char c = linha.charAt(i);
            if (aspas) {
                if (c == '"' && i + 1 < linha.length() && linha.charAt(i + 1) == '"') {
                    sb.append('"');
                    i++;
                } else if (c == '"') {
                    aspas = false;
                } else {
                    sb.append(c);
                }
            } else if (c == '"') {
                aspas = true;
            } else if (c == ',') {
                out.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        out.add(sb.toString());
        return out;
    }

    /** IDs dos CSV usam ponto como separador de milhar (ex.: 14.207). */
    private static Long id(String s) {
        return s == null || s.isBlank() ? null : Long.valueOf(s.replace(".", "").trim());
    }

    private static boolean sim(String s) {
        return "S".equalsIgnoreCase(s);
    }
}
