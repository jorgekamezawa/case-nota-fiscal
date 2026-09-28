package br.com.itau.geradornotafiscal.domain.service.tributacao;

import java.math.BigDecimal;
import java.util.List;

/**
 * Faixas de alíquota pelo valor total dos itens; o teto pertence à faixa, e a última não tem teto (E01-RN-11 a E01-RN-13).
 */
public final class FaixasDeAliquota {

    private final List<Faixa> faixas;

    private FaixasDeAliquota(List<Faixa> faixas) {
        this.faixas = List.copyOf(faixas);
    }

    public static FaixasDeAliquota de(Faixa... faixas) {
        return new FaixasDeAliquota(List.of(faixas));
    }

    public static Faixa ate(String teto, String aliquota) {
        return new Faixa(new BigDecimal(teto), new BigDecimal(aliquota));
    }

    public static Faixa acima(String aliquota) {
        return new Faixa(null, new BigDecimal(aliquota));
    }

    public BigDecimal aliquota(BigDecimal valorTotalItens) {
        return faixas.stream()
                .filter(faixa -> faixa.teto() == null || valorTotalItens.compareTo(faixa.teto()) <= 0)
                .findFirst()
                .map(Faixa::aliquota)
                .orElseThrow();
    }

    public record Faixa(BigDecimal teto, BigDecimal aliquota) {
    }
}
