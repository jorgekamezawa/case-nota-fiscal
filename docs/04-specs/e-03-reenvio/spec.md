# Spec E-03: reenvio devolve a mesma nota

| | |
|---|---|
| **Épico** | [Nota fiscal confiável](../../02-produto/epico-nota-fiscal-confiavel.md) |
| **Fase** | 5 |
| **Status** | Em revisão |
| **Regras de negócio** | [Levantamento](../../01-levantamento/levantamento-regras-negocio.md) |
| **Decisão** | [ADR-0014](../../03-engenharia/adr/0014-reenvio-por-id-pedido-e-hash.md) |

## História
Como sistema de origem, quero que reenviar um pedido devolva a mesma nota, sem duplicar a nota nem os acionamentos, e que o mesmo número com outro conteúdo seja recusado.

## Parte funcional

| ID | Regra | Origem |
|---|---|---|
| E03-RN-01 | O `id_pedido` é obrigatório; se falta ou tem formato errado, a recusa segue a etapa 1 da validação, com os demais erros dela (E01-RN-08, E01-RN-09). Com o `id_pedido` legível, a nota é procurada antes de conferir o restante do pedido: se já existe nota com ele, valem E03-RN-02 e E03-RN-03, sem conferir o restante do pedido. Se não existe, o pedido segue a validação completa ([E-01](../e-01-nota-correta/spec.md)) e é emitido, inclusive quando o primeiro envio falhou antes de a nota ser guardada. | Q-09, Q-15 |
| E03-RN-02 | Com o mesmo conteúdo, o serviço devolve a nota guardada, idêntica à primeira resposta (mesmo identificador, data e valores), e não aciona de novo registro, estoque, entrega e financeiro. | Q-09 |
| E03-RN-03 | Com conteúdo diferente, o pedido é recusado por divergência. A recusa não repete dado do pedido nem da nota e se distingue da recusa por validação. | Q-09, Q-14 |
| E03-RN-04 | Mesmo conteúdo = mesmo valor em todo campo que o contrato conhece. Não contam: ordem dos campos, forma de escrever o número, campo nulo ou ausente, campo que o contrato não conhece. Contam: o texto como recebido (pontuação, espaços, maiúsculas), a ordem das listas e os campos que a nota não traz (ex.: `data`). | Q-14 |
| E03-RN-05 | Envios simultâneos do mesmo `id_pedido` geram uma única nota; cada envio recebe o resultado das regras acima. | Q-09 |
| E03-RN-06 | O reenvio vale enquanto a nota estiver guardada ([E04-RN-03](../e-04-guarda-das-notas/spec.md)). Depois do prazo, o mesmo `id_pedido` é tratado como pedido novo. | Q-12 |

### Exemplos
Pedido base do E-01, já emitido. Cada linha muda só o que está descrito no reenvio.

| # | Reenvio | Resultado | Regra |
|---|---|---|---|
| 1 | Idêntico | Mesma nota; nenhum sistema acionado | E03-RN-02 |
| 2 | Campos em outra ordem | Mesma nota | E03-RN-04 |
| 3 | `valor_unitario` `50` em vez de `50.00` | Mesma nota | E03-RN-04 |
| 4 | `regime_tributacao` nulo em vez de ausente | Mesma nota | E03-RN-04 |
| 5 | Campo `observacao` a mais | Mesma nota | E03-RN-04 |
| 6 | CPF `88740347095` em vez de `887.403.470-95` | Recusado por divergência | E03-RN-03, E03-RN-04 |
| 7 | Nome com um espaço a mais | Recusado por divergência | E03-RN-04 |
| 8 | Pedido de dois itens, reenviado com os itens em ordem trocada | Recusado por divergência | E03-RN-04 |
| 9 | `data` diferente | Recusado por divergência | E03-RN-04 |
| 10 | `valor_frete` -1,00 | Recusado por divergência, sem conferir o frete | E03-RN-01, E03-RN-03 |
| 11 | `id_pedido` como texto | Recusado: `id_pedido` em formato errado | E03-RN-01 |
| 12 | `id_pedido` ausente e `valor_frete` ausente | Recusado: `id_pedido` e frete obrigatórios, na mesma resposta | E03-RN-01 |
| 13 | `id_pedido` sem nota, com `valor_frete` -1,00 | Recusado: frete negativo (validação completa) | E03-RN-01 |
| 14 | Primeiro envio recusado como serviço indisponível; reenvio idêntico | Nota emitida normalmente | E03-RN-01 |
| 15 | Dois envios idênticos simultâneos | Uma nota; os dois recebem a mesma | E03-RN-05 |
| 16 | Dois envios simultâneos com conteúdos diferentes | Uma nota; um recebe a nota e o outro, recusa por divergência | E03-RN-05 |

## Parte não funcional

| ID | Requisito | Origem |
|---|---|---|
| E03-NF-01 | Duplicidade impedida por gravação condicional (só grava se o `id_pedido` não existir), inclusive entre envios simultâneos, com nova tentativa curta em conflito de transação. A nota existente é lida com leitura fortemente consistente. O conflito de transação e a leitura consistente, que o emulador não reproduz, são testados com simulação. | ADR-0014 |
| E03-NF-02 | Comparação por hash SHA-256 dos campos que o contrato conhece, sem os nulos, normalizados pela RFC 8785, exceto os números, escritos pelo valor decimal exato. Só o hash é guardado, nunca o pedido. Números iguais em valor geram o mesmo hash, e números diferentes, hashes diferentes, em qualquer quantidade de dígitos. | ADR-0014; Q-14 |
| E03-NF-03 | Divergência responde 422 no formato do [ADR-0009](../../03-engenharia/adr/0009-erros-no-formato-problem-details.md), com `type` novo em `docs/api/erros.md`. | RFC O-07 |
| E03-NF-04 | Reenvio devolvido não conta em `notas.emitidas`; a divergência conta em `recusas` pelo seu `type`. Reenvio devolvido e divergência geram log próprio, com `id_pedido` em campo próprio e sem dado pessoal. | F04-NF-04, F04-NF-07 |

### Dependências
Nenhuma: a normalização usa o Jackson, já presente.
