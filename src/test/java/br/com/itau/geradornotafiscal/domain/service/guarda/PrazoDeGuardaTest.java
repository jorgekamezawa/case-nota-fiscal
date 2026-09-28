package br.com.itau.geradornotafiscal.domain.service.guarda;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PrazoDeGuardaTest {

    private final PrazoDeGuarda prazoDeGuarda = new PrazoDeGuarda();

    @ParameterizedTest(name = "E04 exemplo {0}: emitida em {1}, apagada a partir de {2}")
    @CsvSource({
            "1, 2026-03-15T10:00, 2032-01-01",
            "2, 2026-12-31T23:59, 2032-01-01",
            "3, 2027-01-01T00:00, 2033-01-01"})
    void e04Rn03_cincoAnosDesdeOPrimeiroDeJaneiroSeguinte(int exemplo, LocalDateTime emissao, LocalDate esperada) {
        assertEquals(esperada, prazoDeGuarda.apagarAPartirDe(emissao));
    }
}
