# Erros da API

Toda resposta de erro de `POST /api/pedido/gerarNotaFiscal` segue o Problem Details ([RFC 9457](https://www.rfc-editor.org/rfc/rfc9457), [ADR-0009](../03-engenharia/adr/0009-erros-no-formato-problem-details.md)), com `Content-Type: application/problem+json`. O consumidor trata o erro pelo `type`, que é estável; `title` e `detail` são texto para pessoas e podem mudar.

## Formato

| Campo | Descrição |
|---|---|
| `type` | Tipo do erro (tabela abaixo). |
| `title` | Resumo do tipo. |
| `status` | Código HTTP. |
| `detail` | Explicação desta ocorrência. |
| `campos` | Só no `pedido-invalido`: todos os campos recusados na etapa em que o pedido parou, cada um com `campo` (caminho, ex.: `itens[0].quantidade`), `type` (motivo) e `detail`. A etapa 1 confere preenchimento e formato, com os erros de tipo (texto no lugar de número, valor fora da lista, data inválida) um por vez, antes dos demais; a etapa 2, as regras de negócio, só quando a etapa 1 passa. |

Nenhuma resposta de erro traz dado pessoal (nome, documento, endereço) nem o valor recebido; a exceção são os totais do `total-divergente`. A ordem dos itens de `campos` não é garantida: o consumidor identifica cada um pelo `campo`.

```json
{
  "type": "/erros/pedido-invalido",
  "title": "Pedido inválido",
  "status": 400,
  "detail": "O pedido tem 2 campo(s) inválido(s).",
  "campos": [
    { "campo": "valor_frete", "type": "/erros/frete-negativo", "detail": "Valor do frete não pode ser negativo." },
    { "campo": "itens[0].quantidade", "type": "/erros/quantidade-invalida", "detail": "Quantidade deve ser inteira e maior que zero." }
  ]
}
```

## Tipos da resposta

| `type` | Status | Quando |
|---|---|---|
| `/erros/pedido-invalido` | 400 | O pedido tem um ou mais campos inválidos, listados em `campos`. |
| `/erros/json-invalido` | 400 | O corpo não é JSON válido ou não é um objeto. |
| `/erros/pedido-grande-demais` | 400 | A nota do pedido passa do tamanho que o serviço consegue guardar; sem `campos` ([E04-RN-04](../04-specs/e-04-guarda-das-notas/spec.md)). Acima de 800 linhas de item, a recusa é o `pedido-invalido` com o motivo `itens-acima-do-maximo`. |
| `/erros/pedido-divergente` | 422 | Já existe nota para o `id_pedido`, emitida para um pedido com outro conteúdo; sem `campos` e sem dado do pedido nem da nota ([E03-RN-03](../04-specs/e-03-reenvio/spec.md)). O reenvio com o mesmo conteúdo recebe 200 com a nota original. |
| `/erros/erro-interno` | 500 | Erro inesperado; sem detalhe interno. |
| `/erros/servico-indisponivel` | 503 | Não foi possível guardar a nota; nada foi gravado, e o pedido pode ser reenviado ([E04-RN-01](../04-specs/e-04-guarda-das-notas/spec.md)). |

## Motivos por campo

Usados em `campos[].type`. Regras nas specs [E-01](../04-specs/e-01-nota-correta/spec.md) e [E-04](../04-specs/e-04-guarda-das-notas/spec.md).

| `type` | Quando | Regra |
|---|---|---|
| `/erros/campo-obrigatorio` | Campo ausente ou nulo (inclusive `id_pedido`), lista vazia, ou endereço de entrega sem região. | E01-RN-01, E01-RN-06, E01-RN-10, E03-RN-01 |
| `/erros/formato-invalido` | Tipo de valor errado (ex.: número enviado como texto, `id_pedido` com decimal, objeto em campo de texto) ou data inválida. | E01-RN-08 |
| `/erros/casas-decimais-excedidas` | Valor monetário com mais de 2 casas decimais. | E01-RN-08 |
| `/erros/valor-nao-aceito` | Valor fora da lista aceita; o `detail` traz os aceitos. | E01-RN-08 |
| `/erros/documento-invalido` | CPF ou CNPJ inválido; o `detail` informa o tipo. | E01-RN-02 |
| `/erros/documento-do-tipo-ausente` | Falta documento do tipo coerente com o tipo de pessoa (CPF para PF, CNPJ para PJ). | E01-RN-02 |
| `/erros/regime-nao-atendido` | Pessoa jurídica com regime `OUTROS`. | E01-RN-03 |
| `/erros/regime-nao-se-aplica` | Pessoa física com regime informado. | E01-RN-03 |
| `/erros/quantidade-invalida` | Quantidade não inteira ou não maior que zero. | E01-RN-04 |
| `/erros/valor-unitario-invalido` | Valor unitário não maior que zero. | E01-RN-04 |
| `/erros/frete-negativo` | `valor_frete` negativo. | E01-RN-05 |
| `/erros/sem-endereco-de-entrega` | Nenhum endereço com finalidade `ENTREGA` ou `COBRANCA_ENTREGA`. | E01-RN-06 |
| `/erros/total-divergente` | `valor_total_itens` diferente da soma dos itens; o `detail` traz o declarado e o calculado. | E01-RN-07 |
| `/erros/itens-acima-do-maximo` | Mais de 800 linhas de item, o máximo por nota. | E04-RN-04 |
