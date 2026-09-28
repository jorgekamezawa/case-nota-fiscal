package br.com.itau.geradornotafiscal.domain.service.tributacao;

import br.com.itau.geradornotafiscal.domain.valueobject.Destinatario;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Alíquota única do pedido, pela regra do tipo de pessoa e do regime (E01-RN-11 a E01-RN-13).
 */
@Component
@RequiredArgsConstructor
public class Tributacao {

    private final List<RegraTributacao> regras;

    public BigDecimal aliquota(Destinatario destinatario, BigDecimal valorTotalItens) {
        return regras.stream()
                .filter(regra -> regra.aplicaA(destinatario.tipoPessoa(), destinatario.regimeTributacao()))
                .findFirst()
                // A validação recusa antes o pedido sem regra (E01-RN-01, E01-RN-03).
                .orElseThrow(() -> new IllegalArgumentException("Sem regra de tributação para o destinatário"))
                .aliquota(valorTotalItens);
    }
}
