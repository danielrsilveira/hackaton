package br.mp.mpf.sisgares.infra.seguranca;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import br.mp.mpf.sisgares.infra.CadastroRepository;

/** Consulta real (PostgreSQL local): mapeamento por e-mail sem diferenciar maiúsculas e e-mail único. */
@SpringBootTest
@Transactional
class UsuarioPorEmailDbTest {

    @Autowired CadastroRepository cadastros;
    @Autowired JdbcClient jdbc;

    @Test
    void encontraSemDiferenciarMaiusculas() {
        var u = cadastros.usuarioPorEmail("  ANA.Souza@EXEMPLO.gov.br ");
        assertThat(u).isPresent();
        assertThat(u.get().perfil()).isEqualTo("SOLICITANTE");
    }

    @Test
    void emailDesconhecidoNaoRetornaNada() {
        assertThat(cadastros.usuarioPorEmail("ninguem@exemplo.gov.br")).isEmpty();
    }

    @Test
    void indiceImpedeDoisUsuariosComOMesmoEmail() {
        assertThatThrownBy(() -> jdbc.sql(
                "insert into usuario (nome, email, perfil, unidade_id) values ('Duplicada', 'ANA.SOUZA@exemplo.gov.br', 'ADMIN', 1)")
                .update()).isInstanceOf(DataIntegrityViolationException.class);
    }
}
