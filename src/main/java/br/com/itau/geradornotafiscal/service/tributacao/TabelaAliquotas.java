package br.com.itau.geradornotafiscal.service.tributacao;

import br.com.itau.geradornotafiscal.model.RegimeTributacaoPJ;
import br.com.itau.geradornotafiscal.model.TipoPessoa;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Alíquota do pedido por tipo de pessoa, regime e faixa do valor total dos itens (E01-RN-11 a E01-RN-13).
 */
@Component
public class TabelaAliquotas {

    private static final List<Faixa> PESSOA_FISICA = List.of(
            new Faixa("499.99", "0"),
            new Faixa("2000.00", "0.12"),
            new Faixa("3500.00", "0.15"),
            new Faixa(null, "0.17"));

    private static final Map<RegimeTributacaoPJ, List<Faixa>> PESSOA_JURIDICA = new EnumMap<>(Map.of(
            RegimeTributacaoPJ.SIMPLES_NACIONAL, List.of(
                    new Faixa("999.99", "0.03"),
                    new Faixa("2000.00", "0.07"),
                    new Faixa("5000.00", "0.13"),
                    new Faixa(null, "0.19")),
            RegimeTributacaoPJ.LUCRO_REAL, List.of(
                    new Faixa("999.99", "0.03"),
                    new Faixa("2000.00", "0.09"),
                    new Faixa("5000.00", "0.15"),
                    new Faixa(null, "0.20")),
            RegimeTributacaoPJ.LUCRO_PRESUMIDO, List.of(
                    new Faixa("999.99", "0.03"),
                    new Faixa("2000.00", "0.09"),
                    new Faixa("5000.00", "0.16"),
                    new Faixa(null, "0.20"))));

    public BigDecimal aliquota(TipoPessoa tipoPessoa, RegimeTributacaoPJ regime, BigDecimal valorTotalItens) {
        List<Faixa> faixas = tipoPessoa == TipoPessoa.FISICA ? PESSOA_FISICA : PESSOA_JURIDICA.get(regime);
        if (faixas == null) {
            // A validação recusa antes o regime OUTROS e a PJ sem regime (E01-RN-01, E01-RN-03).
            throw new IllegalArgumentException("Regime sem alíquota: " + regime);
        }
        return faixas.stream()
                .filter(faixa -> faixa.teto == null || valorTotalItens.compareTo(faixa.teto) <= 0)
                .findFirst()
                .map(faixa -> faixa.aliquota)
                .orElseThrow();
    }

    private static final class Faixa {
        private final BigDecimal teto;
        private final BigDecimal aliquota;

        private Faixa(String teto, String aliquota) {
            this.teto = teto == null ? null : new BigDecimal(teto);
            this.aliquota = new BigDecimal(aliquota);
        }
    }
}
