package br.com.itau.geradornotafiscal.domain.service.calculo;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Arredondamento de valores monetários pela ABNT NBR 5891 (E01-RN-16).
 */
public final class Arredondamento {

    private Arredondamento() {
    }

    // HALF_EVEN sobre o valor exato aplica a NBR 5891: 5 seguido só de zeros vai para o par; seguido de outro algarismo, sobe.
    public static BigDecimal duasCasas(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_EVEN);
    }
}
