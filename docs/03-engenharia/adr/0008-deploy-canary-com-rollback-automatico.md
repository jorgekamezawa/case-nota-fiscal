---
status: proposto
---
# ADR-0008: Fazer deploy canary com rollback automático por alarme

**Decisão:** publicar cada versão com o **canary nativo do ECS** (a versão nova recebe uma parte do tráfego por um período de observação, depois 100%), com **smoke test antes do tráfego** e **rollback automático por alarme**, porque um defeito atinge só uma parte dos consumidores e é revertido sem depender de alguém olhando.

## Contexto
O serviço roda no ECS Fargate ([ADR-0004](0004-computacao-em-ecs-fargate.md)), que oferece blue/green, linear e canary nativos ([anúncio da AWS](https://aws.amazon.com/about-aws/whats-new/2025/10/amazon-ecs-built-in-linear-canary-deployments)). Uma versão com defeito como o D-01 precisa ser detectada e revertida antes de atingir todos os consumidores.

O que pesa: exposição gradual a tráfego real; rollback por métrica, e não por percepção; validar a versão nova antes do primeiro cliente; versões antiga e nova convivendo sem se quebrar.

## Alternativas descartadas
- **Rolling update (padrão do ECS):** troca as tarefas aos poucos, mas sem controle da porcentagem de tráfego nem validação antes do tráfego real; o circuit breaker do ECS reverte só quando o health check falha, não por erro de negócio ou latência.
- **Blue/green com virada total:** sobe a versão nova ao lado da antiga e vira 100% do tráfego de uma vez; o rollback é instantâneo, mas todos os consumidores recebem a versão nova no mesmo instante.
- **Linear:** aumenta o tráfego em degraus iguais (ex.: 10% a cada poucos minutos); mais suave que o canary, com deploy mais longo e ganho pequeno para um serviço único.

## Consequências
- **Ganhos:** um defeito na API atinge só a porcentagem do canary e é revertido automaticamente.
- **Custos:** o deploy dura no mínimo o tempo de observação; com pouco tráfego, a porcentagem do canary pode não gerar amostra suficiente para o alarme.
- **Passa a ser obrigatório:**
  - o smoke test roda num hook de ciclo de vida do ECS, antes de qualquer tráfego;
  - os alarmes do rollback são a taxa de 5xx, a latência p99 e a taxa de falha no processamento das filas, por versão;
  - a porcentagem e o tempo de observação são configuração do pipeline, calibrada com o volume real;
  - toda mudança é compatível com a versão anterior durante a convivência das duas.

  O comportamento do processamento em segundo plano e do banco durante o canary está na seção 6.2 da RFC.
