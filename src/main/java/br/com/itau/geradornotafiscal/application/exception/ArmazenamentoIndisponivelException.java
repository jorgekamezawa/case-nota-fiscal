package br.com.itau.geradornotafiscal.application.exception;

/**
 * O armazenamento das notas não respondeu; nada foi guardado (E04-RN-01).
 */
public class ArmazenamentoIndisponivelException extends RuntimeException {

    public ArmazenamentoIndisponivelException(Throwable causa) {
        super("Armazenamento das notas indisponível", causa);
    }
}
