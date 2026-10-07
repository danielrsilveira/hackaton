package br.mp.mpf.sisgares.infra;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import br.mp.mpf.sisgares.dominio.ConfigRegras;
import br.mp.mpf.sisgares.dominio.SetorInfo;
import br.mp.mpf.sisgares.dominio.VinculoSetorInput;

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

    /** F9/RF01: setor na tela de cadastro, com quantos ambientes e recursos o referenciam. */
    public record SetorCadastro(long id, String descricao, String email, String emailsLista, boolean ativo,
            int ambientes, int recursos) {
    }

    /** RF03: setor vinculado a um ambiente. Setor inativo continua listado, mas não é notificado. */
    public record VinculoSetor(long setorId, String setor, boolean setorAtivo, String codServicoSnp) {
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

    /** Mapeamento do login (claim email do Cognito) para o usuário; único pelo índice ux_usuario_email. */
    public Optional<Usuario> usuarioPorEmail(String email) {
        return jdbc.sql("select id, nome, email, perfil, unidade_id, envolvido_id from usuario where lower(email) = lower(:email)")
                .param("email", email.strip()).query(Usuario.class).optional();
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

    /** F9/RF02: todos os ambientes da unidade, inclusive inativos (tela de cadastro). */
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

    /** RF03: setores notificados nas reservas do ambiente. */
    public List<VinculoSetor> setoresDoAmbiente(long ambienteId) {
        return jdbc.sql("""
                select e.id as setor_id, e.descricao as setor, e.ativo as setor_ativo, ea.cod_servico_snp
                  from envolvido_ambiente ea join envolvido e on e.id = ea.envo_id
                 where ea.ambi_id = :a order by e.descricao""")
                .param("a", ambienteId).query(VinculoSetor.class).list();
    }

    /** RF03: substitui todos os vínculos do ambiente (lista já validada e normalizada). */
    public void substituirSetoresDoAmbiente(long ambienteId, List<VinculoSetorInput> vinculos) {
        jdbc.sql("delete from envolvido_ambiente where ambi_id = :a").param("a", ambienteId).update();
        for (VinculoSetorInput v : vinculos) {
            jdbc.sql("insert into envolvido_ambiente (envo_id, ambi_id, cod_servico_snp) values (:e, :a, :c)")
                    .param("e", v.setorId()).param("a", ambienteId).param("c", v.codServicoSnp()).update();
        }
    }

    private static final String SETOR_CADASTRO = """
            select e.id, e.descricao, e.email, e.emails_lista, e.ativo,
                   (select count(*) from envolvido_ambiente ea where ea.envo_id = e.id) as ambientes,
                   (select count(*) from envolvido_recurso er where er.envo_id = e.id) as recursos
              from envolvido e where e.unidade_id = :uni""";

    /** F9/RF01: setores da unidade, inclusive inativos, com a quantidade de vínculos. */
    public List<SetorCadastro> setoresDaUnidade(long unidadeId) {
        return jdbc.sql(SETOR_CADASTRO + " order by e.descricao").param("uni", unidadeId)
                .query(SetorCadastro.class).list();
    }

    public Optional<SetorCadastro> setor(long id, long unidadeId) {
        return jdbc.sql(SETOR_CADASTRO + " and e.id = :id").param("uni", unidadeId).param("id", id)
                .query(SetorCadastro.class).optional();
    }

    public List<SetorInfo> setoresInfo(long unidadeId) {
        return jdbc.sql("select id, descricao, ativo, unidade_id from envolvido where unidade_id = :uni")
                .param("uni", unidadeId).query(SetorInfo.class).list();
    }

    public long inserirSetor(long unidadeId, String descricao, String email, String emailsLista, boolean ativo) {
        return jdbc.sql("""
                insert into envolvido (descricao, email, emails_lista, ativo, unidade_id)
                values (:d, :e, :l, :a, :uni) returning id""")
                .param("d", descricao).param("e", email).param("l", emailsLista).param("a", ativo).param("uni", unidadeId)
                .query(Long.class).single();
    }

    public void atualizarSetor(long id, long unidadeId, String descricao, String email, String emailsLista, boolean ativo) {
        jdbc.sql("""
                update envolvido set descricao = :d, email = :e, emails_lista = :l, ativo = :a
                 where id = :id and unidade_id = :uni""")
                .param("d", descricao).param("e", email).param("l", emailsLista).param("a", ativo)
                .param("id", id).param("uni", unidadeId).update();
    }

    /** Todos os setores, ativos e inativos, para as regras de vínculo. */
    public Map<Long, SetorInfo> setoresInfo() {
        return jdbc.sql("select id, descricao, ativo, unidade_id from envolvido").query(SetorInfo.class).list()
                .stream().collect(Collectors.toMap(SetorInfo::id, s -> s));
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
