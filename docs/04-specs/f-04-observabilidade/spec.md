# Spec F-04: observabilidade

| | |
|---|---|
| **Épico** | [Nota fiscal confiável](../../02-produto/epico-nota-fiscal-confiavel.md) (habilitador técnico) |
| **Fase** | 4 |
| **Status** | Concluída |
| **Decisão** | [ADR-0007](../../03-engenharia/adr/0007-observabilidade-com-opentelemetry.md), [ADR-0011](../../03-engenharia/adr/0011-telemetria-no-grafana-cloud.md) |

## Objetivo
Enxergar o serviço (saúde, logs, métricas e o caminho de cada requisição) antes da fase 5, a de maior risco.

## Parte funcional
Nenhuma: a fase não muda regra de negócio nem resposta.

## Parte não funcional

| ID | Requisito | Origem |
|---|---|---|
| F04-NF-01 | Endpoints de vida e de prontidão, que não dependem de sistema externo. Nenhum outro endpoint de gestão é exposto. | RFC 6.4; D-13 |
| F04-NF-02 | Cada requisição gera um trace. O contexto recebido no cabeçalho W3C `traceparent` é continuado. Cada integração (registro, estoque, entrega e financeiro) tem um span próprio com a sua duração, e erro fica marcado no span. | ADR-0007; RFC O-02 |
| F04-NF-03 | Logs em JSON estruturado em todos os ambientes, exceto no local, que usa texto legível no console. Cada linha traz `trace_id` e `span_id`. | ADR-0007; RFC 6.5 |
| F04-NF-04 | Toda requisição ao endpoint da nota gera ao menos um log, inclusive a de corpo inválido. Nota emitida: `id_pedido` e identificador da nota, em campos próprios, nunca na mensagem. Recusa: status e `type` de cada motivo, sem os valores recebidos nem o `id_pedido`, que pode não ser legível. Erro inesperado: stack trace. Os demais erros HTTP (404, 405, 415) ficam nas métricas das requisições. | D-13; ADR-0007 |
| F04-NF-05 | Nome, documento e endereço do destinatário nunca aparecem em log, métrica ou trace. Um ponto central mascara sequências de 11 e 14 dígitos na mensagem e na stack trace dos logs, em todas as saídas. Spans guardam só o tipo da exceção, sem a mensagem. Um teste envia um pedido válido, um recusado na etapa 1 e um na etapa 2, e procura esses dados nos logs e spans. | ADR-0007; RFC 6.2 (LGPD) |
| F04-NF-06 | Métricas das requisições: quantidade, erros e duração (p95 e p99) por endpoint e status, além das métricas da JVM. | RFC 6.4 (SLIs) |
| F04-NF-07 | Métricas de negócio: notas emitidas, recusas por `type` de erro e duração por integração. Os rótulos não levam dado pessoal nem valor sem limite (ex.: `id_pedido`). | RFC 6.4; RFC risco 3 |
| F04-NF-08 | Instrumentação só nos adaptadores e na configuração. Domínio e aplicação não conhecem as bibliotecas de telemetria, o que um teste de arquitetura confere. | ADR-0003; F03-NF-01 |
| F04-NF-09 | Os três sinais saem por OTLP. O destino é configuração, sem código de fornecedor. A aplicação envia 100% dos traces. Destino indisponível não afeta a resposta nem o build. | ADR-0007; ADR-0011 |
| F04-NF-10 | Dashboard versionado no repositório: requisições, erros, latência p95 e p99, notas emitidas, recusas por motivo e duração por integração. O mesmo arquivo serve no local e na nuvem. | ADR-0011 |
| F04-NF-11 | Alertas de SLO versionados, por burn rate: disponibilidade de 99,9% (4xx não contam) e latência p95 < 800 ms e p99 < 1,5 s. Até a fase 5, o alerta de latência dispara, porque a resposta ainda espera as integrações (cerca de 1,5 s). | RFC 6.4; ADR-0011 |
| F04-NF-12 | Um comando sobe o Grafana local com o dashboard e os alertas carregados. Uma requisição é seguida do log ao trace pelo `trace_id`. | RFC seção 7 (pronto da fase 4); ADR-0007 |
| F04-NF-13 | Contrato e comportamento inalterados: os testes existentes continuam verdes sem alteração, e as esperas simuladas não mudam. | RFC R-01, R-02, O-07 |

### Dependências
| Item | Uso | Escopo |
|---|---|---|
| Spring Boot Starter Actuator | Vida e prontidão (F04-NF-01) | Aplicação |
| Spring Boot Starter OpenTelemetry | Traces e métricas por OTLP (F04-NF-02, 06, 07, 09) | Aplicação |
| OpenTelemetry Logback Appender (`2.28.1-alpha`, compatível com o SDK gerenciado pelo Boot) | Logs por OTLP (F04-NF-09) | Aplicação |
| Spring Boot Starter OpenTelemetry Test | Traces nos testes (F04-NF-02, 05) | Só testes |
| OpenTelemetry SDK Testing | Spans e logs guardados em memória para os testes (F04-NF-02, 05) | Só testes |
| Imagem `grafana/otel-lgtm` | Grafana local (F04-NF-12) | Só local (compose) |

## Fora deste entregável
- Alarmes das integrações (DLQ, Pipe e tarefa pendente), SLO de conclusão das integrações, métrica e alarme de disponibilidade do DynamoDB e a prova do O-06 (entrega fora do ar vira alerta): fase 5.
- Métricas por consumidor (`client_id`): fase 6.
- Collector sidecar, amostragem no collector, Grafana Cloud, métricas do CloudWatch (ALB, ECS) como fonte de dados do Grafana e alarmes do canary no CloudWatch: fase 7.
