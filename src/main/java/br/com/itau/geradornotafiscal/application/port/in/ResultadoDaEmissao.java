package br.com.itau.geradornotafiscal.application.port.in;

import br.com.itau.geradornotafiscal.domain.entity.NotaFiscal;

/**
 * Nota devolvida ao consumidor e se ela foi emitida agora ou já existia (reenvio, E03-RN-02).
 */
public record ResultadoDaEmissao(NotaFiscal nota, boolean reenvio) {
}
