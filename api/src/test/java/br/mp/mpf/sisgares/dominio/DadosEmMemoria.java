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

/** Implementação em memória de {@link DadosValidacao} para os testes das regras. */
class DadosEmMemoria implements DadosValidacao {

    record Uso(long reservaId, long recursoId, int qtd, Periodo periodo) {
    }

    final Map<Long, Long> pai = new HashMap<>();
    final Map<Long, String> nomes = new HashMap<>();
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
