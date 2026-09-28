package br.com.itau.geradornotafiscal.config;

import br.com.itau.geradornotafiscal.domain.valueobject.Sistema;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Uma fila e uma fila de erro (DLQ) por sistema: a lentidão de um não atrasa os outros (E02-NF-03). O endereço é
 * resolvido pelo nome na primeira vez que é usado.
 */
@Component
@RequiredArgsConstructor
public class FilasDeTarefas {

    private final SqsClient sqs;
    private final Map<String, String> enderecos = new ConcurrentHashMap<>();

    public static String nome(Sistema sistema) {
        return "tarefas-" + sistema.name().toLowerCase(Locale.ROOT);
    }

    public static String nomeDaDlq(Sistema sistema) {
        return nome(sistema) + "-dlq";
    }

    public String fila(Sistema sistema) {
        return endereco(nome(sistema));
    }

    public String dlq(Sistema sistema) {
        return endereco(nomeDaDlq(sistema));
    }

    private String endereco(String nome) {
        return enderecos.computeIfAbsent(nome, fila -> sqs.getQueueUrl(requisicao -> requisicao.queueName(fila)).queueUrl());
    }
}
