package br.com.itau.geradornotafiscal.adapter.in.fila;

import br.com.itau.geradornotafiscal.adapter.in.fila.dto.MensagemTarefa;
import br.com.itau.geradornotafiscal.application.port.in.result.ResultadoTarefa;
import br.com.itau.geradornotafiscal.config.FilasDeTarefas;
import br.com.itau.geradornotafiscal.domain.valueobject.Sistema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiptHandleIsInvalidException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;

/**
 * Uma leitura contínua por fila, em virtual threads; cada mensagem é processada na sua, com um limite de chamadas
 * simultâneas por sistema, porque virtual threads não limitam sozinhas (ADR-0002, E02-NF-07). A mensagem só sai da
 * fila quando a tarefa termina (E02-NF-04).
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "fila.consumo.ativo", matchIfMissing = true)
public class ConsumidorDeTarefas implements SmartLifecycle {

    private final SqsClient sqs;
    private final FilasDeTarefas filas;
    private final ProcessadorDeTarefa processadorDeTarefa;
    private final Duration esperaMaxima;
    private final int concorrenciaPorSistema;
    private final List<Thread> leituras = new ArrayList<>();
    private volatile boolean ativo;

    public ConsumidorDeTarefas(SqsClient sqs, FilasDeTarefas filas, ProcessadorDeTarefa processadorDeTarefa,
                               @Value("${fila.espera-maxima:PT20S}") Duration esperaMaxima,
                               @Value("${fila.concorrencia-por-sistema:20}") int concorrenciaPorSistema) {
        this.sqs = sqs;
        this.filas = filas;
        this.processadorDeTarefa = processadorDeTarefa;
        this.esperaMaxima = esperaMaxima;
        this.concorrenciaPorSistema = concorrenciaPorSistema;
    }

    @Override
    public void start() {
        ativo = true;
        for (Sistema sistema : Sistema.values()) {
            Semaphore vagas = new Semaphore(concorrenciaPorSistema);
            leituras.add(Thread.ofVirtual().name("fila-" + FilasDeTarefas.nome(sistema)).start(() -> ler(sistema, vagas)));
        }
    }

    private void ler(Sistema sistema, Semaphore vagas) {
        while (ativo) {
            try {
                List<Message> mensagens = sqs.receiveMessage(recebimento -> recebimento
                        .queueUrl(filas.fila(sistema))
                        .maxNumberOfMessages(10)
                        .waitTimeSeconds((int) esperaMaxima.toSeconds())).messages();
                for (Message mensagem : mensagens) {
                    vagas.acquire();
                    Thread.ofVirtual().start(() -> {
                        try {
                            processar(sistema, mensagem);
                        } catch (RuntimeException e) {
                            // A mensagem não apagada volta na visibilidade: a tarefa não se perde (E02-NF-04).
                            log.atWarn().addKeyValue("sistema", sistema.name()).setCause(e).log("Falha ao processar a mensagem da tarefa");
                        } finally {
                            vagas.release();
                        }
                    });
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException e) {
                if (ativo) {
                    log.atWarn().addKeyValue("sistema", sistema.name()).setCause(e).log("Falha ao ler a fila de tarefas");
                }
            }
        }
    }

    private void processar(Sistema sistema, Message mensagem) {
        MensagemTarefa tarefa;
        try {
            tarefa = MensagemTarefa.ler(mensagem.body());
        } catch (RuntimeException e) {
            // Mensagem ilegível fica na fila; o limite de recebimentos a leva para a DLQ (E02-NF-05).
            log.atWarn().addKeyValue("sistema", sistema.name()).log("Mensagem de tarefa ilegível");
            return;
        }
        concluir(sistema, mensagem, processadorDeTarefa.processar(tarefa.idPedido(), tarefa.sistema()));
    }

    private void concluir(Sistema sistema, Message mensagem, ResultadoTarefa resultado) {
        switch (resultado) {
            case CONCLUIDA, JA_TERMINADA -> apagar(sistema, mensagem);
            // A mensagem não apagada volta quando vence a visibilidade da fila: é a nova tentativa (E02-NF-05).
            case NOVA_TENTATIVA, EM_EXECUCAO_POR_OUTRO -> { }
            // O SQS conta recebimentos, não falhas: na 5ª falha o próprio serviço move a mensagem (E02-NF-05).
            case FALHOU -> {
                sqs.sendMessage(envio -> envio.queueUrl(filas.dlq(sistema)).messageBody(mensagem.body()));
                apagar(sistema, mensagem);
            }
        }
    }

    // Processamento mais longo que a visibilidade: a mensagem já foi recebida de novo e o recibo antigo não vale;
    // quem a recebeu por último encontra a tarefa terminada e a apaga (E02-NF-04).
    private void apagar(Sistema sistema, Message mensagem) {
        try {
            sqs.deleteMessage(exclusao -> exclusao.queueUrl(filas.fila(sistema)).receiptHandle(mensagem.receiptHandle()));
        } catch (ReceiptHandleIsInvalidException recebidaDeNovo) {
            log.atInfo().addKeyValue("sistema", sistema.name()).log("Mensagem já recebida de novo; será apagada por quem a recebeu");
        }
    }

    @Override
    public void stop() {
        ativo = false;
        leituras.forEach(Thread::interrupt);
        leituras.clear();
    }

    @Override
    public boolean isRunning() {
        return ativo;
    }
}
