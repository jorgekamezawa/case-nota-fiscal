package br.com.itau.geradornotafiscal.domain.service.tributacao;

import br.com.itau.geradornotafiscal.domain.valueobject.Destinatario;
import br.com.itau.geradornotafiscal.domain.valueobject.RegimeTributacaoPJ;
import br.com.itau.geradornotafiscal.domain.valueobject.TipoPessoa;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TributacaoTest {

    /** Regra fictícia, só deste teste: pessoa jurídica no regime OUTROS, 1% em qualquer valor. */
    static class RegraFicticia implements RegraTributacao {

        @Override
        public boolean aplicaA(TipoPessoa tipoPessoa, RegimeTributacaoPJ regime) {
            return tipoPessoa == TipoPessoa.JURIDICA && regime == RegimeTributacaoPJ.OUTROS;
        }

        @Override
        public BigDecimal aliquota(BigDecimal valorTotalItens) {
            return new BigDecimal("0.01");
        }
    }

    @Test
    @DisplayName("F03-NF-02: regra nova entra na lista como classe nova, sem alterar as existentes")
    void f03Nf02_regraNovaSemAlterarAsExistentes() {
        Tributacao tributacao = new Tributacao(List.of(new RegraPessoaFisica(), new RegraSimplesNacional(),
                new RegraLucroReal(), new RegraLucroPresumido(), new RegraFicticia()));

        assertEquals(new BigDecimal("0.01"), aliquota(tributacao, TipoPessoa.JURIDICA, RegimeTributacaoPJ.OUTROS, "1000.00"));
        assertEquals(new BigDecimal("0.12"), aliquota(tributacao, TipoPessoa.FISICA, null, "500.00"));
        assertEquals(new BigDecimal("0.07"), aliquota(tributacao, TipoPessoa.JURIDICA, RegimeTributacaoPJ.SIMPLES_NACIONAL, "1000.00"));
        assertEquals(new BigDecimal("0.15"), aliquota(tributacao, TipoPessoa.JURIDICA, RegimeTributacaoPJ.LUCRO_REAL, "3000.00"));
        assertEquals(new BigDecimal("0.16"), aliquota(tributacao, TipoPessoa.JURIDICA, RegimeTributacaoPJ.LUCRO_PRESUMIDO, "3000.00"));
    }

    static BigDecimal aliquota(Tributacao tributacao, TipoPessoa tipoPessoa, RegimeTributacaoPJ regime, String total) {
        return tributacao.aliquota(new Destinatario("Fulano", tipoPessoa, regime, List.of(), List.of()), new BigDecimal(total));
    }
}
