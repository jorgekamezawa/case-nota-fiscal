package br.com.itau.geradornotafiscal.application.exception;

/**
 * Já existe nota guardada para o pedido; a gravação condicional impediu a segunda nota (E03-NF-01).
 */
public class NotaJaGuardadaException extends RuntimeException {

    public NotaJaGuardadaException() {
        super("Já existe nota para o pedido");
    }
}
