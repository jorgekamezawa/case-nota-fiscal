package br.com.itau.geradornotafiscal.adapter.out.dynamodb;

import br.com.itau.geradornotafiscal.application.exception.ArmazenamentoIndisponivelException;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;

import java.util.function.Supplier;

/**
 * Traduz as falhas do DynamoDB: falha de conexão, erro do serviço ou limite de vazão, depois das novas tentativas do
 * SDK, é indisponibilidade, contada na métrica {@code armazenamento.falhas} (E04-RN-01, E04-NF-04).
 */
@Component
@RequiredArgsConstructor
public class ChamadasDynamoDb {

    private final MeterRegistry meterRegistry;

    public <T> T chamar(String operacao, Supplier<T> chamada) {
        try {
            return chamada.get();
        } catch (SdkClientException e) {
            throw indisponivel(operacao, e);
        } catch (DynamoDbException e) {
            if (e.statusCode() >= 500 || e.isThrottlingException()) {
                throw indisponivel(operacao, e);
            }
            throw e;
        }
    }

    public ArmazenamentoIndisponivelException indisponivel(String operacao, Exception causa) {
        meterRegistry.counter("armazenamento.falhas", "operacao", operacao).increment();
        return new ArmazenamentoIndisponivelException(causa);
    }
}
