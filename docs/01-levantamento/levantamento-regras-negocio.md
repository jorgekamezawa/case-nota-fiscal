# Levantamento de regras de negócio: gerador de nota fiscal

| | |
|---|---|
| **Autor** | Jorge Kamezawa (engenharia) |
| **Revisor** | PO |
| **Status** | Respondido pelo PO |
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
- **Resposta do PO:** Opção (a) com ajuste.
  - Recusar o pedido quando `valor_total_itens` divergir da soma dos itens (valor unitário × quantidade). A comparação é exata, em centavos, depois do arredondamento da Q-08.
  - A mensagem informa o total declarado e o total calculado.
  - Os sistemas de origem são avisados antes de a regra entrar no ar: documentação do contrato de erro e nota na versão (changelog).
  - **Justificativa:** aceitar o total informado permite recolher menos tributo; recalcular em silêncio esconde o erro de quem enviou.
  - **Validado com:** Fiscal; sistemas de origem (a divergência só ocorre por erro e aceitam a recusa com aviso).

**Q-02. O que fazer com pedido sem regra de tributação?** (RN-02)
- **Decide:** PO, com validação do Fiscal
- **Hoje:** PJ com regime `OUTROS`, sem regime ou sem tipo de pessoa gera nota **sem itens**, respondida como sucesso.
- **Opções:** (a) rejeitar o pedido; (b) aplicar uma alíquota padrão (qual?); (c) emitir sem tributo.
- **Recomendação do time:** (a). É melhor recusar do que emitir documento fiscal errado.
- **Resposta do PO:** Opção (a) com ajuste.
  - Recusar o pedido, informando o que falta (tipo de pessoa ou regime) ou que o regime não é atendido.
  - **Justificativa:** nota sem itens respondida como sucesso é falha silenciosa e documento fiscal errado.
  - **Validado com:** Fiscal (`OUTROS` não cobre regime com volume; MEI entra no Simples Nacional).

**Q-03. O tributo é por unidade ou pelo total do item?** (RN-03)
- **Decide:** Fiscal
- **Hoje:** valor unitário × alíquota. Com 8 monitores de 730,00 a 19%, o tributo é 138,70, não 1.109,60.
- **Opções:** (a) manter por unidade; (b) mudar para valor unitário × quantidade × alíquota.
- **Recomendação do time:** (a) até a área fiscal confirmar. Mudar altera o valor que os consumidores já recebem e exige comunicá-los antes.
- **Resposta do PO:** Opção (b).
  - O tributo do item é valor unitário × quantidade × alíquota. Exemplo: 8 × 730,00 = 5.840,00; tributo a 19% = 1.109,60.
  - Os consumidores são avisados antes, porque o valor do tributo muda.
  - **Justificativa:** o valor do item é quantidade × valor unitário; a regra atual recolhe tributo a menor e explica parte dos relatos de valores inconsistentes.
  - **Validado com:** Fiscal; Contabilidade.

**Q-04. Os limites das faixas estão corretos?** (RN-01, RN-02)
- **Decide:** Fiscal
- **Hoje:** o limite superior pertence à faixa, e a faixa não considera o frete. Exemplos para PF: 499,99 → 0%; 500,00 → 12%; 2.000,00 → 12%; 2.000,01 → 15%.
- **Opções:** (a) confirmar os limites como estão; (b) corrigir os limites (quais?); (c) incluir o frete no valor que define a faixa.
- **Recomendação do time:** (a), porque não há relato de erro nos limites.
- **Resposta do PO:** Opção (a) com ajuste.
  - Limites confirmados como estão; o frete não entra no valor que define a faixa.
  - A faixa é classificada sobre o valor já arredondado a 2 casas (Q-08), para não haver valor entre 2.000,00 e 2.000,01 sem faixa.
  - **Validado com:** Fiscal.

### Frete

**Q-05. O que fazer sem endereço de entrega válido?** (RN-04, RN-05)
- **Decide:** PO
- **Hoje:** sem endereço `ENTREGA` ou `COBRANCA_ENTREGA`, o frete sai **0,00** sem aviso. Com o endereço mas sem região, o sistema dá erro.
- **Opções:** (a) rejeitar o pedido; (b) cobrar o frete informado sem acréscimo.
- **Recomendação do time:** (a). Sem endereço de entrega também não é possível agendar a entrega.
- **Resposta do PO:** Opção (a).
  - Recusar nos dois casos (sem endereço de entrega; endereço de entrega sem região), informando o campo.
  - **Justificativa:** frete sem acréscimo é cobrança errada, e sem endereço a entrega não pode ser agendada.
  - **Premissa:** toda venda deste fluxo tem entrega física.

**Q-06. Com mais de um endereço de entrega, qual vale?** (RN-04)
- **Decide:** PO
- **Hoje:** o primeiro da lista.
- **Opções:** (a) manter o primeiro; (b) rejeitar se houver mais de um.
- **Recomendação do time:** (a); não há relato de problema.
- **Resposta do PO:** Opção (a). Vale o primeiro endereço de entrega.
  - **Justificativa:** não há relato de problema; recusar quebraria quem já envia vários endereços.

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
- **Resposta do PO:** Recomendações do time aceitas, com acréscimos.
  - Destinatário, tipo de pessoa, ao menos 1 item e endereço são obrigatórios. Também são obrigatórios: regime, quando PJ (Q-02); documento coerente com o tipo de pessoa (CPF para PF, CNPJ para PJ), com dígito verificador conferido; `valor_total_itens` (Q-01).
  - Quantidade inteira e maior que zero; 2,7 é recusado, não truncado.
  - Valor unitário zero não é permitido.
  - Frete zero é permitido. O `valor_frete` vem da origem e é aceito como informado; só valor negativo é recusado.
  - A recusa lista todos os campos inválidos de uma vez e não repete dado pessoal (nome, documento, endereço).
  - **Justificativa:** dado incompleto hoje vira erro interno ou nota errada; não expor dado pessoal atende à LGPD (minimização).
  - **Validado com:** Fiscal (dígito verificador); DPO (conteúdo da mensagem de erro).

**Q-08. Como arredondar os valores?**
- **Decide:** Fiscal e Contabilidade
- **Hoje:** sem arredondamento; o frete pode sair como 36,16305.
- **Opções:** 2 casas decimais, com (a) regra da ABNT NBR 5891 (5 arredonda para o par) ou (b) arredondamento comercial (5 sempre sobe).
- **Recomendação do time:** (a), por ser a norma brasileira de arredondamento; confirmar qual método a Contabilidade usa hoje. Arredondar cada tributo de item e o frete.
- **Resposta do PO:** Opção (a) com ajuste.
  - 2 casas decimais pela ABNT NBR 5891, aplicadas ao tributo de cada item, ao frete e aos totais.
  - O total da nota é a soma dos valores já arredondados, para que ela feche.
  - **Justificativa:** usar o mesmo método da Contabilidade evita diferença de centavos na conciliação.
  - **Validado com:** Contabilidade (usa a NBR 5891); Fiscal.

### Emissão e sistemas acionados

**Q-09. O que fazer quando o mesmo pedido chega de novo?** (RN-06)
- **Decide:** PO, com os donos dos sistemas de origem
- **Hoje:** cada envio gera uma nota nova e aciona estoque, entrega e financeiro de novo.
- **Opções:** (a) devolver a nota já emitida; (b) rejeitar o reenvio; (c) manter.
- **Recomendação do time:** (a). Um reenvio por falha de rede não pode duplicar a nota. Mesmo `id_pedido` com conteúdo diferente é rejeitado.
- **Pergunta extra:** o `id_pedido` é único no geral ou só dentro de cada sistema de origem?
- **Resposta do PO:** Opção (a).
  - O reenvio devolve a mesma nota (mesmo identificador e mesma data) e não aciona de novo registro, estoque, entrega e financeiro.
  - Mesmo `id_pedido` com conteúdo diferente é recusado.
  - Se o primeiro envio falhou antes de a nota ser gravada, o reenvio processa normalmente.
  - **Pergunta extra:** o `id_pedido` é único no geral e nunca é reutilizado.
  - **Justificativa:** nota duplicada gera tributo, baixa de estoque e cobrança em dobro.
  - **Validado com:** donos dos sistemas de origem.

**Q-10. Estoque, entrega e financeiro precisam estar concluídos quando a nota é devolvida?** (RN-07)
- **Decide:** PO, com os donos de estoque, entrega e financeiro
- **Hoje:** o cliente espera os 4 sistemas terminarem, e o estoque é baixado **antes** do registro da nota.
- **Opções:** (a) devolver a nota assim que registrada e acionar os outros 3 em seguida, sem o cliente esperar; (b) manter tudo antes da resposta.
- **Recomendação do time:** (a). A resposta não traz nenhuma informação desses sistemas, e o pedido deixa de levar até 6,5s. Registrar primeiro garante que nenhum estoque seja baixado para nota inexistente.
- **Resposta do PO:** Opção (a).
  - O cliente espera só: validar, calcular e gravar a nota. Em seguida recebe a nota.
  - Registro, estoque, entrega e financeiro são acionados depois da resposta (assíncrono). O serviço acompanha cada acionamento para tentar de novo (Q-11); nenhum pode se perder, mesmo se o serviço cair.
  - Nenhuma baixa de estoque acontece antes de a nota estar gravada.
  - **Justificativa:** a resposta não traz dado desses sistemas, e o cliente deixa de esperar até 6,5 s.
  - **Validado com:** donos de registro, estoque, entrega e financeiro (ninguém usa o 200 como prova de que eles concluíram).

**Q-11. O que acontece se estoque, entrega ou financeiro falhar depois de a nota ser emitida?** (RN-07, depende da Q-10)
- **Decide:** PO, com os donos de estoque, entrega e financeiro
- **Hoje:** a falha interrompe o fluxo e o cliente recebe erro, mas o que já foi feito não é desfeito.
- **Opções:** (a) tentar de novo automaticamente e, se persistir, alertar a operação para tratar, com a nota mantida; (b) cancelar a nota; (c) avisar o sistema que pediu a nota.
- **Recomendação do time:** (a). A nota já está registrada; cancelar por falha de outro sistema cria um problema fiscal maior que o atraso.
- **Resposta do PO:** Opção (a) com ajuste.
  - A nota é mantida. O serviço tenta de novo automaticamente, sem duplicar a ação (baixa, agendamento ou cobrança).
  - Se a falha persistir, a operação e o dono do sistema ficam sabendo no mesmo dia.
  - Deve ser possível reprocessar só a etapa que falhou, sem emitir outra nota.
  - **Justificativa:** o cliente já recebeu a nota; cancelar por falha de outro sistema cria inconsistência maior que o atraso.
  - **Validado com:** donos de registro, estoque, entrega e financeiro; Risco Operacional.

**Q-12. Por quanto tempo um reenvio devolve a mesma nota?** (depende da Q-09)
- **Decide:** PO, com os donos dos sistemas de origem
- **Hoje:** não se aplica, porque todo envio gera nota nova.
- **Opções:** (a) para sempre; (b) por um período (qual?), depois o `id_pedido` pode ser reutilizado.
- **Recomendação do time:** (a), se o `id_pedido` nunca for reutilizado na origem; senão, o período que a origem garantir.
- **Resposta do PO:** Opção (a), limitada ao prazo de guarda da Q-13.
  - O reenvio devolve a mesma nota enquanto ela estiver guardada (5 anos).
  - **Justificativa:** o `id_pedido` nunca é reutilizado (Q-09), então na prática equivale a "para sempre".
  - **Validado com:** donos dos sistemas de origem.

**Q-13. Por quanto tempo as notas emitidas precisam ser guardadas?**
- **Decide:** Fiscal e Jurídico
- **Hoje:** o serviço não guarda nada; cada nota existe só na resposta.
- **Opções:** (a) o prazo legal de guarda de documento fiscal (qual?); (b) um prazo menor, se a guarda oficial já é feita por outro sistema (qual?).
- **Recomendação do time:** confirmar se outro sistema já é o responsável pela guarda oficial. Se for, este serviço guarda só o necessário para reenvio e auditoria, o que reduz os dados pessoais retidos (LGPD).
- **Resposta do PO:** Opção (a).
  - Este serviço guarda as notas emitidas por 5 anos, prazo da legislação tributária, contados a partir de 1º de janeiro do ano seguinte à emissão, e as apaga depois.
  - **Premissa:** a contagem a partir do exercício seguinte segue o CTN, art. 173; é a leitura conservadora.
  - Não há outro sistema identificado como responsável pela guarda oficial; o Registro é só acionado.
  - **Justificativa:** guardar a menos descumpre obrigação fiscal; a LGPD permite reter dado pessoal para cumprir obrigação legal.
  - **Validado com:** Fiscal; Jurídico; DPO.

**Q-14. Quando dois envios com o mesmo `id_pedido` têm o mesmo conteúdo?** (depende da Q-09)
- **Decide:** PO
- **Hoje:** não se aplica, porque todo envio gera nota nova. A Q-09 decidiu que conteúdo diferente é recusado, sem definir o que é diferente.
- **Casos:** (1) campos em outra ordem; (2) número escrito de outra forma, ex.: `730` e `730.00`; (3) campo nulo num envio e ausente no outro; (4) campo que o contrato não conhece presente só num envio; (5) documento com e sem pontuação; (6) texto com diferença só de espaços ou maiúsculas.
- **Opções:** (a) qualquer diferença no texto enviado recusa; (b) casos 1 a 4 são o mesmo pedido; 5 e 6 são diferentes, porque mudam o que a nota devolve; (c) como (b), e também 5 e 6 iguais.
- **Recomendação do time:** (b). O que o serviço ignora (campo nulo ou desconhecido) não diferencia pedidos, e nada que mudaria a nota devolvida é tratado como igual.
- **Resposta do PO:** Opção (b) com ajuste.
  - Dois envios são iguais quando todo campo que o contrato conhece tem o mesmo valor. Diferença só de forma (casos 1 a 4) não conta.
  - Texto é comparado como recebido: documento com e sem pontuação e diferença de espaços ou maiúsculas são pedidos diferentes (casos 5 e 6).
  - A ordem das listas (itens, documentos, endereços) conta como conteúdo: a nota mantém a ordem, e o endereço de entrega é o primeiro da lista (Q-06).
  - Campo que não aparece na nota também conta: `data` do pedido diferente é outro pedido.
  - A recusa por divergência não repete dado do pedido original nem da nota e se distingue da recusa por validação, para a origem saber que já existe nota para aquele `id_pedido`.
  - **Justificativa:** o reenvio legítimo (falha de rede) repete o mesmo texto, então recusar os casos 5 e 6 quase não tem custo; tratar como igual o que muda a nota devolveria ao consumidor uma nota diferente do que ele enviou, sem ele perceber.

**Q-15. O que responder ao reenvio de um pedido que hoje seria recusado pela validação?** (depende da Q-09 e da Q-12)
- **Decide:** PO
- **Hoje:** não se aplica. Em 5 anos de reenvio (Q-12), uma regra de validação pode mudar, e um reenvio pode chegar com conteúdo inválido.
- **Opções:** (a) validar primeiro: pedido inválido recebe a recusa de validação, mesmo com nota existente; (b) procurar a nota primeiro: conteúdo igual devolve a nota e diferente é recusado como divergente, sem validar.
- **Recomendação do time:** (a). O consumidor recebe o motivo mais útil para corrigir o pedido. Custo: reenvio legítimo pode ser recusado após mudança de regra.
- **Resposta do PO:** Opção (b) com ajuste.
  - Antes de procurar a nota, confere só se o `id_pedido` veio preenchido e no formato certo.
  - Se já existe nota: conteúdo igual (Q-14) devolve a nota; conteúdo diferente é recusado como divergente, sem validar o restante.
  - Se não existe nota: validação completa pelas regras vigentes, inclusive quando o primeiro envio falhou antes de a nota ser gravada (Q-09).
  - **Justificativa:** com (a), após mudança de regra, o reenvio legítimo recebe recusa embora a nota exista e estoque e financeiro já tenham sido acionados; a origem entende venda recusada com cobrança feita. E corrigir o pedido e reenviar cairia de todo modo na recusa por divergência.
  - **Premissa:** devolver nota já emitida não é nova emissão; nota emitida com erro por regra antiga segue a correção pelo Fiscal, não o reenvio.

**Q-16. O que fazer com um pedido grande demais para ser guardado?** (depende da Q-13)
- **Decide:** PO, com Fiscal e donos dos sistemas de origem
- **Hoje:** o serviço não guarda nada e aceita qualquer quantidade de linhas de item. O armazenamento tem limite de tamanho por nota; a estimativa, ainda não medida, é de cerca de 2.500 linhas, conforme o tamanho dos textos.
- **Opções:** (a) recusar, com motivo claro, o pedido acima de um máximo fixo de linhas, abaixo do limite técnico com margem; (b) recusar só quando o pedido não couber, pelo tamanho; (c) dividir o pedido em várias notas.
- **Recomendação do time:** (a), com o máximo definido pelo maior pedido real das origens e pela medição da engenharia. Um número fixo é previsível; (b) aceitaria ou recusaria conforme o tamanho dos textos; (c) muda o que é uma nota.
- **Resposta do PO:** Opção (a) com ajuste.
  - Revista após a medição da engenharia: o máximo passou de 990 para 800 linhas, porque 990 não cabe no pior caso.
  - Pedido com mais de 800 linhas de item é recusado, com motivo claro que informa o máximo e sem repetir dado pessoal (Q-07).
  - 800 fica abaixo do limite de 990 itens por nota do leiaute oficial da NF-e e do limite técnico no pior caso (cerca de 846 linhas), com folga de cerca de 5%; o maior pedido real das origens tem 200 linhas, então nenhuma venda real é recusada.
  - Mesmo abaixo do máximo, pedido que não couber no armazenamento é recusado com motivo claro, nunca com erro interno. Nenhuma nota é devolvida sem estar guardada (Q-10, Q-13).
  - **Justificativa:** número fixo é previsível para as origens; manter 990 faria o mesmo pedido ser aceito ou recusado conforme os textos, e comprimir a nota pioraria a auditoria por 5 anos para um caso sem evidência; dividir em várias notas muda o que é uma nota.
  - **Premissa:** a medição vale para o limite de armazenamento atual e para textos até o tamanho dos campos da NF-e; se um dos dois mudar, a engenharia mede de novo e, se não couber, o caso volta ao PO.
  - **Validado com:** Fiscal (Manual de Orientação do Contribuinte, Anexo I, campo nItem de 1 a 990); donos dos sistemas de origem (maior pedido real: 200 linhas); engenharia (medição com textos no tamanho máximo: 73% do limite sem acentos, 117% só com acentos, cerca de 846 linhas no pior caso).

**Próxima fase (fora deste levantamento):** campos do endereço descartados na resposta e data da nota sem fuso horário.

## 5. Resumo das decisões

| Pergunta | Decisão | Impacto para consumidores do serviço |
|---|---|---|
| Q-01 Total declarado | Recusar divergência, exata em centavos | Origens: pedido hoje aceito passa a ser recusado; exige aviso prévio |
| Q-02 Sem regra de tributação | Recusar, informando o que falta | Origens: sucesso vira recusa; exige aviso prévio |
| Q-03 Base do tributo | Quantidade × valor unitário × alíquota | Origens e financeiro: valor do tributo muda; exige aviso prévio |
| Q-04 Limites das faixas | Mantidos; faixa sobre valor arredondado | Nenhum |
| Q-05 Sem endereço de entrega | Recusar, informando o campo | Origens: frete zero e erro interno viram recusa clara; exige aviso prévio |
| Q-06 Vários endereços | Vale o primeiro | Nenhum |
| Q-07 Validação de entrada | Obrigatórios ampliados; todos os erros juntos, sem dado pessoal | Origens: pedidos hoje aceitos passam a ser recusados; exige aviso prévio |
| Q-08 Arredondamento | 2 casas, NBR 5891; total = soma arredondada | Origens e financeiro: valores com 2 casas; exige aviso prévio |
| Q-09 Reenvio | Devolve a mesma nota sem reacionar sistemas | Estoque, entrega e financeiro deixam de receber duplicatas |
| Q-10 Resposta antes dos sistemas | Gravar, responder, acionar os 4 sistemas em seguida | Registro, estoque, entrega e financeiro recebem depois da resposta |
| Q-11 Falha após emissão | Nova tentativa sem duplicar; alerta no mesmo dia; nota mantida | Origens não recebem erro por falha de outro sistema |
| Q-12 Janela do reenvio | Enquanto a nota estiver guardada (5 anos) | Origens não podem reutilizar `id_pedido` |
| Q-13 Guarda | Este serviço, 5 anos | Nenhum |
| Q-14 Mesmo conteúdo | Mesmo valor em todo campo conhecido; forma não conta; texto e ordem das listas contam | Origens: reenvio com texto ou ordem diferente passa a ser recusado como divergente |
| Q-15 Reenvio inválido pelas regras vigentes | Procurar a nota antes de validar; igual devolve, diferente recusa como divergente | Origens: reenvio legítimo recebe a nota mesmo após mudança de regra; recusa por divergência é motivo novo de recusa |
| Q-16 Pedido grande demais | Recusar acima de 800 linhas de item; recusa por tamanho mantida como proteção | Origens: pedido acima de 800 linhas, hoje aceito, passa a ser recusado; maior pedido real tem 200 |

**Encaminhamentos fora das perguntas:**
- **Itens de um pedido aparecendo em outro (D-01/D-03):** o DPO avalia se houve incidente a comunicar (LGPD). A correção é o conserto do defeito.
- **Notas já emitidas com erro:** o Fiscal avalia se precisam de correção. Fora do escopo desta entrega, pois o serviço não guardava nada até aqui.
- **"Mais de 6 itens" na demanda:** confirmado com a área demandante que são 6 linhas de item ou mais. A espera simulada da entrega é mantida.
