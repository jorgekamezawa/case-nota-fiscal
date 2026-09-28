# Spec E-02: resposta sem esperar os sistemas acionados

| | |
|---|---|
| **Épico** | [Nota fiscal confiável](../../02-produto/epico-nota-fiscal-confiavel.md) |
| **Fase** | 5 |
| **Status** | Concluída |
| **Regras de negócio** | [Levantamento](../../01-levantamento/levantamento-regras-negocio.md) |
| **Decisão** | [ADR-0013](../../03-engenharia/adr/0013-acionamento-por-outbox-com-streams-e-sqs.md), [ADR-0002](../../03-engenharia/adr/0002-virtual-threads-para-esperas-de-io.md), [Spike-0001](../../03-engenharia/spikes/0001-persistencia-e-acionamento-das-integracoes.md) |

## História
Como sistema de origem, quero a nota assim que ela for guardada, sem esperar os quatro sistemas. Como dono de registro, estoque, entrega ou financeiro, quero receber cada nota uma única vez, com nova tentativa em falha e aviso no mesmo dia quando a falha persistir.

## Parte funcional

| ID | Regra | Origem |
|---|---|---|
| E02-RN-01 | A resposta espera só validar, calcular e guardar a nota. Registro, estoque, entrega e financeiro são acionados depois, cada um de forma independente, sem ordem entre eles. | Q-10 |
| E02-RN-02 | A nota e os quatro acionamentos são guardados juntos, ou nada é guardado. Nenhum acionamento se perde, mesmo se o serviço cair. Nenhum sistema é acionado antes de a nota estar guardada. | Q-10, Q-11 |
| E02-RN-03 | Toda chamada leva o identificador da nota, que o sistema acionado usa para descartar a repetição. | Q-11; RFC T-03 |
| E02-RN-04 | Na falha, o serviço tenta de novo automaticamente. A falha de um sistema não atrasa os outros nem chega à origem. | Q-11 |
| E02-RN-05 | Falha persistente: a nota é mantida, e a operação e o dono do sistema ficam sabendo no mesmo dia. | Q-11 |
| E02-RN-06 | É possível reprocessar só a etapa que falhou, sem emitir outra nota. | Q-11 |

### Exemplos
| # | Situação | Resultado | Regra |
|---|---|---|---|
| 1 | Pedido com 6 linhas de item ou mais | Resposta sem esperar os 5,35 s da entrega | E02-RN-01 |
| 2 | Entrega fora do ar | Origem recebe a nota; registro, estoque e financeiro concluem; entrega falha 5 vezes e gera alerta | E02-RN-04, E02-RN-05 |
| 3 | Serviço cai depois de guardar a nota, antes de acionar | Os quatro são acionados quando o serviço volta | E02-RN-02 |
| 4 | Serviço cai no meio da chamada à entrega | A entrega é chamada de novo com o mesmo identificador da nota | E02-RN-03 |
| 5 | Reprocessamento da entrega após falha persistente | Só a entrega é acionada; nenhuma nota nova | E02-RN-06 |
| 6 | Reenvio do mesmo pedido | Nenhum sistema acionado de novo | [E03-RN-02](../e-03-reenvio/spec.md) |

## Parte não funcional

| ID | Requisito | Origem |
|---|---|---|
| E02-NF-01 | p95 abaixo de 800 ms com 1 linha de item e com 6 ou mais, em carga local, cada cenário rodado duas vezes nas mesmas condições. As esperas simuladas não mudam, e o alerta de latência da F-04 deixa de disparar. | RFC O-02, R-02; F04-NF-11 |
| E02-NF-02 | Tabela `tarefas_integracao` gravada na mesma transação da nota, só com `id_pedido`, sistema e campos de controle, sem dado pessoal, e índice só das tarefas abertas, dividido em 10 fatias. | ADR-0013; Spike-0001; RFC 6.2 (LGPD) |
| E02-NF-03 | Streams e um Pipe por sistema levam só a criação da tarefa (registro sem versão anterior) daquele sistema a uma fila com DLQ por sistema. A mensagem tem só `id_pedido` e sistema, e o consumidor ignora campo desconhecido (convivência de versões no canary). O filtro e o formato da mensagem ficam versionados e são conferidos nos testes contra eventos reais do Streams do emulador, com a biblioteca da AWS que aplica os padrões do EventBridge: é uma aproximação, porque a AWS não declara que os Pipes usam essa biblioteca. O Pipe real é validado na fase 7. | ADR-0013; RFC 6.2, risco 9 |
| E02-NF-04 | Execução única por gravação condicional, com bloqueio de 60 s. A mensagem só é apagada quando a tarefa termina; quem não conseguiu pegar a tarefa só apaga a mensagem se ela já terminou. | Spike-0001 |
| E02-NF-05 | Nova tentativa a cada 2 minutos. Na 5ª falha, o serviço marca a tarefa como FALHOU e move a mensagem para a DLQ; o limite de 10 recebimentos da fila fica como rede de segurança. | ADR-0013; Spike-0001 |
| E02-NF-06 | Reconciliação a cada 5 minutos recoloca na fila as tarefas abertas há mais de 15 minutos. Com várias instâncias, cada uma roda a sua, e a mensagem duplicada é absorvida pela execução única (E02-NF-04). Os dois prazos são configuração; sem Pipe na execução local, o perfil `local` usa prazos curtos. | RFC 6.2; ADR-0013; Spike-0001 |
| E02-NF-07 | Virtual threads ligadas, sem bloqueio dentro de `synchronized`; pool HTTP do SDK e limite de chamadas simultâneas por integração explícitos; pinning verificado na carga. | ADR-0002; ADR-0012 |
| E02-NF-08 | Métricas por sistema: tarefas pendentes, concluídas e com falha, idade da tarefa pendente mais antiga e mensagens na DLQ. Alertas: mensagem na DLQ, tarefa pendente há mais de 15 minutos e SLO de conclusão (99% em até 5 minutos). Entrega fora do ar gera alerta (prova do O-06). | RFC 6.4, O-06; ADR-0013 |
| E02-NF-09 | Cada tarefa processada gera trace e log com `id_pedido` e sistema em campos próprios, sem dado pessoal; o span de cada integração (F04-NF-02) passa a esse trace. | ADR-0007 |
| E02-NF-10 | Queda do serviço no meio do processamento não perde acionamento, e o reenvio devolve a mesma nota sem acionar de novo. | RFC O-03 |
| E02-NF-11 | Procedimento documentado para reprocessar só a etapa que falhou. A DLQ guarda a mensagem por 14 dias, contados de quando o serviço a move para lá. | RFC 6.2 |
| E02-NF-12 | Filas e SDK só nos adaptadores; aplicação e domínio conhecem só as portas. | ADR-0003 |

### Dependências
| Item | Uso | Escopo |
|---|---|---|
| AWS SDK for Java 2.x, módulo SQS | Consumo e publicação nas filas (E02-NF-03, 05, 06) | Aplicação |
| Event Ruler (biblioteca da AWS que aplica os padrões de filtro do EventBridge) | Conferir o filtro do Pipe (E02-NF-03) | Só testes |
| Imagem `softwaremill/elasticmq-native` | Filas compatíveis com o SQS (E02-NF-03) | Testes e local |

### Lacunas aceitas
- E02-NF-01 e a verificação de pinning do E02-NF-07: o teste de carga não foi feito nesta fase, por decisão do time. Evidência parcial: a requisição não aciona nenhum sistema (6 linhas de item em 0,7 s na primeira chamada após a subida, antes cerca de 6,4 s) e não há `synchronized` no código.

### Fora da fase 5
Pipes, filas, DLQs e alarme de falha de execução do Pipe na AWS (depende de métrica do CloudWatch): fase 7 (Terraform).
