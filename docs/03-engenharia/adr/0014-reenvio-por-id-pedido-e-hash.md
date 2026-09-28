---
status: aceito
---
# ADR-0014: Reconhecer o reenvio pelo id_pedido e por um hash do conteúdo

**Decisão:** reconhecer o reenvio pela chave `id_pedido`, com gravação condicional, e confirmar que é o mesmo pedido comparando um **hash SHA-256 do corpo normalizado pela RFC 8785**, porque isso impede nota duplicada sem mudar o contrato de entrada e sem guardar uma segunda cópia do pedido com dados pessoais.

## Contexto
Hoje cada envio gera uma nota nova e aciona os sistemas de novo. Pelas decisões do [levantamento](../../01-levantamento/levantamento-regras-negocio.md):
- o reenvio devolve a mesma nota, com o mesmo identificador e a mesma data, sem acionar os sistemas de novo (Q-09);
- o mesmo `id_pedido` com conteúdo diferente é recusado (Q-09);
- o `id_pedido` é único no geral e nunca reutilizado; o reenvio vale enquanto a nota estiver guardada, 5 anos (Q-12, Q-13).

O contrato de entrada não pode mudar (R-01). As notas ficam no DynamoDB ([ADR-0012](0012-persistencia-em-dynamodb.md)); o desenho das tabelas está no [Spike-0001](../spikes/0001-persistencia-e-acionamento-das-integracoes.md).

## Alternativas descartadas
- **Cabeçalho `Idempotency-Key` enviado pelo consumidor:** é o padrão de mercado para APIs de pagamento, mas obriga todos os consumidores a mudar a chamada, o que quebra o contrato atual (R-01). O `id_pedido` já cumpre esse papel.
- **Só o `id_pedido`, sem comparar o conteúdo:** devolveria a nota antiga para um pedido diferente com o mesmo número, em silêncio, contrariando a Q-09.
- **Guardar o pedido inteiro e comparar campo a campo:** duplica nome, documento e endereço do destinatário por 5 anos, sem ganho sobre o hash, e ainda exige normalizar o formato antes de comparar.

## Consequências
- **Ganhos:**
  - nenhuma nota nem acionamento duplicado, inclusive com dois envios simultâneos, porque só uma gravação condicional vence;
  - pedido diferente com o mesmo número é recusado de forma visível;
  - contrato de entrada inalterado;
  - 64 caracteres por nota, sem dado pessoal a mais.
- **Custos:**
  - qualquer diferença num campo que o contrato conhece, mesmo que não afete o cálculo, faz o reenvio ser recusado;
  - depois do expurgo de 5 anos, o mesmo `id_pedido` geraria nota nova (aceito pela Q-12);
  - mudar a regra de normalização torna diferentes os hashes das notas já gravadas;
  - normalização própria, porque a RFC 8785 escreve números em ponto flutuante e perderia precisão.
- **Passa a ser obrigatório:**
  - hash calculado sobre os campos do corpo que o contrato conhece, sem os nulos (Q-14 do levantamento), normalizado pela RFC 8785 (JSON Canonicalization Scheme: campos ordenados pelo código dos caracteres, sem espaços), exceto os números, escritos pelo valor decimal exato, sem zeros à direita: a RFC os converte em ponto flutuante, e valores com mais de cerca de 15 dígitos significativos colidiriam;
  - conflito de transação em envios simultâneos tratado com nova tentativa curta, antes de ler a nota existente;
  - recusa com 422 e corpo no formato do [ADR-0009](0009-erros-no-formato-problem-details.md);
  - leitura fortemente consistente (sempre o dado mais recente) ao buscar a nota existente;
  - resposta do reenvio idêntica à original;
  - testes para campos em outra ordem, `730` e `730.00`, e envios simultâneos.
