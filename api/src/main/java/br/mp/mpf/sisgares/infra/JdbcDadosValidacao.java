package br.mp.mpf.sisgares.infra;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import br.mp.mpf.sisgares.dominio.AmbienteInfo;
import br.mp.mpf.sisgares.dominio.DadosAmbiente;
import br.mp.mpf.sisgares.dominio.DadosRecurso;
import br.mp.mpf.sisgares.dominio.DadosValidacao;
import br.mp.mpf.sisgares.dominio.GrupoInfo;
import br.mp.mpf.sisgares.dominio.IconesRecurso;
import br.mp.mpf.sisgares.dominio.RecursoResumo;
import br.mp.mpf.sisgares.dominio.UsoRecurso;
import br.mp.mpf.sisgares.dominio.PeriodoOcupado;
import br.mp.mpf.sisgares.dominio.RecursoInfo;

@Component
public class JdbcDadosValidacao implements DadosValidacao, DadosAmbiente, DadosRecurso {

    private final JdbcClient jdbc;

    public JdbcDadosValidacao(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Set<Long> ambientesRelacionados(long ambienteId) {
        return new HashSet<>(jdbc.sql("""
                with recursive anc as (
                    select id, id_pai from ambiente where id = :id
                    union -- union (sem all) descarta linhas repetidas: termina mesmo se houver ciclo
                    select a.id, a.id_pai from ambiente a join anc on a.id = anc.id_pai
                ), des as (
                    select id from ambiente where id = :id
                    union
                    select a.id from ambiente a join des on a.id_pai = des.id
                )
                select id from anc union select id from des""")
                .param("id", ambienteId).query(Long.class).list());
    }

    @Override
    public List<PeriodoOcupado> periodosOcupados(Set<Long> ambienteIds, LocalDateTime de, LocalDateTime ate,
            Long excluirReservaId) {
        if (ambienteIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("""
                select p.rese_id as reserva_id, r.ambi_id as ambiente_id, a.descricao as ambiente_descricao,
                       p.dthr_inicio as inicio, p.dthr_termino as termino
                  from periodo_reserva p
                  join reserva r on r.id = p.rese_id
                  join ambiente a on a.id = r.ambi_id
                 where not r.cancelada and r.ambi_id in (:ids) and r.id <> :excluir
                   and p.dthr_inicio < :ate and p.dthr_termino > :de
                 order by p.dthr_inicio""")
                .param("ids", ambienteIds).param("excluir", excluirReservaId == null ? -1L : excluirReservaId)
                .param("de", de).param("ate", ate)
                .query(PeriodoOcupado.class).list();
    }

    @Override
    public Optional<RecursoInfo> recurso(long recursoId) {
        record Linha(long id, String descricao, boolean limitado, int disponibilidade, boolean ativo, Long unidadeId) {
        }
        return jdbc.sql("select id, descricao, limitado, disponibilidade, ativo, unidade_id from recurso where id = :id")
                .param("id", recursoId).query(Linha.class).optional()
                .map(l -> new RecursoInfo(l.id(), l.descricao(), l.limitado(), l.disponibilidade(), l.ativo(),
                        l.unidadeId(), Set.copyOf(jdbc.sql("select ambi_id from vinculo_recurso where recu_id = :id")
                                .param("id", recursoId).query(Long.class).list())));
    }

    @Override
    public int quantidadeReservada(long recursoId, LocalDateTime inicio, LocalDateTime termino, Long excluirReservaId) {
        return jdbc.sql("""
                select coalesce(sum(coalesce(s.qtd, 1)), 0)
                  from solicitacao s join reserva r on r.id = s.rese_id
                 where s.recu_id = :recu and not r.cancelada and r.id <> :excluir
                   and exists (select 1 from periodo_reserva p
                                where p.rese_id = r.id and p.dthr_inicio < :fim and p.dthr_termino > :ini)""")
                .param("recu", recursoId).param("excluir", excluirReservaId == null ? -1L : excluirReservaId)
                .param("ini", inicio).param("fim", termino)
                .query(Integer.class).single();
    }

    @Override
    public Optional<AmbienteInfo> ambiente(long ambienteId) {
        return jdbc.sql("select id, descricao, id_pai, ativo, unidade_id from ambiente where id = :id")
                .param("id", ambienteId).query(AmbienteInfo.class).optional();
    }

    @Override
    public List<AmbienteInfo> ambientes(long unidadeId) {
        return jdbc.sql("select id, descricao, id_pai, ativo, unidade_id from ambiente where unidade_id = :uni")
                .param("uni", unidadeId).query(AmbienteInfo.class).list();
    }

    @Override
    public List<RecursoResumo> recursos(long unidadeId) {
        return jdbc.sql("""
                select id, descricao, grec_id as grupo_id, limitado, disponibilidade, ativo, unidade_id
                  from recurso where unidade_id is null or unidade_id = :uni""")
                .param("uni", unidadeId).query(RecursoResumo.class).list();
    }

    @Override
    public Map<Long, GrupoInfo> grupos() {
        return jdbc.sql("select id, descricao, ativo from grupo_recurso").query(GrupoInfo.class).list().stream()
                .collect(Collectors.toMap(GrupoInfo::id, Function.identity()));
    }

    @Override
    public Set<String> icones() {
        Set<String> r = new HashSet<>(IconesRecurso.DISPONIVEIS);
        r.addAll(jdbc.sql("select arquivo from icone_recurso").query(String.class).list());
        return r;
    }

    @Override
    public List<UsoRecurso> usosNaoTranscorridos(long recursoId, LocalDateTime agora) {
        return jdbc.sql("""
                select r.id as reserva_id, r.unidade_id, r.ambi_id as ambiente_id, s.qtd as quantidade,
                       p.dthr_inicio as inicio, p.dthr_termino as termino
                  from solicitacao s
                  join reserva r on r.id = s.rese_id
                  join periodo_reserva p on p.rese_id = r.id
                 where s.recu_id = :recu and not r.cancelada and p.dthr_termino > :agora
                 order by p.dthr_inicio""")
                .param("recu", recursoId).param("agora", agora).query(UsoRecurso.class).list();
    }

    @Override
    public List<PeriodoOcupado> periodosNaoTranscorridos(Set<Long> ambienteIds, LocalDateTime agora) {
        if (ambienteIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("""
                select p.rese_id as reserva_id, r.ambi_id as ambiente_id, a.descricao as ambiente_descricao,
                       p.dthr_inicio as inicio, p.dthr_termino as termino
                  from periodo_reserva p
                  join reserva r on r.id = p.rese_id
                  join ambiente a on a.id = r.ambi_id
                 where not r.cancelada and r.ambi_id in (:ids) and p.dthr_termino > :agora
                 order by p.dthr_inicio""")
                .param("ids", ambienteIds).param("agora", agora)
                .query(PeriodoOcupado.class).list();
    }
}
