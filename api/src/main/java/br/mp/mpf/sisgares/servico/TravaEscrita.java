package br.mp.mpf.sisgares.servico;

import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

/**
 * Serializa as escritas que dependem das regras de conflito: reservas (RN7) e hierarquia de ambientes (RN6).
 * Sem isso, uma reserva poderia ser validada com a hierarquia antiga enquanto o pai do ambiente muda.
 * Para várias instâncias na AWS, trocar por lock no banco (ex.: pg_advisory_xact_lock).
 */
@Component
public class TravaEscrita {

    private final ReentrantLock lock = new ReentrantLock();

    public <T> T executar(Supplier<T> acao) {
        lock.lock();
        try {
            return acao.get();
        } finally {
            lock.unlock();
        }
    }
}
