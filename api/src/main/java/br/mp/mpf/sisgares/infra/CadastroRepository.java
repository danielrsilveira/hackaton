package br.mp.mpf.sisgares.infra;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import br.mp.mpf.sisgares.dominio.ConfigRegras;

/** Tabelas básicas (leitura e cadastro de ambientes) e configuração. */
@Repository
public class CadastroRepository {

    public record Ambiente(long id, String descricao, Long idPai, boolean ativo) {
    }

    public record Disposicao(long id, String descricao, String iconeArquivo) {
    }

    public record Recurso(long id, String descricao, boolean limitado, int disponibilidade, String iconeArquivo,
            String grupo, Integer grupoOrdem) {
    }

    public record Setor(long id, String descricao, String email, String emailsLista) {
    }

    public record Config(int antecedenciaMin, LocalTime horaMin, LocalTime horaMax, String snpEndpoint,
            LocalTime unidadeHoraMin, LocalTime unidadeHoraMax) {
    }

    private final JdbcClient jdbc;

    public CadastroRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Usuario> usuario(long id) {
        return jdbc.sql("select id, nome, email, perfil, unidade_id, envolvido_id from usuario where id = :id")
                .param("id", id).query(Usuario.class).optional();
    }

    public List<Usuario> usuarios() {
        return jdbc.sql("select id, nome, email, perfil, unidade_id, envolvido_id from usuario order by id")
                .query(Usuario.class).list();
    }

    /** RN3: a faixa da unidade macro, se configurada, prevalece sobre a global. */
    public ConfigRegras regras(long unidadeId) {
        return jdbc.sql("""
                select c.antecedencia_min,
                       coalesce(u.hora_min, c.hora_min) as hora_min,
                       coalesce(u.hora_max, c.hora_max) as hora_max
                  from configuracao c left join unidade u on u.id = :uni
                 where c.id = 1""").param("uni", unidadeId).query(ConfigRegras.class).single();
    }

    public Config config(long unidadeId) {
        return jdbc.sql("""
                select c.antecedencia_min, c.hora_min, c.hora_max, c.snp_endpoint,
                       u.hora_min as unidade_hora_min, u.hora_max as unidade_hora_max
                  from configuracao c left join unidade u on u.id = :uni
                 where c.id = 1""").param("uni", unidadeId).query(Config.class).single();
    }

    public void salvarConfig(long unidadeId, Config c) {
        jdbc.sql("update configuracao set antecedencia_min = :a, hora_min = :hmin, hora_max = :hmax, snp_endpoint = :snp where id = 1")
                .param("a", c.antecedenciaMin()).param("hmin", c.horaMin()).param("hmax", c.horaMax())
                .param("snp", c.snpEndpoint()).update();
        jdbc.sql("update unidade set hora_min = :hmin, hora_max = :hmax where id = :uni")
                .param("hmin", c.unidadeHoraMin()).param("hmax", c.unidadeHoraMax()).param("uni", unidadeId).update();
    }

    public String snpEndpoint() {
        return jdbc.sql("select snp_endpoint from configuracao where id = 1").query(String.class).single();
    }

    public List<Ambiente> ambientes(long unidadeId) {
        return jdbc.sql("select id, descricao, id_pai, ativo from ambiente where ativo and unidade_id = :uni order by descricao")
                .param("uni", unidadeId).query(Ambiente.class).list();
    }

    /** F9/RF01: todos os ambientes da unidade, inclusive inativos (tela de cadastro). */
    public List<Ambiente> ambientesTodos(long unidadeId) {
        return jdbc.sql("select id, descricao, id_pai, ativo from ambiente where unidade_id = :uni order by descricao")
                .param("uni", unidadeId).query(Ambiente.class).list();
    }

    public Optional<Ambiente> ambiente(long id, long unidadeId) {
        return jdbc.sql("select id, descricao, id_pai, ativo from ambiente where id = :id and unidade_id = :uni")
                .param("id", id).param("uni", unidadeId).query(Ambiente.class).optional();
    }

    public long inserirAmbiente(long unidadeId, String descricao, Long idPai, boolean ativo) {
        return jdbc.sql("""
                insert into ambiente (descricao, id_pai, ativo, unidade_id)
                values (:d, :p, :a, :uni) returning id""")
                .param("d", descricao).param("p", idPai).param("a", ativo).param("uni", unidadeId)
                .query(Long.class).single();
    }

    public void atualizarAmbiente(long id, long unidadeId, String descricao, Long idPai, boolean ativo) {
        jdbc.sql("update ambiente set descricao = :d, id_pai = :p, ativo = :a where id = :id and unidade_id = :uni")
                .param("d", descricao).param("p", idPai).param("a", ativo).param("id", id).param("uni", unidadeId)
                .update();
    }

    public List<Disposicao> disposicoes() {
        return jdbc.sql("select id, descricao, icone_arquivo from disposicao where ativo order by id")
                .query(Disposicao.class).list();
    }

    /** RN9: recursos oferecidos na unidade e, se vinculados a ambientes, só nesses ambientes. */
    public List<Recurso> recursosDisponiveis(Long ambienteId, long unidadeId) {
        return jdbc.sql("""
                select r.id, r.descricao, r.limitado, r.disponibilidade, r.icone_arquivo,
                       g.descricao as grupo, g.ordem as grupo_ordem
                  from recurso r join grupo_recurso g on g.id = r.grec_id
                 where r.ativo and g.ativo
                   and (r.unidade_id is null or r.unidade_id = :uni)
                   and (not exists (select 1 from vinculo_recurso v where v.recu_id = r.id)
                        or exists (select 1 from vinculo_recurso v where v.recu_id = r.id and v.ambi_id = :ambi))
                 order by g.ordem nulls last, g.descricao, r.descricao""")
                .param("uni", unidadeId).param("ambi", ambienteId == null ? -1L : ambienteId)
                .query(Recurso.class).list();
    }

    public List<Setor> setores() {
        return jdbc.sql("select id, descricao, email, emails_lista from envolvido where ativo order by descricao")
                .query(Setor.class).list();
    }
}
