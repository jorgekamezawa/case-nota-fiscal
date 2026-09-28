package br.com.itau.geradornotafiscal.service.calculo;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ArredondamentoTest {

    @ParameterizedTest(name = "E01-RN-16: {0} vira {1} ({2})")
    @CsvSource({
            "0.344, 0.34, algarismo seguinte menor que 5",
            "0.346, 0.35, algarismo seguinte maior que 5",
            "0.3453, 0.35, 5 seguido de algarismo diferente de zero",
            "0.345, 0.34, 5 sem nada depois e 2ª casa par",
            "0.375, 0.38, 5 sem nada depois e 2ª casa ímpar",
            "0.34500, 0.34, 5 seguido só de zeros e 2ª casa par",
            "300.0015, 300.00, exemplo de cálculo 5",
            "100, 100.00, sem casas decimais"
    })
    void e01Rn16_arredondaNaSegundaCasa(String valor, String esperado, String caso) {
        assertEquals(esperado, Arredondamento.duasCasas(new BigDecimal(valor)).toPlainString());
    }
}
