package br.com.itau.geradornotafiscal.config;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class MascaraDadosPessoaisTest {

    @ParameterizedTest(name = "F04-NF-05: {0} vira {1}")
    @CsvSource(delimiter = '|', value = {
            "CPF 887.403.470-95 recusado  | CPF *** recusado",
            "CPF 88740347095 recusado     | CPF *** recusado",
            "CNPJ 49.695.613/0001-80      | CNPJ ***",
            "CNPJ 49695613000180          | CNPJ ***",
            "dois: 88740347095 e 49695613000180 | dois: *** e ***",
            "pedido 123456789             | pedido 123456789",
            "número 123456789012345678    | número 123456789012345678"
    })
    void f04Nf05_mascaraCpfECnpjComESemPontuacao(String texto, String esperado) {
        assertThat(MascaraDadosPessoais.mascarar(texto.strip())).isEqualTo(esperado.strip());
    }
}
