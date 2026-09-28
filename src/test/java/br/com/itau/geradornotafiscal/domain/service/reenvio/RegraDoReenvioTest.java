package br.com.itau.geradornotafiscal.domain.service.reenvio;

import br.com.itau.geradornotafiscal.domain.exception.PedidoDivergenteException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RegraDoReenvioTest {

    private final RegraDoReenvio regra = new RegraDoReenvio();

    @Test
    @DisplayName("E03-RN-02: mesmo conteúdo (mesmo hash) vale a nota já emitida")
    void e03Rn02_mesmoConteudo() {
        assertDoesNotThrow(() -> regra.conferir(123L, "a".repeat(64), "a".repeat(64)));
    }

    @Test
    @DisplayName("E03-RN-03: conteúdo diferente (outro hash) é recusado por divergência, informando só o id_pedido")
    void e03Rn03_conteudoDiferente() {
        PedidoDivergenteException divergente =
                assertThrows(PedidoDivergenteException.class, () -> regra.conferir(123L, "a".repeat(64), "b".repeat(64)));

        assertEquals(123L, divergente.idPedido());
    }
}
