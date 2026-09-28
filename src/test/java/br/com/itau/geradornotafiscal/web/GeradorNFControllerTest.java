package br.com.itau.geradornotafiscal.web;

import br.com.itau.geradornotafiscal.PedidoBase;
import br.com.itau.geradornotafiscal.service.impl.EntregaService;
import br.com.itau.geradornotafiscal.service.impl.EstoqueService;
import br.com.itau.geradornotafiscal.service.impl.FinanceiroService;
import br.com.itau.geradornotafiscal.service.impl.RegistroService;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static br.com.itau.geradornotafiscal.PedidoBase.endereco;
import static br.com.itau.geradornotafiscal.PedidoBase.enderecos;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Comportamento do endpoint, com as integrações simuladas por mock (sem as esperas).
 */
@SpringBootTest
@AutoConfigureMockMvc
class GeradorNFControllerTest {

    private static final String ENDPOINT = "/api/pedido/gerarNotaFiscal";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EstoqueService estoqueService;
    @MockBean
    private RegistroService registroService;
    @MockBean
    private EntregaService entregaService;
    @MockBean
    private FinanceiroService financeiroService;

    @Test
    @DisplayName("E01 cálculo #26 (E01-RN-17): nota devolve bairro, cidade e país do endereço")
    void e01Calculo26_enderecoComBairroCidadePais() throws Exception {
        ObjectNode entrega = endereco("ENTREGA", "SUDESTE");
        entrega.put("bairro", "Mooca");
        entrega.put("cidade", "São Paulo");
        entrega.put("pais", "Brasil");

        enviar(enderecos(PedidoBase.novo(), entrega))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.destinatario.enderecos[0].bairro").value("Mooca"))
                .andExpect(jsonPath("$.destinatario.enderecos[0].cidade").value("São Paulo"))
                .andExpect(jsonPath("$.destinatario.enderecos[0].pais").value("Brasil"));
    }

    private ResultActions enviar(ObjectNode pedido) throws Exception {
        return mockMvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(pedido.toString()));
    }
}
