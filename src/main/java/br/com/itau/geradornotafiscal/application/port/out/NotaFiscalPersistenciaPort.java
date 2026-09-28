package br.com.itau.geradornotafiscal.application.port.out;

import br.com.itau.geradornotafiscal.domain.entity.NotaFiscal;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Guarda das notas emitidas (E04-RN-01 a E04-RN-03).
 */
public interface NotaFiscalPersistenciaPort {

    /**
     * Guarda a nota do pedido até a data de expurgo. Lança {@code NotaJaGuardadaException} se o pedido já tem nota,
     * {@code NotaGrandeDemaisException} se a nota não cabe e {@code ArmazenamentoIndisponivelException} se não há como guardar.
     */
    void guardar(Long idPedido, NotaFiscal nota, LocalDate apagarAPartirDe);

    /** Nota guardada do pedido, lida sempre na versão mais recente. */
    Optional<NotaFiscal> buscar(Long idPedido);
}
