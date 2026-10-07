package br.mp.mpf.sisgares.infra;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import br.mp.mpf.sisgares.dominio.Formato;
import br.mp.mpf.sisgares.dominio.ItemRecurso;
import br.mp.mpf.sisgares.dominio.Periodo;
import br.mp.mpf.sisgares.dominio.ReservaInput;
import br.mp.mpf.sisgares.dominio.StatusReserva;
import br.mp.mpf.sisgares.dominio.notificacao.ReservaResumo;
import br.mp.mpf.sisgares.dominio.notificacao.Vinculo;

/** Persistência de reservas, períodos, solicitações, notificações e pedidos SNP. */
@Repository
public class ReservaRepository {

    public record Cabecalho(long id, long unidadeId, long solicitanteId, String solicitante, Long ambienteId,
            String ambiente, String complementoAmbiente, String finalidade, int qtdParticipantes, Long disposicaoId,
            String disposicao, boolean cancelada, LocalDateTime dthrUltimaAlteracao, int versao) {

        public String ambienteTexto() {
            String base = ambiente == null ? "Não solicitado / local próprio" : ambiente;
            return complementoAmbiente == null || complementoAmbiente.isBlank() ? base
                    : base + " – " + complementoAmbiente;
        }
    }

    public record RecursoDaReserva(long recursoId, String descricao, String iconeArquivo, boolean limitado,
            Integer quantidade, String grupo) {

        public String texto() {
            return limitado && quantidade != null ? descricao + " (" + quantidade + ")" : descricao;
        }
    }

    public record PedidoSnp(long id, long reservaId, String setor, String codServico, String origem, String numero,
            String url, LocalDateTime dthr) {
    }

    public record Notificacao(long id, long reservaId, String setor, String destinatarios, String tipo,
            String assunto, String html, LocalDateTime dthr) {
    }

    private final JdbcClient jdbc;

    public ReservaRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public long inserir(ReservaInput in, long solicitanteId, long unidadeId, LocalDateTime agora) {
        long id = jdbc.sql("""
                insert into reserva (unidade_id, solicitante_id, ambi_id, complemento_ambiente, finalidade,
                                     qtd_participantes, disp_id, dthr_criacao, dthr_ultima_alteracao)
                values (:uni, :sol, :ambi, :compl, :fin, :qtd, :disp, :agora, :agora)
                returning id""")
                .param("uni", unidadeId).param("sol", solicitanteId).param("ambi", in.ambienteId())
                .param("compl", vazioParaNull(in.complementoAmbiente())).param("fin", in.finalidade().strip())
                .param("qtd", in.qtdParticipantes()).param("disp", in.ambienteId() == null ? null : in.disposicaoId())
                .param("agora", agora)
                .query(Long.class).single();
        gravarFilhos(id, in);
        return id;
    }

    public void atualizar(long id, ReservaInput in, LocalDateTime agora) {
        jdbc.sql("""
                update reserva set ambi_id = :ambi, complemento_ambiente = :compl, finalidade = :fin,
                       qtd_participantes = :qtd, disp_id = :disp, dthr_ultima_alteracao = :agora, versao = versao + 1
                 where id = :id""")
                .param("ambi", in.ambienteId()).param("compl", vazioParaNull(in.complementoAmbiente()))
                .param("fin", in.finalidade().strip()).param("qtd", in.qtdParticipantes())
                .param("disp", in.ambienteId() == null ? null : in.disposicaoId())
                .param("agora", agora).param("id", id).update();
        jdbc.sql("delete from periodo_reserva where rese_id = :id").param("id", id).update();
        jdbc.sql("delete from solicitacao where rese_id = :id").param("id", id).update();
        gravarFilhos(id, in);
    }

    public void cancelar(long id, LocalDateTime agora) {
        jdbc.sql("""
                update reserva set cancelada = true, dthr_cancelamento = :agora, dthr_ultima_alteracao = :agora,
                       versao = versao + 1 where id = :id""")
                .param("agora", agora).param("id", id).update();
    }

    private void gravarFilhos(long id, ReservaInput in) {
        for (Periodo p : in.periodos()) {
            jdbc.sql("insert into periodo_reserva (rese_id, dthr_inicio, dthr_termino) values (:id, :ini, :fim)")
                    .param("id", id).param("ini", p.inicio()).param("fim", p.termino()).update();
        }
        if (in.recursos() == null) {
            return;
        }
        for (ItemRecurso item : in.recursos()) {
            if (item == null || item.recursoId() == null) {
                continue;
            }
            jdbc.sql("""
                    insert into solicitacao (rese_id, recu_id, qtd)
                    select :id, r.id, case when r.limitado then cast(:qtd as integer) else null end
                      from recurso r where r.id = :recu""")
                    .param("id", id).param("recu", item.recursoId()).param("qtd", item.quantidade()).update();
        }
    }

    public Optional<Cabecalho> cabecalho(long id) {
        return jdbc.sql("""
                select r.id, r.unidade_id, r.solicitante_id, u.nome as solicitante, r.ambi_id as ambiente_id,
                       a.descricao as ambiente, r.complemento_ambiente, r.finalidade, r.qtd_participantes,
                       r.disp_id as disposicao_id, d.descricao as disposicao, r.cancelada, r.dthr_ultima_alteracao,
                       r.versao
                  from reserva r
                  join usuario u on u.id = r.solicitante_id
                  left join ambiente a on a.id = r.ambi_id
                  left join disposicao d on d.id = r.disp_id
                 where r.id = :id""").param("id", id).query(Cabecalho.class).optional();
    }

    public List<Periodo> periodos(long reservaId) {
        return jdbc.sql("""
                select dthr_inicio as inicio, dthr_termino as termino from periodo_reserva
                 where rese_id = :id order by dthr_inicio""").param("id", reservaId).query(Periodo.class).list();
    }

    public List<RecursoDaReserva> recursos(long reservaId) {
        return jdbc.sql("""
                select s.recu_id as recurso_id, r.descricao, r.icone_arquivo, r.limitado, s.qtd as quantidade,
                       g.descricao as grupo
                  from solicitacao s join recurso r on r.id = s.recu_id join grupo_recurso g on g.id = r.grec_id
                 where s.rese_id = :id order by g.ordem nulls last, r.descricao""")
                .param("id", reservaId).query(RecursoDaReserva.class).list();
    }

    public ReservaResumo resumo(long id, LocalDateTime agora) {
        Cabecalho c = cabecalho(id).orElseThrow();
        List<Periodo> ps = periodos(id);
        return new ReservaResumo(id,
                StatusReserva.calcular(c.cancelada(), ps, agora).name(),
                c.solicitante(),
                c.ambienteTexto(),
                c.disposicao(),
                c.finalidade(),
                String.valueOf(c.qtdParticipantes()),
                ps.stream().map(Formato::periodo).collect(Collectors.joining("\n")),
                recursos(id).stream().map(RecursoDaReserva::texto).collect(Collectors.joining("\n")));
    }

    /** RN10/RN11: setores vinculados ao ambiente e aos recursos da reserva. */
    public List<Vinculo> vinculos(long reservaId) {
        return jdbc.sql("""
                select e.id as envolvido_id, e.descricao as setor,
                       coalesce(nullif(trim(e.emails_lista), ''), e.email) as destinatarios,
                       ea.cod_servico_snp, 'Ambiente: ' || a.descricao as origem
                  from reserva r
                  join envolvido_ambiente ea on ea.ambi_id = r.ambi_id
                  join envolvido e on e.id = ea.envo_id and e.ativo
                  join ambiente a on a.id = r.ambi_id
                 where r.id = :id
                union all
                select e.id, e.descricao, coalesce(nullif(trim(e.emails_lista), ''), e.email),
                       er.cod_servico_snp, 'Recurso: ' || rc.descricao
                  from solicitacao s
                  join envolvido_recurso er on er.recu_id = s.recu_id
                  join envolvido e on e.id = er.envo_id and e.ativo
                  join recurso rc on rc.id = s.recu_id
                 where s.rese_id = :id""").param("id", reservaId).query(Vinculo.class).list();
    }

    public void inserirNotificacao(long reservaId, long envolvidoId, String destinatarios, String tipo,
            String assunto, String html, LocalDateTime agora) {
        jdbc.sql("""
                insert into notificacao (rese_id, envo_id, destinatarios, tipo, assunto, html, dthr)
                values (:r, :e, :d, :t, :a, :h, :agora)""")
                .param("r", reservaId).param("e", envolvidoId).param("d", destinatarios).param("t", tipo)
                .param("a", assunto).param("h", html).param("agora", agora).update();
    }

    public boolean existePedido(long reservaId, long envolvidoId, String codServico, String origem) {
        return jdbc.sql("""
                select count(*) from pedido_snp
                 where rese_id = :r and envo_id = :e and cod_servico = :c and origem = :o""")
                .param("r", reservaId).param("e", envolvidoId).param("c", codServico).param("o", origem)
                .query(Integer.class).single() > 0;
    }

    public void inserirPedido(long reservaId, long envolvidoId, String codServico, String origem, String numero,
            String url, LocalDateTime agora) {
        jdbc.sql("""
                insert into pedido_snp (rese_id, envo_id, cod_servico, origem, numero, url, dthr)
                values (:r, :e, :c, :o, :n, :u, :agora)""")
                .param("r", reservaId).param("e", envolvidoId).param("c", codServico).param("o", origem)
                .param("n", numero).param("u", url).param("agora", agora).update();
    }

    public List<PedidoSnp> pedidos(Long reservaId) {
        return jdbc.sql("""
                select p.id, p.rese_id as reserva_id, e.descricao as setor, p.cod_servico, p.origem, p.numero, p.url, p.dthr
                  from pedido_snp p join envolvido e on e.id = p.envo_id
                 where (:r = -1 or p.rese_id = :r) order by p.id desc""")
                .param("r", reservaId == null ? -1L : reservaId).query(PedidoSnp.class).list();
    }

    public List<Notificacao> notificacoes(Long reservaId, Long envolvidoId) {
        return jdbc.sql("""
                select n.id, n.rese_id as reserva_id, e.descricao as setor, n.destinatarios, n.tipo, n.assunto,
                       n.html, n.dthr
                  from notificacao n join envolvido e on e.id = n.envo_id
                 where (:r = -1 or n.rese_id = :r) and (:e = -1 or n.envo_id = :e)
                 order by n.id desc limit 200""")
                .param("r", reservaId == null ? -1L : reservaId).param("e", envolvidoId == null ? -1L : envolvidoId)
                .query(Notificacao.class).list();
    }

    /** Reservas do solicitante (para acompanhamento). */
    public List<Long> idsDoSolicitante(long solicitanteId) {
        return jdbc.sql("""
                select r.id from reserva r
                 where r.solicitante_id = :s
                 order by (select max(p.dthr_termino) from periodo_reserva p where p.rese_id = r.id) desc
                 limit 50""").param("s", solicitanteId).query(Long.class).list();
    }

    /** Reservas não canceladas com período cruzando [de, ate), opcionalmente de interesse de um setor. */
    public List<Long> idsNoIntervalo(LocalDateTime de, LocalDateTime ate, Long setorId, long unidadeId) {
        return jdbc.sql("""
                select distinct r.id from reserva r join periodo_reserva p on p.rese_id = r.id
                 where not r.cancelada and r.unidade_id = :uni and p.dthr_inicio < :ate and p.dthr_termino > :de
                   and (:setor = -1
                        or exists (select 1 from envolvido_ambiente ea where ea.ambi_id = r.ambi_id and ea.envo_id = :setor)
                        or exists (select 1 from solicitacao s join envolvido_recurso er on er.recu_id = s.recu_id
                                    where s.rese_id = r.id and er.envo_id = :setor))
                 order by r.id""")
                .param("uni", unidadeId).param("de", de).param("ate", ate)
                .param("setor", setorId == null ? -1L : setorId).query(Long.class).list();
    }

    public record PeriodoAgenda(long reservaId, long ambienteId, String ambiente, long solicitanteId,
            String finalidade, LocalDateTime inicio, LocalDateTime termino) {
    }

    public List<PeriodoAgenda> agenda(List<Long> ambienteIds, LocalDateTime de, LocalDateTime ate) {
        return jdbc.sql("""
                select r.id as reserva_id, r.ambi_id as ambiente_id, a.descricao as ambiente, r.solicitante_id,
                       r.finalidade, p.dthr_inicio as inicio, p.dthr_termino as termino
                  from periodo_reserva p join reserva r on r.id = p.rese_id join ambiente a on a.id = r.ambi_id
                 where not r.cancelada and r.ambi_id in (:ids) and p.dthr_inicio < :ate and p.dthr_termino > :de
                 order by p.dthr_inicio""")
                .param("ids", ambienteIds).param("de", de).param("ate", ate).query(PeriodoAgenda.class).list();
    }

    private static String vazioParaNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
