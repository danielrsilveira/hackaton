package br.mp.mpf.sisgares.infra;

/** Usuário fictício (sem autenticação real na demonstração). */
public record Usuario(long id, String nome, String email, String perfil, long unidadeId, Long envolvidoId) {

    public boolean admin() {
        return "ADMIN".equals(perfil);
    }

    public boolean atendente() {
        return "ATENDENTE".equals(perfil);
    }

    /** Administrador ou atendente: vê dados das reservas de terceiros (necessidade de atendimento). */
    public boolean gestor() {
        return admin() || atendente();
    }
}
