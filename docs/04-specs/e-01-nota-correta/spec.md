# Spec E-01: nota correta ou recusa clara

| | |
|---|---|
| **Épico** | [Nota fiscal confiável](../../02-produto/epico-nota-fiscal-confiavel.md) |
| **Fase** | 1 |
| **Status** | Em revisão |
| **Regras de negócio** | [Levantamento](../../01-levantamento/levantamento-regras-negocio.md) |

## História
Como sistema de origem, quero receber a nota com tributo, frete e totais corretos, só com os itens do meu pedido, ou uma recusa com todos os motivos de uma vez, para nunca repassar uma nota errada.

## Parte funcional
"Definido nesta spec" = ponto que o levantamento não decidia, decidido pelo PO ao escrever esta spec.

### Validação
| ID | Regra | Origem |
|---|---|---|
| E01-RN-01 | São obrigatórios: destinatário, tipo de pessoa, regime (quando PJ), ao menos 1 documento, ao menos 1 item, ao menos 1 endereço, `valor_total_itens` e `valor_frete`. Cada documento tem tipo e número; cada item, quantidade e valor unitário; cada endereço, finalidade. | Q-02, Q-07; `valor_frete` e subcampos: definido nesta spec |
| E01-RN-02 | Em cada documento, os caracteres que não são dígitos, inclusive espaços, são desconsiderados. Depois disso, o CPF tem 11 dígitos e o CNPJ 14, com o dígito verificador conferido e sem todos os dígitos iguais (ex.: `111.111.111-11`); documento que falha é recusado como inválido, informando o tipo (ex.: "CPF inválido"). O destinatário tem ao menos um documento do tipo coerente com o tipo de pessoa: CPF para PF, CNPJ para PJ. | Q-07; limpeza, dígitos iguais e vários documentos: definido nesta spec |
| E01-RN-03 | PJ com regime `OUTROS` é recusada, informando que o regime não é atendido. PF não tem regime: o campo vem ausente ou nulo, e qualquer valor é recusado, informando que o regime não se aplica a pessoa física. | Q-02; PF: definido nesta spec |
| E01-RN-04 | Cada item tem quantidade inteira maior que zero (2,7 é recusado, não truncado) e valor unitário maior que zero. Item válido é o que atende esta regra e a E01-RN-08. | Q-07; valor unitário negativo: definido nesta spec |
| E01-RN-05 | `valor_frete` não pode ser negativo. Zero é aceito, e o valor é aceito como informado, respeitado o formato (E01-RN-08). | Q-07; precisão (E01-RN-08): definido nesta spec |
| E01-RN-06 | O destinatário tem ao menos um endereço com finalidade `ENTREGA` ou `COBRANCA_ENTREGA`. O primeiro deles é o endereço de entrega e precisa ter região. | Q-05, Q-06; presença da região só no escolhido (o valor é conferido em todos pela E01-RN-08): definido nesta spec |
| E01-RN-07 | `valor_total_itens` é igual à soma de valor unitário × quantidade dos itens, comparada exatamente, em centavos, sem arredondamento, porque os valores têm no máximo 2 casas (E01-RN-08) e a quantidade é inteira. A recusa informa o total declarado e o calculado. A conferência só é feita quando há ao menos 1 item e todos são válidos. | Q-01, Q-08; itens inválidos: definido nesta spec |
| E01-RN-08 | Todo campo presente, mesmo que nenhuma regra o use, é recusado se tiver valor fora da lista aceita ou formato errado, informando o campo. Formato errado: campo numérico (`id_pedido`, `quantidade`, `valor_unitario`, `valor_frete`, `valor_total_itens`) com texto, inclusive número enviado como texto (ex.: `"10"`); valor monetário com mais de 2 casas decimais; `data` que não é uma data válida. O formato é conferido primeiro: campo com formato errado recebe só esse motivo. Aceitos (valores do contrato atual): tipo de pessoa `FISICA`, `JURIDICA`; regime `SIMPLES_NACIONAL`, `LUCRO_REAL`, `LUCRO_PRESUMIDO`, `OUTROS` (este recusado pela E01-RN-03); documento `CPF`, `CNPJ`; finalidade `ENTREGA`, `COBRANCA_ENTREGA`, `COBRANCA`, `OUTROS`; região `NORTE`, `NORDESTE`, `CENTRO_OESTE`, `SUDESTE`, `SUL`. | Q-07; definido nesta spec |
| E01-RN-09 | Pedido recusado não gera nota. A recusa lista todos os campos inválidos de uma vez, cada um com o motivo, e não repete dado pessoal (nome, documento, endereço). | Q-07 |
| E01-RN-10 | Quando um campo obrigatório falta, a recusa não lista os campos que dependem dele: sem destinatário, nada dentro dele é conferido (tipo de pessoa, regime, documentos, endereços); sem tipo de pessoa, a coerência do documento com o tipo de pessoa e o regime não são conferidos, mas o dígito verificador de cada documento é. Os demais campos continuam conferidos (E01-RN-09). Campo com valor nulo conta como ausente. Campo que o contrato não conhece é ignorado, sem recusa. | Definido nesta spec |

#### Exemplos de validação
Pedido base: PF, CPF `887.403.470-95` (válido), 1 item de 50,00 × 2, `valor_total_itens` 100,00, `valor_frete` 10,00, um endereço `ENTREGA` na região `SUDESTE`. Cada linha muda só o que está descrito.

| # | Mudança no pedido base | Resultado | Regra |
|---|---|---|---|
| 1 | Nenhuma | Aceito | todas |
| 2 | `valor_frete` 0,00 | Aceito | E01-RN-05 |
| 3 | `valor_frete` ausente | Recusado: frete obrigatório | E01-RN-01 |
| 4 | `valor_frete` -1,00 | Recusado: frete negativo | E01-RN-05 |
| 5 | `valor_frete` 10,555 | Recusado: frete com mais de 2 casas | E01-RN-08 |
| 6 | Item de 5.000,00 × 1, total declarado 100,00 | Recusado: total declarado 100,00, calculado 5.000,00 | E01-RN-07 |
| 7 | Quantidade 2,7 | Recusado: quantidade inteira maior que zero; total não conferido | E01-RN-04, E01-RN-07 |
| 8 | Valor unitário 0,00 | Recusado: valor unitário maior que zero; total não conferido | E01-RN-04, E01-RN-07 |
| 9 | Valor unitário 10,005 | Recusado: valor unitário com mais de 2 casas; total não conferido | E01-RN-08, E01-RN-07 |
| 10 | PJ com CNPJ `49.695.613/0001-80` no lugar do CPF, sem regime | Recusado: regime obrigatório para PJ | E01-RN-01 |
| 11 | PJ com CNPJ `49.695.613/0001-80` no lugar do CPF e regime `OUTROS` | Recusado: regime não atendido | E01-RN-03 |
| 12 | PF com regime `LUCRO_REAL` | Recusado: regime não se aplica a pessoa física | E01-RN-03 |
| 13 | PF com regime `MEI` | Recusado: regime fora dos valores aceitos (formato conferido primeiro) | E01-RN-08 |
| 14 | CNPJ válido no lugar do CPF, mantendo PF | Recusado: falta documento do tipo CPF | E01-RN-02 |
| 15 | CPF `887.403.470-96` | Recusado: CPF inválido | E01-RN-02 |
| 16 | CPF `887.403.470-9` (10 dígitos) | Recusado: CPF inválido | E01-RN-02 |
| 17 | Único endereço com finalidade `COBRANCA` | Recusado: sem endereço de entrega | E01-RN-06 |
| 18 | Primeiro endereço `ENTREGA` sem região; segundo `COBRANCA_ENTREGA` com região | Recusado: endereço de entrega sem região | E01-RN-06 |
| 19 | Segundo endereço, `COBRANCA`, com região `LESTE` | Recusado: região fora dos valores aceitos | E01-RN-08 |
| 20 | Tipo de pessoa `ESTRANGEIRA` | Recusado: tipo de pessoa fora dos valores aceitos | E01-RN-08 |
| 21 | Destinatário ausente | Recusado: destinatário obrigatório; documento, regime e endereço não conferidos | E01-RN-10 |
| 22 | Quantidade -1 e `valor_frete` -5,00 | Recusado, com os dois campos na mesma resposta, sem nome, documento ou endereço; total não conferido | E01-RN-04, E01-RN-05, E01-RN-07, E01-RN-09 |
| 23 | Destinatário ausente e `valor_frete` -5,00 | Recusado: destinatário obrigatório e frete negativo, na mesma resposta | E01-RN-09, E01-RN-10 |
| 24 | `valor_frete` -1,555 | Recusado só por frete com mais de 2 casas | E01-RN-08 |
| 25 | Quantidade `"2"` (texto) | Recusado: quantidade em formato errado; total não conferido | E01-RN-08, E01-RN-07 |
| 26 | Item sem quantidade | Recusado: quantidade obrigatória; total não conferido | E01-RN-01, E01-RN-07 |
| 27 | Lista de itens vazia | Recusado: ao menos 1 item; total não conferido | E01-RN-01, E01-RN-07 |
| 28 | `data` `2022-13-45` | Recusado: data inválida | E01-RN-08 |
| 29 | Tipo de pessoa ausente | Recusado: tipo de pessoa obrigatório; coerência do documento e regime não conferidos | E01-RN-01, E01-RN-10 |
| 30 | PJ com regime `SIMPLES_NACIONAL`, mantendo só o CPF | Recusado: falta documento do tipo CNPJ | E01-RN-02 |
| 31 | PJ com CNPJ `49.695.613/0001-81` no lugar do CPF e regime `SIMPLES_NACIONAL` | Recusado: CNPJ inválido | E01-RN-02 |
| 32 | CPF `887 403 470 95` (com espaços) | Aceito | E01-RN-02 |
| 33 | CPF `111.111.111-11` | Recusado: CPF inválido | E01-RN-02 |
| 34 | Quantidade 0 | Recusado: quantidade inteira maior que zero; total não conferido | E01-RN-04, E01-RN-07 |
| 35 | Valor unitário -50,00 | Recusado: valor unitário maior que zero; total não conferido | E01-RN-04, E01-RN-07 |
| 36 | `valor_frete` nulo | Recusado: frete obrigatório | E01-RN-01, E01-RN-10 |
| 37 | Campo `observacao` no pedido | Aceito; campo ignorado | E01-RN-10 |

### Cálculo
Vale para pedidos aceitos pela validação.

| ID | Regra | Origem |
|---|---|---|
| E01-RN-11 | Alíquota de PF pelo valor total dos itens: até 499,99 = 0%; de 500,00 a 2.000,00 = 12%; de 2.000,01 a 3.500,00 = 15%; acima de 3.500,00 = 17%. | RN-01, Q-04 |
| E01-RN-12 | Alíquota de PJ pelo valor total dos itens e pelo regime (Simples Nacional / Lucro Real / Lucro Presumido): até 999,99 = 3% / 3% / 3%; de 1.000,00 a 2.000,00 = 7% / 9% / 9%; de 2.000,01 a 5.000,00 = 13% / 15% / 16%; acima de 5.000,00 = 19% / 20% / 20%. | RN-02, Q-04 |
| E01-RN-13 | A faixa usa o `valor_total_itens` do pedido, já conferido pela E01-RN-07, e não considera o frete. O pedido tem uma única alíquota, aplicada a todos os itens. | RN-01 a RN-03, Q-04 |
| E01-RN-14 | Tributo do item = valor unitário × quantidade × alíquota, arredondado a 2 casas (E01-RN-16). | Q-03, Q-08 |
| E01-RN-15 | Frete da nota = `valor_frete` do pedido acrescido do percentual da região do endereço de entrega (E01-RN-06), arredondado a 2 casas (E01-RN-16): Norte 8%; Nordeste 8,5%; Centro-Oeste 7%; Sudeste 4,8%; Sul 6%. | RN-04, RN-05, Q-06, Q-08 |
| E01-RN-16 | Arredondamento pela ABNT NBR 5891, olhando o algarismo depois da 2ª casa: menor que 5, a 2ª casa fica; maior que 5, ou 5 seguido de algum algarismo diferente de zero, a 2ª casa sobe; 5 sem nada diferente de zero depois, a 2ª casa sobe se for ímpar e fica se for par. | Q-08 |
| E01-RN-17 | A nota traz: identificador novo a cada emissão; data e hora do momento em que a nota é gerada, não a `data` do pedido; `valor_total_itens` igual ao do pedido; `valor_frete` da E01-RN-15; os itens do pedido, na mesma ordem, cada um com `id_item`, `descricao`, `valor_unitario` e `quantidade` como recebidos e o tributo da E01-RN-14; e o destinatário como recebido, com todos os campos enviados, inclusive `bairro`, `cidade` e `pais` dos endereços, que hoje se perdem e passam a voltar sem retirar nenhum campo atual da resposta (o documento não passa pela limpeza da E01-RN-02). A nota não tem campo de total geral (itens + frete + tributos); cada valor monetário tem no máximo 2 casas decimais, e assim qualquer soma fecha em centavos. | RN-06, Q-08; ordem, destinatário completo, documento e total geral: definido nesta spec |
| E01-RN-18 | Cada nota traz exatamente os itens do próprio pedido, com a mesma quantidade de linhas, sem influência de pedidos processados antes nem de pedidos enviados antes de a resposta deste chegar. | Demanda (problemas funcionais) |

#### Exemplos de cálculo
Pedido base: PF, CPF `887.403.470-95`, `data` 2022-05-01, 1 item de 50,00 × 2, `valor_total_itens` 100,00, `valor_frete` 10,00, um endereço `ENTREGA` na região `SUDESTE`. Todos são pedidos aceitos pela validação. Cada linha muda só o que está descrito; o total declarado acompanha a soma dos itens. "PJ" = CNPJ `49.695.613/0001-80` no lugar do CPF, com o regime indicado.

| # | Mudança no pedido base | Resultado na nota | Regra |
|---|---|---|---|
| 1 | Nenhuma | Alíquota 0%; tributo do item 0,00; frete 10,48; `valor_total_itens` 100,00 | E01-RN-11, E01-RN-15 |
| 2 | 1 item de 499,99 × 1 | Alíquota 0%; tributo 0,00 | E01-RN-11 |
| 3 | 1 item de 250,00 × 2 (total 500,00) | Alíquota 12%; tributo 60,00 | E01-RN-11, E01-RN-14 |
| 4 | 1 item de 2.000,00 × 1 | Alíquota 12%; tributo 240,00 | E01-RN-11 |
| 5 | 1 item de 2.000,01 × 1 | Alíquota 15%; tributo 300,00 (300,0015 arredondado) | E01-RN-11, E01-RN-16 |
| 6 | 1 item de 3.500,00 × 1 | Alíquota 15%; tributo 525,00 | E01-RN-11 |
| 7 | 1 item de 3.500,01 × 1 | Alíquota 17%; tributo 595,00 (595,0017 arredondado) | E01-RN-11, E01-RN-16 |
| 8 | PJ Simples Nacional, 1 item de 999,99 × 1 | Alíquota 3%; tributo 30,00 (29,9997 arredondado) | E01-RN-12, E01-RN-16 |
| 9 | PJ Simples Nacional, 1 item de 1.000,00 × 1 | Alíquota 7%; tributo 70,00 | E01-RN-12 |
| 10 | PJ Lucro Real, 1 item de 1.000,00 × 1 | Alíquota 9%; tributo 90,00 | E01-RN-12 |
| 11 | PJ Lucro Real, 1 item de 2.000,01 × 1 | Alíquota 15%; tributo 300,00 | E01-RN-12, E01-RN-16 |
| 12 | PJ Lucro Presumido, 1 item de 3.000,00 × 1 | Alíquota 16%; tributo 480,00 | E01-RN-12 |
| 13 | PJ Simples Nacional, 1 item de 5.000,00 × 1 | Alíquota 13%; tributo 650,00 | E01-RN-12 |
| 14 | PJ Simples Nacional, 8 × 730,00, frete 72,00 | Alíquota 19%; tributo 1.109,60; frete 75,46 (75,456 arredondado) | E01-RN-12, E01-RN-14, E01-RN-15 |
| 15 | PJ Lucro Real, 8 × 730,00 | Alíquota 20%; tributo 1.168,00 | E01-RN-12 |
| 16 | Item A de 300,00 × 1 e item B de 250,00 × 2 (total 800,00) | Alíquota 12% para os dois; tributo A 36,00, B 60,00; itens na ordem A, B | E01-RN-13, E01-RN-17 |
| 17 | PJ Simples Nacional, 1 item de 11,50 × 1 | Alíquota 3%; tributo 0,34 (0,345: 4 é par, fica) | E01-RN-16 |
| 18 | PJ Simples Nacional, 1 item de 12,50 × 1 | Alíquota 3%; tributo 0,38 (0,375: 7 é ímpar, sobe) | E01-RN-16 |
| 19 | Frete 1,00, região `NORDESTE` | Frete 1,08 (1,085: 8 é par, fica) | E01-RN-15, E01-RN-16 |
| 20 | Região `CENTRO_OESTE` | Frete 10,70 | E01-RN-15 |
| 21 | Frete 0,00 | Frete 0,00 | E01-RN-15 |
| 22 | Primeiro endereço `COBRANCA` no `NORTE`; segundo `ENTREGA` no `SUL` | Frete 10,60 (Sul) | E01-RN-15 |
| 23 | Dois endereços `ENTREGA`: primeiro no `NORTE`, segundo no `SUL` | Frete 10,80 (Norte) | E01-RN-15 |
| 24 | Único endereço `COBRANCA_ENTREGA` no `NORTE` | Frete 10,80 | E01-RN-15 |
| 25 | Nenhuma (conferir data) | `data` da nota = momento da geração, não 2022-05-01 | E01-RN-17 |
| 26 | Endereço com `bairro` Mooca, `cidade` São Paulo e `pais` Brasil | Nota devolve os três campos com os mesmos valores | E01-RN-17 |
| 27 | Documento enviado como `887.403.470-95` | Nota devolve `887.403.470-95` | E01-RN-17 |
| 28 | Mesmo pedido enviado duas vezes seguidas | Duas notas, com identificadores diferentes, cada uma com 1 item | E01-RN-17, E01-RN-18 |
| 29 | Pedido de 1 item e pedido de 3 itens, o segundo enviado antes de a resposta do primeiro chegar | Nota com 1 item e nota com 3 itens, cada uma com os próprios itens | E01-RN-18 |
| 30 | PJ Lucro Real, 1 item de 999,99 × 1 | Alíquota 3%; tributo 30,00 | E01-RN-12, E01-RN-16 |
| 31 | PJ Lucro Presumido, 1 item de 999,99 × 1 | Alíquota 3%; tributo 30,00 | E01-RN-12, E01-RN-16 |
| 32 | PJ Lucro Presumido, 1 item de 1.000,00 × 1 | Alíquota 9%; tributo 90,00 | E01-RN-12 |
| 33 | PJ Lucro Presumido, 1 item de 5.000,01 × 1 | Alíquota 20%; tributo 1.000,00 (1.000,002 arredondado) | E01-RN-12, E01-RN-16 |
| 34 | PJ Simples Nacional, 1 item de 11,51 × 1 | Alíquota 3%; tributo 0,35 (0,3453: 5 seguido de algarismo diferente de zero, sobe) | E01-RN-16 |

### Fora deste entregável
- Reenvio devolver a mesma nota: E-03 (até lá, cada envio gera nota nova, como no exemplo de cálculo 28). A validação do `id_pedido` também entra no E-03.
- Campos que o levantamento não exige (`id_pedido`, `data`, `nome`, `id_item`, `descricao` e os campos do endereço além de finalidade e região) não são validados nesta fase, exceto pela E01-RN-08.
- Fuso horário da data: tratado na parte técnica, sem mudar a regra.

## Parte não funcional
A preencher pelo time.
