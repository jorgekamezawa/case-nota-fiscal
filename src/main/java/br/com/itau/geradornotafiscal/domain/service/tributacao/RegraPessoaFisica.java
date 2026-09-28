package br.com.itau.geradornotafiscal.domain.service.tributacao;

import br.com.itau.geradornotafiscal.domain.valueobject.RegimeTributacaoPJ;
import br.com.itau.geradornotafiscal.domain.valueobject.TipoPessoa;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Pessoa física (E01-RN-11).
 */
@Component
public class RegraPessoaFisica implements RegraTributacao {

    private static final FaixasDeAliquota FAIXAS = FaixasDeAliquota.de(
            FaixasDeAliquota.ate("499.99", "0"),
            FaixasDeAliquota.ate("2000.00", "0.12"),
            FaixasDeAliquota.ate("3500.00", "0.15"),
            FaixasDeAliquota.acima("0.17"));

    @Override
    public boolean aplicaA(TipoPessoa tipoPessoa, RegimeTributacaoPJ regime) {
        return tipoPessoa == TipoPessoa.FISICA;
    }

    @Override
    public BigDecimal aliquota(BigDecimal valorTotalItens) {
        return FAIXAS.aliquota(valorTotalItens);
    }
}
