package br.com.itau.geradornotafiscal.service.frete;

import br.com.itau.geradornotafiscal.model.Regiao;
import br.com.itau.geradornotafiscal.service.calculo.Arredondamento;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

/**
 * Frete da nota: valor do pedido acrescido do percentual da região de entrega (E01-RN-15).
 */
@Component
public class CalculadoraFrete {

    private static final Map<Regiao, BigDecimal> FATOR_POR_REGIAO = new EnumMap<>(Map.of(
            Regiao.NORTE, new BigDecimal("1.08"),
            Regiao.NORDESTE, new BigDecimal("1.085"),
            Regiao.CENTRO_OESTE, new BigDecimal("1.07"),
            Regiao.SUDESTE, new BigDecimal("1.048"),
            Regiao.SUL, new BigDecimal("1.06")));

    public BigDecimal calcular(BigDecimal valorFrete, Regiao regiaoEntrega) {
        return Arredondamento.duasCasas(valorFrete.multiply(FATOR_POR_REGIAO.get(regiaoEntrega)));
    }
}
