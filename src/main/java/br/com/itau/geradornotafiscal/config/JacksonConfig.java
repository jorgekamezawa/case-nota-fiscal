package br.com.itau.geradornotafiscal.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfig {

    // Números decimais lidos como decimal exato, com as casas enviadas, também na árvore JSON (E01-NF-06, E01-RN-08).
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer decimaisExatos() {
        return builder -> builder
                .featuresToEnable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .postConfigurer(objectMapper -> objectMapper.setNodeFactory(JsonNodeFactory.withExactBigDecimals(true)));
    }
}
