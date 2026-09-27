---
status: proposto
---
# ADR-0011: Enviar a telemetria para o Grafana Cloud

**Decisão:** o collector ([ADR-0007](0007-observabilidade-com-opentelemetry.md)) envia logs, métricas e traces para o **Grafana Cloud** (plano gratuito), porque é a mesma tela do ambiente local (`otel-lgtm`) e os mesmos dashboards, versionados no repositório, servem nos dois lugares.

## Contexto
O ADR-0007 deixou o destino da telemetria em aberto. O ambiente na AWS é de demonstração: sobe, é validado e é destruído sem sobras. O ambiente local já usa Grafana, e os alarmes do rollback do canary ficam no CloudWatch de qualquer forma, porque é o ECS que os lê ([ADR-0008](0008-deploy-canary-com-rollback-automatico.md)).

O que pesa, nesta ordem:
1. mesma tela e mesmos dashboards no local e na nuvem;
2. OpenTelemetry puro, sem nada do fornecedor no código;
3. custo zero para a demonstração;
4. nada de telemetria deixado na conta AWS após o `destroy`.

## Alternativas descartadas
- **CloudWatch com X-Ray, recebendo OTLP direto:** fica na própria conta e autentica pelo papel IAM, sem segredo. Mas a tela e a linguagem de consulta são outras, os dashboards não se reaproveitam do local, e o APM completo (Application Signals) exige o agente Java descartado no ADR-0007.
- **New Relic:** também grátis (100 GB por mês) e com APM completo a partir dos traces, mas é uma terceira tela, diferente do local, e os dashboards não se reaproveitam.
- **Datadog:** o APM mais completo do mercado, mas pago por host após 14 dias de teste, e o melhor dele depende do agente próprio.
- **Amazon Managed Prometheus com Managed Grafana:** mesma tela do local e dados dentro da AWS, mas são três serviços (métricas, logs no CloudWatch, traces no X-Ray), com licença de editor paga após 90 dias.

## Consequências
- **Ganhos:** o mesmo painel do notebook até a AWS; qualquer revisor roda o local e vê exatamente o dashboard usado na nuvem; custo zero; o `destroy` não precisa limpar telemetria.
- **Custos:** uma conta externa e um token de acesso para manter; retenção de 14 dias no plano gratuito; a telemetria sai da AWS pela NAT. Numa produção bancária, a saída de dados para fornecedor externo passaria por Segurança e LGPD, e esta decisão seria revista.
- **Passa a ser obrigatório:**
  - token do Grafana Cloud no Secrets Manager, lido pelo collector;
  - dashboards e alertas versionados no repositório (JSON) e usados no local e na nuvem;
  - métricas do CloudWatch (ALB, ECS) lidas pelo Grafana como fonte de dados, para um painel único;
  - o rollback do canary continua nos alarmes do CloudWatch.
