# Runbook: reprocessar a etapa que falhou

Quando usar: um alerta de **mensagem na DLQ** de um sistema (registro, estoque, entrega ou financeiro), o que quer dizer que uma tarefa falhou 5 vezes e ficou como `FALHOU` ([E-02](../../04-specs/e-02-resposta-sem-esperar/spec.md), E02-RN-05 e E02-RN-06). A nota continua válida e já foi entregue à origem; só a etapa daquele sistema falta.

**Prazo:** a DLQ guarda a mensagem por 14 dias, contados de quando o serviço a moveu para lá. Depois disso, a mensagem some, e o reprocessamento passa a exigir montar a mensagem à mão (passo 3).

## Antes de começar
- Confirme com o dono do sistema que ele voltou a responder. Reprocessar com o sistema ainda fora do ar só gera mais 5 falhas.
- Não há risco de duplicar a ação no sistema: toda chamada leva o identificador da nota, e o sistema descarta a repetição (E02-RN-03).
- As tabelas e as filas guardam só `id_pedido` e sistema; nenhum passo expõe dado pessoal.

## Passos
Os exemplos usam o sistema `ENTREGA` e o pedido `123`. Na AWS, rode com o papel de operação da conta do ambiente. No ambiente local, acrescente `--endpoint-url http://localhost:8000` (DynamoDB) ou `--endpoint-url http://localhost:9324` (SQS) e `--region us-east-1`.

**1. Ler as mensagens da DLQ e anotar os pedidos.** Cada mensagem traz `id_pedido` e `sistema`.
```
aws sqs get-queue-url --queue-name tarefas-entrega-dlq
aws sqs receive-message --queue-url <url-da-dlq> --max-number-of-messages 10 --visibility-timeout 300
```

**2. Voltar a tarefa de `FALHOU` para `PENDENTE`,** com as tentativas zeradas e de volta ao índice de pendentes. A fatia é `PENDENTE#` seguido do resto da divisão do `id_pedido` por 10 (123 dá `PENDENTE#3`); `pendente_desde` é o momento atual em milissegundos. A condição impede mexer numa tarefa que não falhou.
```
aws dynamodb update-item --table-name tarefas_integracao \
  --key '{"id_pedido":{"N":"123"},"sistema":{"S":"ENTREGA"}}' \
  --update-expression "SET #status = :pendente, tentativas = :zero, fatia_pendente = :fatia, pendente_desde = :agora, versao = versao + :um REMOVE bloqueada_ate" \
  --condition-expression "#status = :falhou" \
  --expression-attribute-names '{"#status":"status"}' \
  --expression-attribute-values '{":pendente":{"S":"PENDENTE"},":falhou":{"S":"FALHOU"},":zero":{"N":"0"},":um":{"N":"1"},":fatia":{"S":"PENDENTE#3"},":agora":{"N":"'"$(date +%s%3N)"'"}}'
```

**3. Devolver a mensagem à fila do sistema e apagá-la da DLQ,** com o `receipt-handle` lido no passo 1.
```
aws sqs send-message --queue-url <url-da-fila-tarefas-entrega> --message-body '{"id_pedido": "123", "sistema": "ENTREGA"}'
aws sqs delete-message --queue-url <url-da-dlq> --receipt-handle <receipt-handle>
```
Se o passo 3 não for feito, a reconciliação recoloca a tarefa na fila em até 20 minutos, porque ela voltou a pendente no passo 2.

**4. Conferir.** Em alguns segundos, a tarefa fica `CONCLUIDA`, e só o sistema reprocessado é acionado.
```
aws dynamodb get-item --table-name tarefas_integracao --key '{"id_pedido":{"N":"123"},"sistema":{"S":"ENTREGA"}}' --consistent-read
```
No Grafana, o painel "Mensagens na DLQ" volta a zero e o alerta se resolve. Se a tarefa voltar a falhar, ela retorna à DLQ depois de mais 5 tentativas: escale para o dono do sistema.
