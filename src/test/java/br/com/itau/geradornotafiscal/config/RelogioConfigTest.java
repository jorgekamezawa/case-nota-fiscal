package br.com.itau.geradornotafiscal.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RelogioConfigTest {

    @Test
    @DisplayName("E01-NF-02: relógio da aplicação no horário de America/Sao_Paulo")
    void e01Nf02_relogioEmSaoPaulo() {
        assertEquals(ZoneId.of("America/Sao_Paulo"), new RelogioConfig().relogio().getZone());
    }
}
