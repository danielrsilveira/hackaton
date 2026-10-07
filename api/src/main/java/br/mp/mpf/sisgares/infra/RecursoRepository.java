package br.mp.mpf.sisgares.infra;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import br.mp.mpf.sisgares.dominio.VinculoSetorInput;
import br.mp.mpf.sisgares.infra.CadastroRepository.VinculoSetor;

/** Cadastro de recursos (F9/RF06) e de seus vínculos com setores (RF07) e ambientes (RF08). */
@Repository
public class RecursoRepository {

    /** Recurso na tela de cadastro, com a quantidade de setores e ambientes vinculados. */
    public record RecursoCadastro(long id, String descricao, long grupoId, String grupo, Integer grupoOrdem,
            boolean limitado, int disponibilidade, String iconeArquivo, Long unidadeId, boolean ativo, int setores,
            int ambientes) {
    }

    public record Grupo(long id, String descricao, Integer ordem, boolean ativo) {
    }

    /** RF08: ambiente em que o recurso pode ser pedido. */
    public record AmbienteDoRecurso(long ambienteId, String ambiente, boolean ambienteAtivo, long unidadeId) {
    }

    private static final String CADASTRO = """
            select r.id, r.descricao, r.grec_id as grupo_id, g.descricao as grupo, g.ordem as grupo_ordem,
                   r.limitado, r.disponibilidade, r.icone_arquivo, r.unidade_id, r.ativo,
                   (select count(*) from envolvido_recurso er where er.recu_id = r.id) as setores,
                   (select count(*) from vinculo_recurso v where v.recu_id = r.id) as ambientes
              from recurso r join grupo_recurso g on g.id = r.grec_id
             where (r.unidade_id is null or r.unidade_id = :uni)""";

    private final JdbcClient jdbc;

    public RecursoRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Recursos oferecidos na unidade (os dela e os sem unidade), inclusive inativos. */
    public List<RecursoCadastro> listar(long unidadeId) {
        return jdbc.sql(CADASTRO + " order by g.ordem nulls last, g.descricao, r.descricao")
                .param("uni", unidadeId).query(RecursoCadastro.class).list();
    }

    public Optional<RecursoCadastro> recurso(long id, long unidadeId) {
        return jdbc.sql(CADASTRO + " and r.id = :id").param("uni", unidadeId).param("id", id)
                .query(RecursoCadastro.class).optional();
    }

    public List<Grupo> grupos() {
        return jdbc.sql("select id, descricao, ordem, ativo from grupo_recurso order by ordem nulls last, descricao")
                .query(Grupo.class).list();
    }

    public long inserir(String descricao, long grupoId, boolean limitado, int disponibilidade, String icone,
            Long unidadeId, boolean ativo) {
        return jdbc.sql("""
                insert into recurso (descricao, grec_id, limitado, disponibilidade, icone_arquivo, unidade_id, ativo)
                values (:d, :g, :l, :disp, :i, :uni, :a) returning id""")
                .param("d", descricao).param("g", grupoId).param("l", limitado).param("disp", disponibilidade)
                .param("i", icone).param("uni", unidadeId).param("a", ativo)
                .query(Long.class).single();
    }

    public void atualizar(long id, String descricao, long grupoId, boolean limitado, int disponibilidade, String icone,
            Long unidadeId, boolean ativo) {
        jdbc.sql("""
                update recurso set descricao = :d, grec_id = :g, limitado = :l, disponibilidade = :disp,
                       icone_arquivo = :i, unidade_id = :uni, ativo = :a
                 where id = :id""")
                .param("d", descricao).param("g", grupoId).param("l", limitado).param("disp", disponibilidade)
                .param("i", icone).param("uni", unidadeId).param("a", ativo).param("id", id)
                .update();
    }

    /** RF07: setores notificados quando o recurso é pedido. */
    public List<VinculoSetor> setores(long recursoId) {
        return jdbc.sql("""
                select e.id as setor_id, e.descricao as setor, e.ativo as setor_ativo, er.cod_servico_snp
                  from envolvido_recurso er join envolvido e on e.id = er.envo_id
                 where er.recu_id = :r order by e.descricao""")
                .param("r", recursoId).query(VinculoSetor.class).list();
    }

    /** RF07: substitui todos os setores do recurso (lista já validada e normalizada). */
    public void substituirSetores(long recursoId, List<VinculoSetorInput> vinculos) {
        jdbc.sql("delete from envolvido_recurso where recu_id = :r").param("r", recursoId).update();
        for (VinculoSetorInput v : vinculos) {
            jdbc.sql("insert into envolvido_recurso (envo_id, recu_id, cod_servico_snp) values (:e, :r, :c)")
                    .param("e", v.setorId()).param("r", recursoId).param("c", v.codServicoSnp()).update();
        }
    }

    /** RF08: todos os ambientes vinculados, de qualquer unidade. */
    public List<AmbienteDoRecurso> ambientes(long recursoId) {
        return jdbc.sql("""
                select a.id as ambiente_id, a.descricao as ambiente, a.ativo as ambiente_ativo, a.unidade_id
                  from vinculo_recurso v join ambiente a on a.id = v.ambi_id
                 where v.recu_id = :r order by a.descricao""")
                .param("r", recursoId).query(AmbienteDoRecurso.class).list();
    }

    /** RF08: substitui os vínculos com ambientes da unidade; os de outras unidades ficam. */
    public void substituirAmbientes(long recursoId, long unidadeId, List<Long> ambienteIds) {
        jdbc.sql("""
                delete from vinculo_recurso v using ambiente a
                 where a.id = v.ambi_id and v.recu_id = :r and a.unidade_id = :uni""")
                .param("r", recursoId).param("uni", unidadeId).update();
        for (Long a : ambienteIds) {
            jdbc.sql("insert into vinculo_recurso (recu_id, ambi_id) values (:r, :a)")
                    .param("r", recursoId).param("a", a).update();
        }
    }
}
