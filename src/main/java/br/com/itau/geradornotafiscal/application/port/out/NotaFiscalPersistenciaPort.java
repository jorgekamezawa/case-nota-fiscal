package br.com.itau.geradornotafiscal.application.port.out;

import br.com.itau.geradornotafiscal.domain.entity.NotaFiscal;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Guarda das notas emitidas (E04-RN-01 a E04-RN-03), com o hash do pedido para reconhecer o reenvio (E03-NF-02).
 */
public interface NotaFiscalPersistenciaPort {

    /**
     * Guarda a nota do pedido até a data de expurgo, só se o pedido não tem nota ou a dele venceu (E03-RN-06).
     * Lança {@code NotaJaGuardadaException} se o pedido já tem nota, {@code ConflitoDeGravacaoException} se outra
     * gravação do mesmo pedido está em andamento, {@code NotaGrandeDemaisException} se a nota não cabe e
     * {@code ArmazenamentoIndisponivelException} se não há como guardar.
     */
    void guardar(Long idPedido, NotaFiscal nota, String hashPedido, LocalDate apagarAPartirDe);

    /** Nota guardada e ainda no prazo, lida sempre na versão mais recente (E03-NF-01). */
    Optional<NotaGuardada> buscar(Long idPedido);

    record NotaGuardada(NotaFiscal nota, String hashPedido) {
    }
}
