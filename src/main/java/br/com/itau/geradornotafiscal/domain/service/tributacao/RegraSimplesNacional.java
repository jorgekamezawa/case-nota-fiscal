package br.com.itau.geradornotafiscal.domain.service.tributacao;

import br.com.itau.geradornotafiscal.domain.valueobject.RegimeTributacaoPJ;
import br.com.itau.geradornotafiscal.domain.valueobject.TipoPessoa;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Pessoa jurídica no Simples Nacional (E01-RN-12).
 */
@Component
public class RegraSimplesNacional implements RegraTributacao {

    private static final FaixasDeAliquota FAIXAS = FaixasDeAliquota.de(
            FaixasDeAliquota.ate("999.99", "0.03"),
            FaixasDeAliquota.ate("2000.00", "0.07"),
            FaixasDeAliquota.ate("5000.00", "0.13"),
            FaixasDeAliquota.acima("0.19"));

    @Override
    public boolean aplicaA(TipoPessoa tipoPessoa, RegimeTributacaoPJ regime) {
        return tipoPessoa == TipoPessoa.JURIDICA && regime == RegimeTributacaoPJ.SIMPLES_NACIONAL;
    }

    @Override
    public BigDecimal aliquota(BigDecimal valorTotalItens) {
        return FAIXAS.aliquota(valorTotalItens);
    }
}
