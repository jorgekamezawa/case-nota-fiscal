package br.com.itau.geradornotafiscal.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.cfg.JsonNodeFeature;
import tools.jackson.databind.cfg.MutableCoercionConfig;
import tools.jackson.databind.type.LogicalType;

@Configuration
public class JacksonConfig {

    // Números decimais lidos como decimal exato, com as casas enviadas, também na árvore JSON (E01-NF-06, E01-RN-08).
    @Bean
    public JsonMapperBuilderCustomizer decimaisExatos() {
        return builder -> builder
                .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .disable(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES);
    }

    // Sem conversão silenciosa na entrada (E01-RN-08): texto (inclusive vazio) no lugar de número, decimal em campo inteiro
    // e número no lugar de valor da lista são erro de formato. A data tem conversor próprio no contrato de entrada.
    @Bean
    public JsonMapperBuilderCustomizer entradaEstrita() {
        return builder -> builder
                .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                .enable(EnumFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
                .withCoercionConfig(LogicalType.Integer, JacksonConfig::semTexto)
                .withCoercionConfig(LogicalType.Float, JacksonConfig::semTexto);
    }

    private static void semTexto(MutableCoercionConfig regra) {
        regra.setCoercion(CoercionInputShape.String, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.EmptyString, CoercionAction.Fail);
    }
}
