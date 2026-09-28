package br.com.itau.geradornotafiscal.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.cfg.JsonNodeFeature;

@Configuration
public class JacksonConfig {

    // Números decimais lidos como decimal exato, com as casas enviadas, também na árvore JSON (E01-NF-06, E01-RN-08).
    @Bean
    public JsonMapperBuilderCustomizer decimaisExatos() {
        return builder -> builder
                .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .disable(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES);
    }
}
