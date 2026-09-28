# Pipes das tarefas de integração

Um Pipe por sistema (REGISTRO, ESTOQUE, ENTREGA, FINANCEIRO) leva cada tarefa criada na tabela `tarefas_integracao` à fila do sistema ([ADR-0013](../../docs/03-engenharia/adr/0013-acionamento-por-outbox-com-streams-e-sqs.md), E02-NF-03).

- `filtro-tarefas.json`: padrão de filtro do Pipe, com `${sistema}` trocado pelo sistema de cada Pipe. Aceita só a criação da tarefa: registro do sistema, sem versão anterior. O tipo do evento (`eventName`) não pode ser usado no filtro dos Pipes, por isso a criação é reconhecida pela falta de `OldImage`, que exige o Streams com as versões nova e anterior (`NEW_AND_OLD_IMAGES`).
- `mensagem-tarefa.json`: transformador de entrada do Pipe; a mensagem leva só `id_pedido` (como texto) e o sistema, sem dado pessoal.

Os dois arquivos são conferidos nos testes contra eventos reais do Streams do DynamoDB Local (`FiltroDoPipeTest`) e reusados pelo Terraform na fase 7, quando o Pipe real é validado na AWS.
