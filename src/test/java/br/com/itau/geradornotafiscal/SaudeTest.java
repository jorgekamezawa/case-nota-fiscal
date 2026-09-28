package br.com.itau.geradornotafiscal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Endpoints de gestão expostos (F04-NF-01).
 */
@SpringBootTest
@AutoConfigureMockMvc
class SaudeTest {

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest(name = "F04-NF-01: {0} responde UP")
    @ValueSource(strings = {"/actuator/health/liveness", "/actuator/health/readiness"})
    void f04Nf01_vidaEProntidaoRespondemUp(String caminho) throws Exception {
        mockMvc.perform(get(caminho))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void f04Nf01_prontidaoNaoDependeDeSistemaExterno() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @ParameterizedTest(name = "F04-NF-01: {0} não é exposto")
    @ValueSource(strings = {"/actuator", "/actuator/metrics", "/actuator/env", "/actuator/info"})
    void f04Nf01_nenhumOutroEndpointDeGestao(String caminho) throws Exception {
        mockMvc.perform(get(caminho)).andExpect(status().isNotFound());
    }
}
