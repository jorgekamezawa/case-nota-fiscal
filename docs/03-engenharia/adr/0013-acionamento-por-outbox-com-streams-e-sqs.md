---
status: proposto
---
# ADR-0013: Acionar os sistemas externos por outbox com DynamoDB Streams e filas SQS

**Decisão:** gravar uma tarefa por sistema externo na mesma transação da nota (outbox: a "caixa de saída" guardada junto com o dado) e levá-la até uma fila SQS por sistema pelo **DynamoDB Streams e EventBridge Pipes**, com uma reconciliação periódica, porque nenhum acionamento se perde se o serviço cair, cada sistema recebe a nota em segundos sem o cliente esperar e as novas tentativas ficam com um serviço gerenciado.

## Contexto
Registro, estoque, entrega e financeiro são sistemas externos, com esperas simuladas de 0,25 s a 5,35 s. Pelas decisões do [levantamento](../../01-levantamento/levantamento-regras-negocio.md):
- o cliente recebe a nota logo após a gravação, e os quatro são acionados depois (Q-10);
- nenhum acionamento pode se perder, mesmo se o serviço cair (Q-10);
- a falha gera nova tentativa sem duplicar a ação, alerta no mesmo dia e reprocessamento só da etapa que falhou (Q-11).

As notas e as tarefas ficam no DynamoDB ([ADR-0012](0012-persistencia-em-dynamodb.md)). O desenho completo, com o caminho de um pedido e cada falha coberta, está no [Spike-0001](../spikes/0001-persistencia-e-acionamento-das-integracoes.md).

## Alternativas descartadas
- **Publicar direto na fila depois de gravar a nota:** são duas gravações em sistemas diferentes, sem transação entre elas. Se o serviço cair entre a gravação e a publicação, a nota existe e nenhum sistema é acionado, sem que ninguém perceba.
- **Executar em segundo plano, em memória, após a resposta:** o mais simples, mas tudo o que está em andamento se perde num deploy ou numa queda, e as novas tentativas dependem do processo continuar vivo.
- **Leitura periódica da tabela de tarefas pelo serviço:** cada tarefa do ECS consulta as pendentes a cada 1 ou 2 segundos. Funciona com menos peças, mas as consultas crescem com o número de tarefas do ECS mesmo sem pedido nenhum, o acionamento espera o intervalo, e as novas tentativas e a fila de erro teriam de ser construídas à mão. Fica só como reconciliação, a cada poucos minutos.
- **Streams acionando uma Lambda que chama os sistemas:** elimina a fila, mas a Lambda cobra pelo tempo de execução, então cada espera simulada (até 5,35 s na entrega) seria paga em toda chamada, e o código das integrações passaria a viver fora do serviço, com deploy próprio.

## Consequências
- **Ganhos:**
  - nenhum acionamento se perde: a tabela de tarefas é a fonte da verdade, e a reconciliação recoloca o que ficou parado;
  - o cliente não espera nenhum dos quatro sistemas;
  - uma fila por sistema: a lentidão da entrega não atrasa os outros;
  - novas tentativas pela visibilidade da fila e fila de erro (DLQ) do SQS, sem agendador próprio;
  - o sistema é acionado em segundos, porque o Streams empurra cada tarefa, sem consulta periódica no caminho normal.
- **Custos:**
  - mais peças para operar: Streams, um Pipe, uma fila e uma DLQ por sistema;
  - o Pipe não tem emulador local gratuito: localmente, o teste parte da fila, e o Pipe é validado no ambiente AWS;
  - a entrega é "pelo menos uma vez": todo processamento precisa tolerar a mesma mensagem mais de uma vez;
  - o consumo das filas roda no mesmo serviço da API e disputa os mesmos recursos ([ADR-0004](0004-computacao-em-ecs-fargate.md)).
- **Passa a ser obrigatório:**
  - filtro dos Pipes só para inserções, para que mudança de status não gere mensagem nova;
  - execução única de cada tarefa por gravação condicional, e a mensagem só é apagada quando a tarefa termina;
  - a tarefa vai para a DLQ pelo próprio serviço ao marcar FALHOU, porque o SQS conta recebimentos, não falhas;
  - identificador da nota como chave de idempotência em toda chamada externa (T-03 da RFC);
  - alarme na DLQ de cada fila, com aviso à operação no mesmo dia;
  - alarme de falha de execução dos Pipes e de tarefa pendente há mais de 15 minutos;
  - métricas de tarefas pendentes, concluídas e com falha, por sistema.
