package br.com.itau.geradornotafiscal.application.exception;

/**
 * A nota passa do tamanho que o armazenamento aceita; nada foi guardado (E04-RN-04).
 */
public class NotaGrandeDemaisException extends RuntimeException {

    public NotaGrandeDemaisException() {
        super("Nota grande demais para ser guardada");
    }
}
