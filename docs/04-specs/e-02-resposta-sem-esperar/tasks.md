# Tasks E-02: resposta sem esperar os sistemas acionados

Spec: [spec.md](spec.md). Base `br.com.itau.geradornotafiscal` (abreviado `p`). Cada task termina com `./mvnw -B clean verify` verde, sem aviso novo, e a saída no PR. Nenhuma linha com `Thread.sleep` muda. Mudam por requisito, sem enfraquecer: `GerarNotaFiscalUseCaseImplTest` (o caso de uso deixa de chamar as integrações), `TracesEMetricasTest` (o span de cada integração passa ao trace da tarefa) e, em `GerarNotaFiscalUseCaseImplTest`, os testes do E01-NF-05 e do exemplo de cálculo 29, que usavam as portas das integrações dentro da requisição: passam a provar as mesmas regras pela nota guardada.

## Back

### T-05. Tarefas na mesma transação da nota
- **Cobre:** E02-RN-01, E02-RN-02, E02-NF-02.
- **Classes:**
  - `p.domain.valueobject.Sistema`: REGISTRO, ESTOQUE, ENTREGA, FINANCEIRO;
  - `p.domain.entity.TarefaIntegracao`: fábrica `criarPendente` e as transições pegar, concluir e falhar, com a regra das 5 tentativas (E02-NF-05);
  - `p.application.usecase.GerarNotaFiscalUseCaseImpl`: deixa de chamar as quatro portas e grava a nota com as quatro tarefas pela `NotaFiscalPersistenciaPort`;
  - `p.adapter.out.dynamodb.NotaFiscalDynamoAdapter`: uma transação com a nota e as tarefas, com o mesmo `expira_em` da nota; índice `pendentes` com fatias. Nota vencida substituída (E03-RN-06) regrava as tarefas como alteração, não criação, e o Pipe não as publica: a reconciliação as recoloca na fila.
- **Testes:** `TarefaIntegracaoTest`; transação grava as cinco linhas ou nenhuma; resposta sem esperar as integrações (exemplo 1).

### T-06. Consumo das filas
- **Cobre:** E02-RN-03 a E02-RN-05, E02-NF-04, E02-NF-05, E02-NF-07, E02-NF-09, E02-NF-12.
- **Dependência:** AWS SDK 2.x (SQS); de teste, a imagem `softwaremill/elasticmq-native` com tag fixa.
- **Classes:**
  - `p.adapter.in.fila.ConsumidorDeTarefas`: uma leitura contínua por fila, em virtual threads, ligada por propriedade (desligada nos testes que não consomem filas, para contextos do Spring em cache não disputarem as mensagens); apaga, mantém ou move a mensagem para a DLQ conforme o resultado;
  - `p.adapter.in.fila.dto.MensagemTarefa`: `id_pedido` (chega como texto, como o Pipe o extrai do evento) e sistema, ignorando campo desconhecido;
  - `p.application.port.in.ExecutarTarefaUseCase` e `p.application.usecase.ExecutarTarefaUseCaseImpl`: pega a tarefa, lê a nota, chama a porta do sistema e devolve `p.application.port.in.result.TarefaExecutada`, com o `ResultadoTarefa` (concluída, já terminada, nova tentativa, em execução por outro ou falhou), que define se a mensagem é apagada, mantida ou movida para a DLQ;
  - `p.adapter.in.fila.ProcessadorDeTarefa`: trace, log e métricas de uma tarefa, usado pelo consumo e pelos testes de telemetria;
  - `p.config.FilasDeTarefas`: nome e endereço da fila e da DLQ de cada sistema;
  - `p.application.port.out.TarefaIntegracaoPort` e `p.adapter.out.dynamodb.TarefaIntegracaoDynamoAdapter`: gravações condicionais da tarefa;
  - `p.config.SqsConfig`: cliente e URLs das filas por configuração;
  - `application.properties`: virtual threads ligadas; pool HTTP do SDK (`apache5-client`, T-01 do E-04) e limite de chamadas simultâneas por integração explícitos;
  - `p.ArquiteturaTest`: aplicação e domínio não dependem de `software.amazon..`.
- **Testes:** exemplos 2 e 4; mensagem repetida executa uma vez só; bloqueio vencido libera a tarefa; 5ª falha move para a DLQ; mesmo identificador da nota em toda repetição; trace e log da tarefa sem dado pessoal.

### T-07. Transporte local e reconciliação
- **Cobre:** E02-RN-02, E02-NF-03, E02-NF-06, E02-NF-10.
- **Dependência:** de teste, Event Ruler.
- **Classes e arquivos:**
  - `infra/pipes/filtro-tarefas.json` e `infra/pipes/mensagem-tarefa.json`: filtro (sistema da tarefa e ausência de versão anterior, com o Streams gravando as versões nova e anterior) e formato da mensagem, a serem reusados pelo Terraform na fase 7;
  - `PipeDeTeste`, só em `src/test`: lê o Streams do emulador, aplica o filtro versionado com o Event Ruler e publica a mensagem na fila do sistema;
  - `p.adapter.in.agendador.ReconciliacaoAgendada`: nos prazos da configuração (5 e 15 minutos; curtos no perfil `local`), chama `p.application.port.in.ReconciliarTarefasUseCase` (`p.application.usecase.ReconciliarTarefasUseCaseImpl`);
  - `p.application.port.out.FilaTarefasPort` e `p.adapter.out.sqs.FilaTarefasSqsAdapter`: publica a mensagem.
- **Testes:** filtro aceita a criação da tarefa do sistema e recusa alteração, remoção e outro sistema, com eventos reais do emulador; caminho completo da nota à fila pelo `PipeDeTeste`; exemplo 3 (tarefa sem mensagem é recolocada pela reconciliação); queda com a tarefa em execução (E02-NF-10).

### T-08. Métricas, alertas e procedimento de reprocessamento
- **Cobre:** E02-RN-05, E02-RN-06, E02-NF-08, E02-NF-11, E04-NF-04.
- **Classes e arquivos:**
  - `ProcessadorDeTarefa`: contador de tarefas por sistema e resultado e tempo da emissão à conclusão;
  - `p.adapter.in.agendador.MedicaoDasTarefas` e `p.application.port.in.ConsultarSituacaoDasTarefasUseCase`: medidores de tarefas pendentes, da idade da mais antiga e de mensagens na DLQ, por sistema (a contagem da DLQ vem do `FilaTarefasSqsAdapter`); como várias instâncias medem o mesmo valor, os alertas agregam por máximo, não por soma;
  - `p.adapter.out.entrega.EntregaAdapter`: propriedade válida só no perfil `local` que faz a entrega falhar (prova do O-06);
  - `observabilidade/grafana/alertas/integracoes.json`: DLQ, tarefa pendente há mais de 15 minutos, SLO de conclusão e banco indisponível;
  - `observabilidade/grafana/dashboards/gerador-nota-fiscal.json`: painéis das integrações;
  - `docs/03-engenharia/runbooks/reprocessamento.md`: exemplo 5.
- **Testes:** métricas presentes com rótulo `sistema`, sem `id_pedido`; exemplo 5 seguindo o runbook contra os emuladores.

## Infra

### T-09. Ambiente local
- **Cobre:** E02-NF-03, E04-NF-05.
- **Arquivos e classes:**
  - `compose.yaml`: DynamoDB Local e ElasticMQ, com tag fixa;
  - `p.config.local.InfraLocal`: cria tabelas, índice, Streams com versões nova e anterior, filas e DLQs (retenção de 14 dias) no perfil `local` e nos testes;
  - `application-local.properties`: endpoints dos emuladores;
  - `CLAUDE.md`: como subir o ambiente local.

## QA

### T-10. Rastreabilidade e evidência da fase
Um agente de contexto limpo confere a evidência de cada regra e requisito do E-02, do E-03 e do E-04, e que nenhuma linha com `Thread.sleep` mudou. Evidência local:
- **O-02:** teste de carga não feito nesta fase, por decisão do time; lacuna aceita na spec;
- **O-03:** aplicação encerrada à força durante o processamento, e as tarefas concluem depois de ela voltar; reenvio devolve a mesma nota sem acionar;
- **O-06:** entrega fora do ar gera o alerta no Grafana local.

Ao final, as specs vão para "Concluída", o README atualiza "Estado atual" e "O que ainda falta", e tudo que subiu é desligado. Pronto quando não há lacuna, ou quando cada lacuna tem justificativa aceita.
