package br.com.itau.geradornotafiscal.domain.service.tributacao;

import br.com.itau.geradornotafiscal.domain.valueobject.RegimeTributacaoPJ;
import br.com.itau.geradornotafiscal.domain.valueobject.TipoPessoa;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;

import static br.com.itau.geradornotafiscal.domain.service.tributacao.TributacaoTest.aliquota;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class TributacaoInjetadaTest {

    @Autowired
    private Tributacao tributacao;

    @Autowired
    private List<RegraTributacao> regras;

    @Test
    @DisplayName("F03-NF-02: as 4 regras reais chegam à Tributacao pela injeção, sem registro manual")
    void f03Nf02_regrasReaisInjetadas() {
        assertEquals(4, regras.size());
        assertEquals(new BigDecimal("0.12"), aliquota(tributacao, TipoPessoa.FISICA, null, "500.00"));
        assertEquals(new BigDecimal("0.07"), aliquota(tributacao, TipoPessoa.JURIDICA, RegimeTributacaoPJ.SIMPLES_NACIONAL, "1000.00"));
        assertEquals(new BigDecimal("0.15"), aliquota(tributacao, TipoPessoa.JURIDICA, RegimeTributacaoPJ.LUCRO_REAL, "3000.00"));
        assertEquals(new BigDecimal("0.16"), aliquota(tributacao, TipoPessoa.JURIDICA, RegimeTributacaoPJ.LUCRO_PRESUMIDO, "3000.00"));
    }
}
