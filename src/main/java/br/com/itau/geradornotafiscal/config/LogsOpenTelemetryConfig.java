package br.com.itau.geradornotafiscal.config;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Liga o appender do OpenTelemetry declarado no logback-spring.xml ao SDK criado pelo Boot, que não faz isso sozinho
 * (F04-NF-09).
 */
@Configuration(proxyBeanMethods = false)
public class LogsOpenTelemetryConfig {

    @Bean
    InitializingBean instalarAppenderOpenTelemetry(OpenTelemetry openTelemetry) {
        return () -> OpenTelemetryAppender.install(openTelemetry);
    }
}
