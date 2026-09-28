package br.com.itau.geradornotafiscal.adapter.out.dynamodb;

import br.com.itau.geradornotafiscal.adapter.out.dynamodb.mappers.NotaFiscalRegistroMapper;
import br.com.itau.geradornotafiscal.domain.entity.NotaFiscal;
import br.com.itau.geradornotafiscal.domain.valueobject.Destinatario;
import br.com.itau.geradornotafiscal.domain.valueobject.Documento;
import br.com.itau.geradornotafiscal.domain.valueobject.Endereco;
import br.com.itau.geradornotafiscal.domain.valueobject.Finalidade;
import br.com.itau.geradornotafiscal.domain.valueobject.ItemNotaFiscal;
import br.com.itau.geradornotafiscal.domain.valueobject.Regiao;
import br.com.itau.geradornotafiscal.domain.valueobject.RegimeTributacaoPJ;
import br.com.itau.geradornotafiscal.domain.valueobject.TipoDocumento;
import br.com.itau.geradornotafiscal.domain.valueobject.TipoPessoa;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Medição do E04-NF-06: nota com os textos no tamanho máximo dos campos equivalentes do leiaute da NF-e (cProd 60,
 * xProd 120, xNome 60, endereço 60 por campo, UF 2, CEP 8) e os valores no maior tamanho, com um caractere de 1 byte
 * e com um de 2 bytes. O máximo de 800 linhas (E04-RN-04) cabe no pior caso; 990, o limite da NF-e, não.
 */
class TamanhoDaNotaTest {

    private final NotaFiscalDynamoAdapter adapter =
            new NotaFiscalDynamoAdapter(null, new NotaFiscalRegistroMapper(), new SimpleMeterRegistry(), "notas");

    @ParameterizedTest(name = "E04-NF-06: {0} linhas com o caractere ''{1}'' cabem? {2}")
    @CsvSource({
            "800, á, true",
            "990, a, true",
            "990, á, false"})
    void e04Nf06_medicao(int linhas, String caractere, boolean cabe) {
        int tamanho = NotaFiscalDynamoAdapter.tamanho(adapter.item(Long.MAX_VALUE, nota(linhas, caractere), LocalDate.MAX));
        System.out.printf("E04-NF-06 medição: %d linhas, caractere '%s', %d bytes de %d (%.0f%%)%n", linhas, caractere,
                tamanho, NotaFiscalDynamoAdapter.TAMANHO_MAXIMO_EM_BYTES,
                100.0 * tamanho / NotaFiscalDynamoAdapter.TAMANHO_MAXIMO_EM_BYTES);

        assertEquals(cabe, tamanho <= NotaFiscalDynamoAdapter.TAMANHO_MAXIMO_EM_BYTES);
    }

    static NotaFiscal nota(int linhas, String caractere) {
        BigDecimal maiorValor = new BigDecimal("9999999999999.99");
        List<ItemNotaFiscal> itens = IntStream.range(0, linhas)
                .mapToObj(i -> new ItemNotaFiscal(caractere.repeat(60), caractere.repeat(120), maiorValor,
                        BigDecimal.valueOf(Integer.MAX_VALUE), maiorValor))
                .toList();
        String campo = caractere.repeat(60);
        Destinatario destinatario = new Destinatario(campo, TipoPessoa.JURIDICA, RegimeTributacaoPJ.SIMPLES_NACIONAL,
                List.of(new Documento("49.695.613/0001-80", TipoDocumento.CNPJ)),
                List.of(new Endereco("03105003", campo, campo, campo, campo, "SP", campo, campo,
                        Finalidade.COBRANCA_ENTREGA, Regiao.CENTRO_OESTE)));
        return NotaFiscal.reconstituir("7d1c5b1e-2f4a-4c1b-9a53-0c7c4e9b2a10", LocalDateTime.of(2026, 12, 31, 23, 59, 59, 999_000_000),
                maiorValor, maiorValor, itens, destinatario);
    }
}
