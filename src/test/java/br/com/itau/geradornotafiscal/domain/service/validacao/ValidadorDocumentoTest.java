package br.com.itau.geradornotafiscal.domain.service.validacao;

import br.com.itau.geradornotafiscal.domain.valueobject.TipoDocumento;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ValidadorDocumentoTest {

    @ParameterizedTest(name = "E01-RN-02: {0} {1} válido = {2}")
    @CsvSource({
            "CPF, 887.403.470-95, true",
            "CPF, 88740347095, true",
            "CPF, '887 403 470 95', true",
            "CPF, 887.403.470-96, false",
            "CPF, 887.403.470-9, false",
            "CPF, 111.111.111-11, false",
            "CPF, 529.982.247-25, true",
            "CNPJ, 49.695.613/0001-80, true",
            "CNPJ, 49695613000180, true",
            "CNPJ, 49.695.613/0001-81, false",
            "CNPJ, 11.222.333/0001-81, true",
            "CNPJ, 00.000.000/0000-00, false",
            "CNPJ, 887.403.470-95, false"
    })
    void e01Rn02_digitoVerificador(TipoDocumento tipo, String numero, boolean valido) {
        assertEquals(valido, ValidadorDocumento.valido(tipo, numero));
    }
}
