package br.mp.mpf.sisgares.dominio;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Implementação em memória de {@link DadosValidacao} e {@link DadosAmbiente} para os testes das regras. */
class DadosEmMemoria implements DadosValidacao, DadosAmbiente, DadosRecurso {

    final List<RecursoResumo> resumos = new ArrayList<>();
    final Map<Long, GrupoInfo> gruposPorId = new HashMap<>();
    final List<UsoRecurso> usosRecurso = new ArrayList<>();

    DadosEmMemoria resumo(RecursoResumo r) {
        resumos.add(r);
        return this;
    }

    DadosEmMemoria grupo(GrupoInfo g) {
        gruposPorId.put(g.id(), g);
        return this;
    }

    DadosEmMemoria pedir(long reservaId, long unidadeId, Long ambienteId, Integer qtd, Periodo p) {
        usosRecurso.add(new UsoRecurso(reservaId, unidadeId, ambienteId, qtd, p.inicio(), p.termino()));
        return this;
    }

    @Override
    public List<RecursoResumo> recursos(long unidadeId) {
        return resumos.stream().filter(r -> r.unidadeId() == null || r.unidadeId() == unidadeId).toList();
    }

    @Override
    public Map<Long, GrupoInfo> grupos() {
        return gruposPorId;
    }

    final Set<String> iconesEnviados = new HashSet<>();

    @Override
    public Set<String> icones() {
        Set<String> r = new HashSet<>(IconesRecurso.DISPONIVEIS);
        r.addAll(iconesEnviados);
        return r;
    }

    /** Nos testes os usos não distinguem recurso: cada cenário pede um só. */
    @Override
    public List<UsoRecurso> usosNaoTranscorridos(long recursoId, LocalDateTime agora) {
        return usosRecurso.stream().filter(u -> u.termino().isAfter(agora)).toList();
    }

    record Uso(long reservaId, long recursoId, int qtd, Periodo periodo) {
    }

    static final long UNIDADE_PADRAO = 1L;

    final Map<Long, Long> pai = new HashMap<>();
    final Map<Long, String> nomes = new HashMap<>();
    final Set<Long> inativos = new HashSet<>();
    final Map<Long, Long> unidades = new HashMap<>();
    final List<PeriodoOcupado> ocupados = new ArrayList<>();
    final Map<Long, RecursoInfo> recursos = new HashMap<>();
    final List<Uso> usos = new ArrayList<>();

    DadosEmMemoria ambiente(long id, String nome, Long idPai) {
        nomes.put(id, nome);
        if (idPai != null) {
            pai.put(id, idPai);
        }
        return this;
    }

    DadosEmMemoria inativar(long id) {
        inativos.add(id);
        return this;
    }

    DadosEmMemoria naUnidade(long id, long unidadeId) {
        unidades.put(id, unidadeId);
        return this;
    }

    DadosEmMemoria reservar(long reservaId, long ambienteId, Periodo p) {
        ocupados.add(new PeriodoOcupado(reservaId, ambienteId, nomes.get(ambienteId), p.inicio(), p.termino()));
        return this;
    }

    DadosEmMemoria recurso(RecursoInfo r) {
        recursos.put(r.id(), r);
        return this;
    }

    DadosEmMemoria usar(long reservaId, long recursoId, int qtd, Periodo p) {
        usos.add(new Uso(reservaId, recursoId, qtd, p));
        return this;
    }

    private AmbienteInfo info(long id) {
        return new AmbienteInfo(id, nomes.get(id), pai.get(id), !inativos.contains(id),
                unidades.getOrDefault(id, UNIDADE_PADRAO));
    }

    @Override
    public Optional<AmbienteInfo> ambiente(long ambienteId) {
        return nomes.containsKey(ambienteId) ? Optional.of(info(ambienteId)) : Optional.empty();
    }

    @Override
    public List<AmbienteInfo> ambientes(long unidadeId) {
        return nomes.keySet().stream().map(this::info).filter(a -> a.unidadeId() == unidadeId).toList();
    }

    @Override
    public List<PeriodoOcupado> periodosNaoTranscorridos(Set<Long> ambienteIds, LocalDateTime agora) {
        return ocupados.stream()
                .filter(o -> ambienteIds.contains(o.ambienteId()))
                .filter(o -> o.termino().isAfter(agora))
                .toList();
    }

    @Override
    public Set<Long> ambientesRelacionados(long ambienteId) {
        Set<Long> r = new HashSet<>();
        r.add(ambienteId);
        for (Long p = pai.get(ambienteId); p != null; p = pai.get(p)) {
            r.add(p);
        }
        boolean mudou = true;
        while (mudou) {
            mudou = false;
            for (var e : pai.entrySet()) {
                if (r.contains(e.getValue()) && isDescendente(e.getKey(), ambienteId) && r.add(e.getKey())) {
                    mudou = true;
                }
            }
        }
        return r;
    }

    private boolean isDescendente(long id, long ancestral) {
        for (Long p = pai.get(id); p != null; p = pai.get(p)) {
            if (p == ancestral) {
                return true;
            }
        }
        return false;
    }

    @Override
    public List<PeriodoOcupado> periodosOcupados(Set<Long> ambienteIds, LocalDateTime de, LocalDateTime ate,
            Long excluirReservaId) {
        Periodo janela = new Periodo(de, ate);
        return ocupados.stream()
                .filter(o -> ambienteIds.contains(o.ambienteId()))
                .filter(o -> !Objects.equals(o.reservaId(), excluirReservaId))
                .filter(o -> o.periodo().cruza(janela))
                .toList();
    }

    @Override
    public Optional<RecursoInfo> recurso(long recursoId) {
        return Optional.ofNullable(recursos.get(recursoId));
    }

    @Override
    public int quantidadeReservada(long recursoId, LocalDateTime inicio, LocalDateTime termino, Long excluirReservaId) {
        Periodo p = new Periodo(inicio, termino);
        return usos.stream()
                .filter(u -> u.recursoId() == recursoId)
                .filter(u -> !Objects.equals(u.reservaId(), excluirReservaId))
                .filter(u -> u.periodo().cruza(p))
                .mapToInt(Uso::qtd)
                .sum();
    }
}
