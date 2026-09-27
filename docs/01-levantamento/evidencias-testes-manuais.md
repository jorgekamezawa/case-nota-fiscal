# Testes manuais (comportamento original)

Como usar no Postman: **Import > Raw text**, cole um curl e clique em Import.
Reinicie a aplicação antes de começar: vários erros dependem do estado acumulado entre chamadas.
Resultados obtidos com o código original (Java 11, Spring Boot 2.6.2).

---

## 1. Itens acumulando entre chamadas
Envie a mesma requisição várias vezes. O pedido tem 1 item, mas a resposta traz 1, 2, 3... itens.
Causa: `CalculadoraAliquotaProduto` guarda os itens numa lista `static`, compartilhada por todas as requisições.

```bash
curl -X POST 'http://localhost:8080/api/pedido/gerarNotaFiscal' -H 'Content-Type: application/json' -d '{"id_pedido":1,"data":"2022-05-01","valor_total_itens":100.0,"valor_frete":10.0,"itens":[{"id_item":1,"descricao":"Teclado USB","valor_unitario":50,"quantidade":2}],"destinatario":{"nome":"John Doe","tipo_pessoa":"FISICA","documentos":[{"tipo":"CPF","numero":"88740347095"}],"enderecos":[{"logradouro":"Av do estado","numero":"5533","complemento":"4 anndar b","bairro":"Mooca","cidade":"São Paulo","estado":"SP","pais":"Brasil","cep":"03105003","finalidade":"ENTREGA","regiao":"SUDESTE"}]}}'
```

## 2. Tempo de resposta aumentando
Use o curl do teste 1 e observe o tempo no Postman. Da 1ª à 5ª chamada leva ~1,5s; da 6ª em diante, ~6,5s para sempre.
Causa: a entrega dorme 5s quando a nota tem mais de 5 itens, e a lista acumulada (teste 1) passa desse limite.
Medido: chamadas 1 a 5 = 1,49s; chamadas 6 e 7 = 6,49s.

## 3. Campos do endereço somem da resposta
Na resposta do teste 1, compare `destinatario.enderecos` com o que foi enviado: `bairro`, `cidade` e `pais` não voltam.
Causa: a classe `Endereco` não tem esses campos, e o Jackson descarta silenciosamente o que não conhece.

## 4. PJ Simples Nacional: tributo por unidade e frete sem arredondamento
8 monitores de 730 (total 5840, alíquota 19%). `valor_tributo_item` vem 138.7 (sobre 1 unidade, não sobre as 8 = 1109.6). O frete vem 75.456, com 3 casas.
Causa: tributo calculado sobre o valor unitário (Q-03 do levantamento) e valor monetário em `double`, sem escala nem arredondamento (Q-08).

```bash
curl -X POST 'http://localhost:8080/api/pedido/gerarNotaFiscal' -H 'Content-Type: application/json' -d '{"id_pedido":1,"data":"2022-05-01","valor_total_itens":5840.0,"valor_frete":72.0,"itens":[{"id_item":1,"descricao":"Monitor LCD SAMSUNG","valor_unitario":730,"quantidade":8}],"destinatario":{"nome":"John Doe","tipo_pessoa":"JURIDICA","regime_tributacao":"SIMPLES_NACIONAL","documentos":[{"tipo":"CNPJ","numero":"49695613000180"}],"enderecos":[{"logradouro":"Av do estado","numero":"5533","complemento":"4 anndar b","bairro":"Mooca","cidade":"São Paulo","estado":"SP","pais":"Brasil","cep":"03105003","finalidade":"ENTREGA","regiao":"SUDESTE"}]}}'
```

## 5. PJ com regime OUTROS: nota sem itens
Mesmo pedido do teste 4 com `regime_tributacao` = `OUTROS`. Responde 200 com `itens: []`: a nota sai sem nenhum item.
Causa: não existe regra para `OUTROS`, e o código segue com a lista vazia (Q-02 do levantamento).

```bash
curl -X POST 'http://localhost:8080/api/pedido/gerarNotaFiscal' -H 'Content-Type: application/json' -d '{"id_pedido":1,"data":"2022-05-01","valor_total_itens":5840.0,"valor_frete":72.0,"itens":[{"id_item":1,"descricao":"Monitor LCD SAMSUNG","valor_unitario":730,"quantidade":8}],"destinatario":{"nome":"John Doe","tipo_pessoa":"JURIDICA","regime_tributacao":"OUTROS","documentos":[{"tipo":"CNPJ","numero":"49695613000180"}],"enderecos":[{"logradouro":"Av do estado","numero":"5533","complemento":"4 anndar b","bairro":"Mooca","cidade":"São Paulo","estado":"SP","pais":"Brasil","cep":"03105003","finalidade":"ENTREGA","regiao":"SUDESTE"}]}}'
```

## 6. PJ sem regime de tributação: nota sem itens
PJ sem o campo `regime_tributacao`. Responde 200 com `itens: []`, mesmo erro do teste 5.
Causa: sem regime não há regra de alíquota, e o código segue com a lista vazia (Q-02 do levantamento).

```bash
curl -X POST 'http://localhost:8080/api/pedido/gerarNotaFiscal' -H 'Content-Type: application/json' -d '{"id_pedido":1,"data":"2022-05-01","valor_total_itens":5840.0,"valor_frete":72.0,"itens":[{"id_item":1,"descricao":"Monitor LCD SAMSUNG","valor_unitario":730,"quantidade":8}],"destinatario":{"nome":"John Doe","tipo_pessoa":"JURIDICA","documentos":[{"tipo":"CNPJ","numero":"49695613000180"}],"enderecos":[{"logradouro":"Av do estado","numero":"5533","complemento":"4 anndar b","bairro":"Mooca","cidade":"São Paulo","estado":"SP","pais":"Brasil","cep":"03105003","finalidade":"ENTREGA","regiao":"SUDESTE"}]}}'
```

## 7. Sem endereço de entrega: frete zerado
Único endereço com `finalidade` = `COBRANCA`. O frete enviado é 10, mas a resposta traz `valor_frete: 0.0`.
Causa: sem região de entrega o código não entra em nenhum `if` e o frete fica 0, sem erro nem aviso.

```bash
curl -X POST 'http://localhost:8080/api/pedido/gerarNotaFiscal' -H 'Content-Type: application/json' -d '{"id_pedido":1,"data":"2022-05-01","valor_total_itens":100.0,"valor_frete":10.0,"itens":[{"id_item":1,"descricao":"Teclado USB","valor_unitario":50,"quantidade":2}],"destinatario":{"nome":"John Doe","tipo_pessoa":"FISICA","documentos":[{"tipo":"CPF","numero":"88740347095"}],"enderecos":[{"logradouro":"Av do estado","numero":"5533","complemento":"4 anndar b","bairro":"Mooca","cidade":"São Paulo","estado":"SP","pais":"Brasil","cep":"03105003","finalidade":"COBRANCA","regiao":"SUDESTE"}]}}'
```

## 8. Valor total declarado diferente dos itens
O pedido declara `valor_total_itens: 100`, mas o item vale 5000. A resposta ecoa 100 e aplica alíquota 0% (faixa < 500) ao item de 5000.
Causa: a faixa da alíquota usa o total informado no payload, sem conferir com a soma dos itens.

```bash
curl -X POST 'http://localhost:8080/api/pedido/gerarNotaFiscal' -H 'Content-Type: application/json' -d '{"id_pedido":1,"data":"2022-05-01","valor_total_itens":100.0,"valor_frete":10.0,"itens":[{"id_item":1,"descricao":"Notebook","valor_unitario":5000,"quantidade":1}],"destinatario":{"nome":"John Doe","tipo_pessoa":"FISICA","documentos":[{"tipo":"CPF","numero":"88740347095"}],"enderecos":[{"logradouro":"Av do estado","numero":"5533","complemento":"4 anndar b","bairro":"Mooca","cidade":"São Paulo","estado":"SP","pais":"Brasil","cep":"03105003","finalidade":"ENTREGA","regiao":"SUDESTE"}]}}'
```

## 9. Precisão de valores monetários
Frete 33.33 para o NORDESTE (acréscimo de 8,5%). A resposta traz `valor_frete: 36.16305`, com 5 casas decimais.
Causa: `double` sem escala nem arredondamento (Q-08 do levantamento).

```bash
curl -X POST 'http://localhost:8080/api/pedido/gerarNotaFiscal' -H 'Content-Type: application/json' -d '{"id_pedido":1,"data":"2022-05-01","valor_total_itens":100.0,"valor_frete":33.33,"itens":[{"id_item":1,"descricao":"Teclado USB","valor_unitario":50,"quantidade":2}],"destinatario":{"nome":"John Doe","tipo_pessoa":"FISICA","documentos":[{"tipo":"CPF","numero":"88740347095"}],"enderecos":[{"logradouro":"Av do estado","numero":"5533","complemento":"4 anndar b","bairro":"Mooca","cidade":"São Paulo","estado":"SP","pais":"Brasil","cep":"03105003","finalidade":"ENTREGA","regiao":"NORDESTE"}]}}'
```

## 10. Sem destinatário: erro 500 genérico
Pedido sem `destinatario`. Responde 500 "Internal Server Error" (`NullPointerException` no log).
Causa: `NullPointerException` ao acessar o destinatário ausente; não há validação de entrada (Q-07 do levantamento).

```bash
curl -X POST 'http://localhost:8080/api/pedido/gerarNotaFiscal' -H 'Content-Type: application/json' -d '{"id_pedido":1,"data":"2022-05-01","valor_total_itens":100.0,"valor_frete":10.0,"itens":[{"id_item":1,"descricao":"Teclado USB","valor_unitario":50,"quantidade":2}]}'
```

## 11. Quantidade e valores negativos aceitos
Item com quantidade -5 e total -250. Responde 200 e gera a nota normalmente.
Causa: não há nenhuma validação de entrada.

```bash
curl -X POST 'http://localhost:8080/api/pedido/gerarNotaFiscal' -H 'Content-Type: application/json' -d '{"id_pedido":1,"data":"2022-05-01","valor_total_itens":-250.0,"valor_frete":10.0,"itens":[{"id_item":1,"descricao":"Teclado USB","valor_unitario":50,"quantidade":-5}],"destinatario":{"nome":"John Doe","tipo_pessoa":"FISICA","documentos":[{"tipo":"CPF","numero":"88740347095"}],"enderecos":[{"logradouro":"Av do estado","numero":"5533","complemento":"4 anndar b","bairro":"Mooca","cidade":"São Paulo","estado":"SP","pais":"Brasil","cep":"03105003","finalidade":"ENTREGA","regiao":"SUDESTE"}]}}'
```

## 12. Concorrência: itens perdidos e respostas misturadas
Dispare o teste 4 muitas vezes ao mesmo tempo (no Postman: aba **Performance** da collection, com vários usuários virtuais).
Medido: 50 chamadas simultâneas com a lista já com 14 itens perderam 11 inclusões (22%); com a aplicação recém-reiniciada, 50 chamadas perderam 1 (2%) e 150 perderam 5 e 10 (3% a 7%). A perda varia com a carga, e em todas as rodadas as respostas vieram idênticas.
Causa: `ArrayList` compartilhada e não thread-safe. Um cliente recebe os itens de outro, o que também é vazamento de dados.
