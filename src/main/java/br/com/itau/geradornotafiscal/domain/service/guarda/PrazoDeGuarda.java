package br.com.itau.geradornotafiscal.domain.service.guarda;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Prazo de guarda da nota (E04-RN-03): 5 anos contados de 1º de janeiro do ano seguinte à emissão.
 */
@Component
public class PrazoDeGuarda {

    private static final int ANOS_DE_GUARDA = 5;

    /** Primeiro dia em que a nota pode ser apagada; a data de emissão já está no horário de São Paulo. */
    public LocalDate apagarAPartirDe(LocalDateTime emissao) {
        return LocalDate.of(emissao.getYear() + 1 + ANOS_DE_GUARDA, 1, 1);
    }
}
