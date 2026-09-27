---
status: proposto
---
# ADR-0004: Executar o serviço em ECS com Fargate

**Decisão:** rodar o serviço em **ECS com Fargate** (containers sem servidores para gerenciar), atrás de um ALB interno, porque atende sem cold start, com escala automática e deploy gradual nativo, e não traz cluster nem máquinas para o time manter.

## Contexto
O serviço é uma API Spring Boot que passa boa parte do tempo esperando I/O (registro de 0,5 s; integrações de até 5,35 s) e precisa rodar em rede privada. Se a seção 6.2 da RFC confirmar processamento em segundo plano, ele também precisa de um processo contínuo.

O que pesa, nesta ordem:
1. nenhum cold start (inicialização a frio) no caminho da resposta;
2. escala automática e alta disponibilidade em mais de uma zona (AZ);
3. deploy gradual com rollback;
4. custo que não cresce com o tempo de espera das integrações simuladas;
5. operação proporcional a um serviço único.

## Alternativas descartadas
- **EKS (Kubernetes gerenciado):** atende os quatro primeiros pontos, com canary mais sofisticado (Argo Rollouts analisa métricas e decide sozinho). Mas um cluster só para este serviço traz plano de controle pago, upgrades de versão do Kubernetes, add-ons e segurança do cluster. Passa a ser a escolha certa se a empresa já mantém uma plataforma EKS: o custo marginal cai a quase zero e seguir o padrão vale mais.
- **Lambda com SnapStart:** o SnapStart restaura um snapshot da inicialização e reduz o cold start, mas eliminá-lo exige concorrência provisionada, cobrada por hora. E a Lambda cobra duração × memória: cada espera simulada é paga em toda chamada. Um processo contínuo viraria outra Lambda agendada, e o volume de conexões exigiria RDS Proxy.
- **EC2 com Auto Scaling:** menor custo por vCPU, mas imagem do sistema, patches e deploy gradual ficam por conta do time.

## Consequências
- **Ganhos:** blue/green, linear e canary nativos do ECS, com rollback por alarme; sem servidores nem cluster para manter.
- **Custos:** menos controle de máquina e rede; custo por vCPU maior que EC2; escalar leva cerca de 1 minuto.
- **Passa a ser obrigatório:** no mínimo 2 tarefas em AZs diferentes. Se houver processamento em segundo plano, ele roda no mesmo serviço até as métricas mostrarem disputa de recursos com a API; a separação fica em aberto.
