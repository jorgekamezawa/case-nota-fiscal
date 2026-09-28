package br.com.itau.geradornotafiscal.application.exception;

/**
 * Outra gravação do mesmo pedido estava em andamento; a nota dela pode ainda não estar visível (E03-NF-01).
 */
public class ConflitoDeGravacaoException extends RuntimeException {

    public ConflitoDeGravacaoException(Throwable causa) {
        super("Conflito com outra gravação do mesmo pedido", causa);
    }
}
