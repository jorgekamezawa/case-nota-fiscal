package br.com.itau.geradornotafiscal.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.UrlHandlerFilter;

@Configuration
public class BarraFinalConfig {

    // O Spring 6 deixou de aceitar a URL com barra no fim; o filtro mantém o comportamento anterior (F02-NF-02).
    @Bean
    public UrlHandlerFilter barraFinal() {
        return UrlHandlerFilter.trailingSlashHandler("/api/**").wrapRequest().build();
    }
}
