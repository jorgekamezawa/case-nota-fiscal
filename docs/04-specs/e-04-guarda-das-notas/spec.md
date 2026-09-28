# Spec E-04: guarda das notas por 5 anos

| | |
|---|---|
| **Épico** | [Nota fiscal confiável](../../02-produto/epico-nota-fiscal-confiavel.md) |
| **Fase** | 5 |
| **Status** | Concluída |
| **Regras de negócio** | [Levantamento](../../01-levantamento/levantamento-regras-negocio.md) |
| **Decisão** | [ADR-0012](../../03-engenharia/adr/0012-persistencia-em-dynamodb.md), [Spike-0001](../../03-engenharia/spikes/0001-persistencia-e-acionamento-das-integracoes.md) |

## História
Como área fiscal, quero cada nota emitida guardada pelo prazo legal e apagada depois, para cumprir a obrigação sem reter dado pessoal além do necessário.

## Parte funcional

| ID | Regra | Origem |
|---|---|---|
| E04-RN-01 | A nota só é devolvida depois de guardada. Se não puder ser guardada, nada é gravado e o pedido é recusado como serviço indisponível. | Q-10, Q-16; RFC O-07 |
| E04-RN-02 | A nota é guardada com tudo o que a resposta devolve, para o reenvio devolvê-la igual ([E-03](../e-03-reenvio/spec.md)). Nenhuma cópia do pedido é guardada. | Q-09, Q-13 |
| E04-RN-03 | A nota é guardada por 5 anos contados de 1º de janeiro do ano seguinte à emissão, no fuso de São Paulo, e apagada depois. | Q-13 |
| E04-RN-04 | Pedido com mais de 800 linhas de item é recusado, informando o máximo. Pedido que, mesmo abaixo disso, não couber no armazenamento é recusado com motivo claro, nunca como erro interno. Nenhuma das recusas repete dado pessoal. | Q-16 |

### Exemplos
| # | Situação | Resultado | Regra |
|---|---|---|---|
| 1 | Nota emitida em 15/03/2026 | Apagada a partir de 01/01/2032 | E04-RN-03 |
| 2 | Nota emitida em 31/12/2026, 23:59 | Apagada a partir de 01/01/2032 | E04-RN-03 |
| 3 | Nota emitida em 01/01/2027, 00:00 | Apagada a partir de 01/01/2033 | E04-RN-03 |
| 4 | Armazenamento indisponível | Recusa de serviço indisponível; nenhuma nota guardada | E04-RN-01 |
| 5 | Pedido com 800 linhas de item | Aceito | E04-RN-04 |
| 6 | Pedido com 801 linhas de item | Recusado: acima do máximo de 800 linhas | E04-RN-04 |

### Fora deste entregável
- Identificação de quem emitiu a nota (`client_id`): fase 6.
- Exportação para auditoria: sob demanda, fora da fase 5.

## Parte não funcional

| ID | Requisito | Origem |
|---|---|---|
| E04-NF-01 | Tabela `notas` com chave `id_pedido` e a data de expurgo no formato do expurgo automático do banco (segundos desde 1970, UTC). | ADR-0012; Spike-0001 |
| E04-NF-02 | Armazenamento indisponível responde 503 no formato do [ADR-0009](../../03-engenharia/adr/0009-erros-no-formato-problem-details.md), sem detalhe interno. Mais de 800 linhas é motivo por campo (`itens`) do `pedido-invalido`, na etapa 2 da validação; nota que não cabe responde 400 com `type` próprio, sem campos. Os `type` novos entram em `docs/api/erros.md`. A prontidão continua sem depender do banco. | RFC O-07, 6.4 |
| E04-NF-03 | Banco só no adaptador; aplicação e domínio conhecem só a porta. | ADR-0003 |
| E04-NF-04 | Métrica e alerta de indisponibilidade do banco. | RFC 6.4; F-04 (fora do entregável) |
| E04-NF-05 | Testes contra o emulador oficial do banco, em container. | ADR-0012 |
| E04-NF-06 | O tamanho da nota é conferido antes de gravar. Medido com os textos no tamanho máximo dos campos equivalentes do leiaute da NF-e (o contrato não limita os textos): 990 linhas cabem com texto sem acento (73% do limite), mas não só com acentos (117%, cerca de 846 linhas cabem); daí o máximo de 800 da E04-RN-04. Se o limite ou os tamanhos mudarem, mede de novo. | Q-16; RFC risco 4 |

### Dependências
| Item | Uso | Escopo |
|---|---|---|
| AWS SDK for Java 2.x, módulo DynamoDB | Acesso ao banco (E04-NF-01) | Aplicação |
| AWS SDK for Java 2.x, cliente HTTP `apache5-client` (no lugar do `apache-client` padrão) | Conexões do SDK compatíveis com virtual threads ([ADR-0002](../../03-engenharia/adr/0002-virtual-threads-para-esperas-de-io.md)) | Aplicação |
| Testcontainers 2.x (`testcontainers-junit-jupiter`) | Emulador em container nos testes (E04-NF-05) | Só testes |
| Imagem `amazon/dynamodb-local` | Emulador oficial do DynamoDB (E04-NF-05) | Testes e local |

### Fora da fase 5
Chave KMS própria, backup contínuo, ativação do expurgo automático e papel IAM na AWS: fase 7 (Terraform).
