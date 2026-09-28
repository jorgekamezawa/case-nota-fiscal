package br.com.itau.geradornotafiscal.config;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JacksonConfigTest {

    @Test
    @DisplayName("E01-NF-06: decimal lido na árvore JSON como decimal exato, com as casas enviadas")
    void e01Nf06_decimalExatoComAsCasasEnviadas() throws Exception {
        JsonMapper.Builder builder = JsonMapper.builder();
        new JacksonConfig().decimaisExatos().customize(builder);
        ObjectMapper objectMapper = builder.build();

        JsonNode pedido = objectMapper.readTree("{\"valor_frete\": 10.555, \"valor_total_itens\": 100.00}");

        assertEquals("10.555", pedido.get("valor_frete").decimalValue().toPlainString());
        assertEquals("100.00", pedido.get("valor_total_itens").decimalValue().toPlainString());
    }
}
