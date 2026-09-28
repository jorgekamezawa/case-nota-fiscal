package br.com.itau.geradornotafiscal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.actuate.endpoint.HealthEndpointGroup;
import org.springframework.boot.health.actuate.endpoint.HealthEndpointGroups;
import org.springframework.boot.health.contributor.HealthContributors;
import org.springframework.boot.health.registry.HealthContributorRegistry;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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
    @Autowired
    private HealthEndpointGroups grupos;
    @Autowired
    private HealthContributorRegistry indicadores;

    @ParameterizedTest(name = "F04-NF-01: {0} responde UP")
    @ValueSource(strings = {"/actuator/health/liveness", "/actuator/health/readiness"})
    void f04Nf01_vidaEProntidaoRespondemUp(String caminho) throws Exception {
        mockMvc.perform(get(caminho))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void f04Nf01_vidaEProntidaoNaoDependemDeSistemaExterno() {
        assertThat(membros(grupos.get("liveness"))).containsExactly("livenessState");
        assertThat(membros(grupos.get("readiness"))).containsExactly("readinessState");
    }

    @ParameterizedTest(name = "F04-NF-01: {0} não é exposto")
    @ValueSource(strings = {"/actuator", "/actuator/metrics", "/actuator/env", "/actuator/info"})
    void f04Nf01_nenhumOutroEndpointDeGestao(String caminho) throws Exception {
        mockMvc.perform(get(caminho)).andExpect(status().isNotFound());
    }

    private List<String> membros(HealthEndpointGroup grupo) {
        return indicadores.stream().map(HealthContributors.Entry::name).filter(grupo::isMember).toList();
    }
}
