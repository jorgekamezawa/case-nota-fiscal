package br.com.itau.geradornotafiscal.domain.service.tributacao;

import br.com.itau.geradornotafiscal.domain.valueobject.RegimeTributacaoPJ;
import br.com.itau.geradornotafiscal.domain.valueobject.TipoPessoa;

import java.math.BigDecimal;

/**
 * Regra de alíquota de um tipo de pessoa ou regime. Regra nova é uma classe nova com {@code @Component}:
 * a {@link Tributacao} a recebe na lista injetada, sem alterar as existentes (F03-NF-02).
 */
public interface RegraTributacao {

    boolean aplicaA(TipoPessoa tipoPessoa, RegimeTributacaoPJ regime);

    BigDecimal aliquota(BigDecimal valorTotalItens);
}
