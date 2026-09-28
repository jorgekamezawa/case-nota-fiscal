package br.com.itau.geradornotafiscal.domain.service.tributacao;

import br.com.itau.geradornotafiscal.domain.valueobject.RegimeTributacaoPJ;
import br.com.itau.geradornotafiscal.domain.valueobject.TipoPessoa;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Pessoa jurídica no Lucro Presumido (E01-RN-12).
 */
@Component
public class RegraLucroPresumido implements RegraTributacao {

    private static final FaixasDeAliquota FAIXAS = FaixasDeAliquota.de(
            FaixasDeAliquota.ate("999.99", "0.03"),
            FaixasDeAliquota.ate("2000.00", "0.09"),
            FaixasDeAliquota.ate("5000.00", "0.16"),
            FaixasDeAliquota.acima("0.20"));

    @Override
    public boolean aplicaA(TipoPessoa tipoPessoa, RegimeTributacaoPJ regime) {
        return tipoPessoa == TipoPessoa.JURIDICA && regime == RegimeTributacaoPJ.LUCRO_PRESUMIDO;
    }

    @Override
    public BigDecimal aliquota(BigDecimal valorTotalItens) {
        return FAIXAS.aliquota(valorTotalItens);
    }
}
