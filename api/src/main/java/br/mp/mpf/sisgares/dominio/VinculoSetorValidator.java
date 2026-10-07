package br.mp.mpf.sisgares.dominio;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Regras do vínculo de setores envolvidos com um ambiente (RF03). Classe pura.
 * <ul>
 * <li>Cada vínculo informa um setor da unidade (setor sem unidade vale para todas). Setor inativo
 * não pode ser vinculado de novo, mas um vínculo antigo com ele pode ser mantido.</li>
 * <li>O mesmo setor não pode aparecer duas vezes.</li>
 * <li>O código de serviço do SNP é opcional; se informado, tem até 50 caracteres, sem espaços.</li>
 * </ul>
 */
public final class VinculoSetorValidator {

    public static final String REGRA = "RF03";
    public static final int TAMANHO_CODIGO = 50;
    private static final Pattern CODIGO = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._/-]*$");

    private VinculoSetorValidator() {
    }

    /** Código normalizado: sem espaços nas pontas e null quando vazio. */
    public static String codigo(String codServicoSnp) {
        if (codServicoSnp == null || codServicoSnp.isBlank()) {
            return null;
        }
        return codServicoSnp.strip();
    }

    /**
     * @param setores       setores conhecidos, por id (ativos e inativos, de qualquer unidade)
     * @param jaVinculados  setores já vinculados: podem ser mantidos mesmo se inativados depois
     */
    public static List<Erro> validar(List<VinculoSetorInput> vinculos, long unidadeId, Map<Long, SetorInfo> setores,
            Set<Long> jaVinculados) {
        List<Erro> erros = new ArrayList<>();
        if (vinculos == null) {
            return List.of(new Erro(REGRA, "Informe a lista de setores (pode ser vazia)."));
        }
        Set<Long> vistos = new HashSet<>();
        for (int i = 0; i < vinculos.size(); i++) {
            VinculoSetorInput v = vinculos.get(i);
            int n = i + 1;
            if (v == null || v.setorId() == null) {
                erros.add(new Erro(REGRA, "Linha %d: escolha o setor.".formatted(n)));
                continue;
            }
            SetorInfo s = setores.get(v.setorId());
            if (s == null || (s.unidadeId() != null && s.unidadeId() != unidadeId)) {
                erros.add(new Erro(REGRA, "Linha %d: o setor escolhido não existe nesta unidade.".formatted(n)));
                continue;
            }
            if (!s.ativo() && !jaVinculados.contains(s.id())) {
                erros.add(new Erro(REGRA, "%s está inativo e não pode ser vinculado.".formatted(s.descricao())));
                continue;
            }
            if (!vistos.add(s.id())) {
                erros.add(new Erro(REGRA, "%s aparece mais de uma vez.".formatted(s.descricao())));
            }
            String cod = codigo(v.codServicoSnp());
            if (cod != null && (cod.length() > TAMANHO_CODIGO || !CODIGO.matcher(cod).matches())) {
                erros.add(new Erro(REGRA, "%s: o código de serviço do SNP deve ter até %d caracteres, sem espaços (ex.: SEG-0401)."
                        .formatted(s.descricao(), TAMANHO_CODIGO)));
            }
        }
        return erros;
    }
}
