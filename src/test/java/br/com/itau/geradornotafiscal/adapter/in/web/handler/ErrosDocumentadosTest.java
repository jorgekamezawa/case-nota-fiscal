package br.com.itau.geradornotafiscal.adapter.in.web.handler;

import br.com.itau.geradornotafiscal.adapter.in.web.validacao.MotivoEntrada;
import br.com.itau.geradornotafiscal.domain.exception.MotivoRegra;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ErrosDocumentadosTest {

    private static final Path CATALOGO = Path.of("docs/api/erros.md");

    @Test
    @DisplayName("E01-NF-03: todo type que a API pode responder está documentado no catálogo de erros")
    void e01Nf03_typesDocumentados() throws Exception {
        String catalogo = Files.readString(CATALOGO, StandardCharsets.UTF_8);

        List<String> naoDocumentados = Stream.concat(
                        Stream.of("pedido-invalido", "json-invalido", "erro-interno", "pedido-grande-demais", "servico-indisponivel", "pedido-divergente"),
                        Stream.concat(Arrays.stream(MotivoEntrada.values()).map(MotivoEntrada::codigo),
                                Arrays.stream(MotivoRegra.values()).map(MotivoRegra::codigo)))
                .filter(codigo -> !catalogo.contains("`/erros/" + codigo + "`"))
                .collect(Collectors.toList());

        assertEquals(List.of(), naoDocumentados);
    }
}
