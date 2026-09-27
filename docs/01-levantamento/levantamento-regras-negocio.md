# Levantamento de regras de negócio: gerador de nota fiscal

| | |
|---|---|
| **Autor** | Jorge Kamezawa (engenharia) |
| **Revisor** | PO |
| **Status** | Aguardando respostas do PO |
| **Demanda** | [demanda.md](../00-demanda/demanda.md) |

## 1. Objetivo

Levantar as regras que o sistema aplica hoje e as lacunas encontradas, para que o PO decida como cada uma deve funcionar. As respostas alimentam o épico e as specs de desenvolvimento.

## 2. Fontes analisadas

- **Demanda:** [demanda.md](../00-demanda/demanda.md)
- **Código:** `src/main/java/br/com/itau/geradornotafiscal/`
  - `service/impl/GeradorNotaFiscalServiceImpl.java` (cálculo e fluxo)
  - `service/CalculadoraAliquotaProduto.java` (tributo por item)
  - `service/impl/EstoqueService.java`, `RegistroService.java`, `EntregaService.java`, `FinanceiroService.java` e `port/out/EntregaIntegrationPort.java` (sistemas acionados)
- **Testes manuais:** [evidencias-testes-manuais.md](evidencias-testes-manuais.md)
- **Defeitos encontrados:** [diagnostico-tecnico.md](diagnostico-tecnico.md)

## 3. Regras vigentes

Extraídas do código atual. Descrevem o que o sistema faz hoje, sem julgamento.

### RN-01. Alíquota para pessoa física
A faixa é definida pelo **valor total dos itens informado no pedido**.

| Valor total dos itens | Alíquota |
|---|---|
| até 499,99 | 0% |
| de 500,00 a 2.000,00 | 12% |
| de 2.000,01 a 3.500,00 | 15% |
| acima de 3.500,00 | 17% |

Fonte: `GeradorNotaFiscalServiceImpl.java:29-36`

### RN-02. Alíquota para pessoa jurídica, por regime
A faixa é definida pelo valor total dos itens informado no pedido.

| Valor total dos itens | Simples Nacional | Lucro Real | Lucro Presumido |
|---|---|---|---|
| até 999,99 | 3% | 3% | 3% |
| de 1.000,00 a 2.000,00 | 7% | 9% | 9% |
| de 2.000,01 a 5.000,00 | 13% | 15% | 16% |
| acima de 5.000,00 | 19% | 20% | 20% |

Fonte: `GeradorNotaFiscalServiceImpl.java:43-83`

### RN-03. Base de cálculo do tributo
O tributo de cada item é **valor unitário × alíquota**, sem considerar a quantidade. A mesma alíquota vale para todos os itens do pedido.
Exemplo: 8 monitores de 730,00 a 19% resultam em tributo de 138,70 no item.

Fonte: `CalculadoraAliquotaProduto.java:14`

### RN-04. Escolha do endereço para o frete
Usa o **primeiro endereço** do destinatário com finalidade `ENTREGA` ou `COBRANCA_ENTREGA`. Endereços `COBRANCA` e `OUTROS` são ignorados.

Fonte: `GeradorNotaFiscalServiceImpl.java:90-94`

### RN-05. Acréscimo de frete por região
O frete da nota é o **frete informado no pedido** acrescido de um percentual conforme a região do endereço da RN-04.

| Região | Acréscimo |
|---|---|
| Norte | 8% |
| Nordeste | 8,5% |
| Centro-Oeste | 7% |
| Sudeste | 4,8% |
| Sul | 6% |

Fonte: `GeradorNotaFiscalServiceImpl.java:99-108`

### RN-06. Dados da nota emitida
- **Identificador:** código novo e aleatório a cada emissão.
- **Data:** momento da emissão, não a data do pedido.
- **Valor total dos itens:** copiado do pedido, sem recálculo.
- **Itens:** os do pedido, cada um com seu tributo (RN-03).
- **Destinatário:** o do pedido.

Fonte: `GeradorNotaFiscalServiceImpl.java:112-120`

### RN-07. Sistemas acionados após a emissão
Em sequência. A nota só é devolvida depois que todos terminam. "Itens" na tabela são as linhas de item do pedido, não a quantidade.

| Ordem | Sistema | Ação | Tempo simulado |
|---|---|---|---|
| 1 | Estoque | baixa dos itens | 0,38s |
| 2 | Registro | registro da nota | 0,5s |
| 3 | Entrega | agendamento | 0,35s, ou 5,35s com 6 itens ou mais |
| 4 | Financeiro | envio para contas a receber | 0,25s |

Fonte: `GeradorNotaFiscalServiceImpl.java:123-126` e serviços de cada sistema.

## 4. Lacunas e perguntas ao PO

Cada pergunta traz quem decide, a situação atual, as opções, a recomendação do time e o espaço para a resposta. Evidências em [evidencias-testes-manuais.md](evidencias-testes-manuais.md).

### Tributação

**Q-01. O total informado no pedido deve ser conferido?** (RN-01, RN-02)
- **Decide:** PO, com validação do Fiscal
- **Hoje:** a faixa usa o `valor_total_itens` enviado pelo cliente, sem conferência. Um pedido que declara 100,00 com um item de 5.000,00 recebe alíquota de 0%.
- **Opções:** (a) rejeitar o pedido se o total divergir da soma dos itens (valor unitário × quantidade); (b) ignorar o total informado e usar a soma calculada; (c) manter.
- **Recomendação do time:** (a). Recalcular em silêncio esconde o erro de quem enviou; manter permite recolher menos tributo.
- **Resposta do PO:** pendente

**Q-02. O que fazer com pedido sem regra de tributação?** (RN-02)
- **Decide:** PO, com validação do Fiscal
- **Hoje:** PJ com regime `OUTROS`, sem regime ou sem tipo de pessoa gera nota **sem itens**, respondida como sucesso.
- **Opções:** (a) rejeitar o pedido; (b) aplicar uma alíquota padrão (qual?); (c) emitir sem tributo.
- **Recomendação do time:** (a). É melhor recusar do que emitir documento fiscal errado.
- **Resposta do PO:** pendente

**Q-03. O tributo é por unidade ou pelo total do item?** (RN-03)
- **Decide:** Fiscal
- **Hoje:** valor unitário × alíquota. Com 8 monitores de 730,00 a 19%, o tributo é 138,70, não 1.109,60.
- **Opções:** (a) manter por unidade; (b) mudar para valor unitário × quantidade × alíquota.
- **Recomendação do time:** (a) até a área fiscal confirmar. Mudar altera o valor que os consumidores já recebem e exige comunicá-los antes.
- **Resposta do PO:** pendente

**Q-04. Os limites das faixas estão corretos?** (RN-01, RN-02)
- **Decide:** Fiscal
- **Hoje:** o limite superior pertence à faixa, e a faixa não considera o frete. Exemplos para PF: 499,99 → 0%; 500,00 → 12%; 2.000,00 → 12%; 2.000,01 → 15%.
- **Opções:** (a) confirmar os limites como estão; (b) corrigir os limites (quais?); (c) incluir o frete no valor que define a faixa.
- **Recomendação do time:** (a), porque não há relato de erro nos limites.
- **Resposta do PO:** pendente

### Frete

**Q-05. O que fazer sem endereço de entrega válido?** (RN-04, RN-05)
- **Decide:** PO
- **Hoje:** sem endereço `ENTREGA` ou `COBRANCA_ENTREGA`, o frete sai **0,00** sem aviso. Com o endereço mas sem região, o sistema dá erro.
- **Opções:** (a) rejeitar o pedido; (b) cobrar o frete informado sem acréscimo.
- **Recomendação do time:** (a). Sem endereço de entrega também não é possível agendar a entrega.
- **Resposta do PO:** pendente

**Q-06. Com mais de um endereço de entrega, qual vale?** (RN-04)
- **Decide:** PO
- **Hoje:** o primeiro da lista.
- **Opções:** (a) manter o primeiro; (b) rejeitar se houver mais de um.
- **Recomendação do time:** (a); não há relato de problema.
- **Resposta do PO:** pendente

### Validação e valores

**Q-07. Quais dados são obrigatórios e quais valores são aceitos?**
- **Decide:** PO
- **Hoje:** pedido sem destinatário, itens ou endereço dá erro interno. Quantidade negativa, total negativo e lista de itens vazia são aceitos. Quantidade 2,7 vira 2.
- **Decidir cada item** (recomendação do time entre parênteses):
  - Destinatário, tipo de pessoa, ao menos 1 item e endereço são obrigatórios? (sim)
  - Quantidade precisa ser inteira e maior que zero? (sim; 2,7 é recusado, não truncado)
  - Valor unitário zero, como num brinde, é permitido? (não)
  - Frete zero é permitido? (sim)
  - Pedido que viola uma regra é recusado informando o campo? (sim)
- **Resposta do PO:** pendente

**Q-08. Como arredondar os valores?**
- **Decide:** Fiscal e Contabilidade
- **Hoje:** sem arredondamento; o frete pode sair como 36,16305.
- **Opções:** 2 casas decimais, com (a) regra da ABNT NBR 5891 (5 arredonda para o par) ou (b) arredondamento comercial (5 sempre sobe).
- **Recomendação do time:** (a), por ser a norma brasileira de arredondamento; confirmar qual método a Contabilidade usa hoje. Arredondar cada tributo de item e o frete.
- **Resposta do PO:** pendente

### Emissão e sistemas acionados

**Q-09. O que fazer quando o mesmo pedido chega de novo?** (RN-06)
- **Decide:** PO, com os donos dos sistemas de origem
- **Hoje:** cada envio gera uma nota nova e aciona estoque, entrega e financeiro de novo.
- **Opções:** (a) devolver a nota já emitida; (b) rejeitar o reenvio; (c) manter.
- **Recomendação do time:** (a). Um reenvio por falha de rede não pode duplicar a nota. Mesmo `id_pedido` com conteúdo diferente é rejeitado.
- **Pergunta extra:** o `id_pedido` é único no geral ou só dentro de cada sistema de origem?
- **Resposta do PO:** pendente

**Q-10. Estoque, entrega e financeiro precisam estar concluídos quando a nota é devolvida?** (RN-07)
- **Decide:** PO, com os donos de estoque, entrega e financeiro
- **Hoje:** o cliente espera os 4 sistemas terminarem, e o estoque é baixado **antes** do registro da nota.
- **Opções:** (a) devolver a nota assim que registrada e acionar os outros 3 em seguida, sem o cliente esperar; (b) manter tudo antes da resposta.
- **Recomendação do time:** (a). A resposta não traz nenhuma informação desses sistemas, e o pedido deixa de levar até 6,5s. Registrar primeiro garante que nenhum estoque seja baixado para nota inexistente.
- **Resposta do PO:** pendente

**Q-11. O que acontece se estoque, entrega ou financeiro falhar depois de a nota ser emitida?** (RN-07, depende da Q-10)
- **Decide:** PO, com os donos de estoque, entrega e financeiro
- **Hoje:** a falha interrompe o fluxo e o cliente recebe erro, mas o que já foi feito não é desfeito.
- **Opções:** (a) tentar de novo automaticamente e, se persistir, alertar a operação para tratar, com a nota mantida; (b) cancelar a nota; (c) avisar o sistema que pediu a nota.
- **Recomendação do time:** (a). A nota já está registrada; cancelar por falha de outro sistema cria um problema fiscal maior que o atraso.
- **Resposta do PO:** pendente

**Q-12. Por quanto tempo um reenvio devolve a mesma nota?** (depende da Q-09)
- **Decide:** PO, com os donos dos sistemas de origem
- **Hoje:** não se aplica, porque todo envio gera nota nova.
- **Opções:** (a) para sempre; (b) por um período (qual?), depois o `id_pedido` pode ser reutilizado.
- **Recomendação do time:** (a), se o `id_pedido` nunca for reutilizado na origem; senão, o período que a origem garantir.
- **Resposta do PO:** pendente

**Q-13. Por quanto tempo as notas emitidas precisam ser guardadas?**
- **Decide:** Fiscal e Jurídico
- **Hoje:** o serviço não guarda nada; cada nota existe só na resposta.
- **Opções:** (a) o prazo legal de guarda de documento fiscal (qual?); (b) um prazo menor, se a guarda oficial já é feita por outro sistema (qual?).
- **Recomendação do time:** confirmar se outro sistema já é o responsável pela guarda oficial. Se for, este serviço guarda só o necessário para reenvio e auditoria, o que reduz os dados pessoais retidos (LGPD).
- **Resposta do PO:** pendente

**Próxima fase (fora deste levantamento):** campos do endereço descartados na resposta e data da nota sem fuso horário.

## 5. Resumo das decisões

Preenchido após as respostas do PO.
