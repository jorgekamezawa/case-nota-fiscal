package br.com.itau.geradornotafiscal.domain.valueobject;

/** Sistemas acionados depois da emissão, cada um de forma independente (E02-RN-01). */
public enum Sistema {
    REGISTRO,
    ESTOQUE,
    ENTREGA,
    FINANCEIRO
}
