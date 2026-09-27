# Diagnóstico técnico: gerador de nota fiscal

| | |
|---|---|
| **Autor** | Jorge Kamezawa (engenharia) |
| **Usado por** | [RFC-0001](../03-engenharia/rfc/0001-modernizacao-gerador-nota-fiscal.md) (resumo na seção 3) e [levantamento](levantamento-regras-negocio.md) |

Fonte única dos defeitos do serviço: causa, evidência e impacto de cada um. Os demais documentos citam pelo ID.

Evidências obtidas executando o código original (Java 11, Spring Boot 2.6.2) e a suíte existente; roteiro em [evidencias-testes-manuais.md](evidencias-testes-manuais.md). **Informado** = listado na demanda; **Identificado** = encontrado nesta análise.

### Crítica

**D-01. Itens acumulados entre requisições** · Informado
- **Causa:** `CalculadoraAliquotaProduto` guarda os itens numa lista `static` (`CalculadoraAliquotaProduto.java:9`), compartilhada por todas as requisições, nunca limpa e devolvida por referência.
- **Evidência:** o mesmo pedido de 1 item retornou 1, 2, 3... 7 itens em 7 chamadas.
- **Impacto:** nota com itens de outros pedidos; valores, total e quantidade errados; dados de um cliente expostos a outro (LGPD); memória crescendo sem limite.

**D-02. Latência crescente após execuções sucessivas** · Informado
- **Causa:** consequência de D-01. Com mais de 5 itens acumulados, a integração de entrega simula 5 s adicionais (`EntregaIntegrationPort.java:10-15`).
- **Evidência:** cerca de 1,5 s nas 5 primeiras chamadas; 6,49 s da 6ª em diante.
- **Impacto:** degradação de mais de 4x para todos os clientes, permanente até reiniciar.

**D-03. Condição de corrida na lista compartilhada** · Identificado
- **Causa:** `ArrayList` compartilhada por várias threads, sem sincronização.
- **Evidência:** chamadas simultâneas perderam de 2% a 22% das inclusões, conforme a carga (11 de 50 numa rodada; 1 de 50 e 5 a 10 de 150 em outras), sem exceção no log. Todas as respostas vieram com a mesma lista.
- **Impacto:** perda silenciosa de itens, sem erro nem alerta.

**D-04. PJ sem regra de tributação gera nota sem itens** · Identificado. Regime `OUTROS`, sem regime ou sem tipo de pessoa responde 200 com `itens: []` (Q-02).

### Alta

**D-05. Integrações sequenciais** · Informado
- **Causa:** as 4 integrações rodam em série; a latência é a soma (1,48 s), não a maior delas (0,5 s). A espera extra da entrega dispara com **6 linhas de item ou mais** (`size() > 5`); a quantidade não influi, e a demanda ("mais de 6") está imprecisa.
- **Evidência:** com a aplicação recém-reiniciada, 5 linhas levaram 1,6 s; 6 linhas, 6,6 s.
- **Impacto:** cada requisição ocupa uma thread do servidor durante todas as esperas.

**D-06. Suíte de testes quebrada e dependente de ordem** · Informado
- **Causa:** testes no pacote `calculadoratributos`, fora do pacote da aplicação; resultado dependente da lista `static`; mock sem efeito porque o serviço usa `new`; testes unitários com as esperas reais (1,5 s e 2,4 s).
- **Evidência:** 3 testes, 1 falha (`expected: <1> but was: <2>`) e 1 erro (`Unable to find a @SpringBootConfiguration`); cada teste isolado passa.
- **Impacto:** a suíte não protege contra regressão e cobre 2 das 21 combinações de regra (16 alíquotas e 5 regiões de frete).

**D-07. Falha parcial sem tratamento** · Identificado
- **Causa:** nenhum retry ou compensação; `InterruptedException` embrulhada sem restaurar a flag de interrupção.
- **Evidência:** numa cópia com o registro falhando, a baixa de estoque já tinha rodado, a entrega não foi chamada e o cliente recebeu 500; após o `catch`, a flag fica `false`.
- **Impacto:** estoque baixado sem nota registrada.

**D-08. Sem idempotência** · Identificado
- **Evidência:** o mesmo pedido enviado 2 vezes gerou 2 notas (`e212592f...` e `0501b078...`).
- **Impacto:** um retry do cliente duplica a nota e repete estoque, entrega e financeiro.

**D-09. Total declarado não é conferido** · Identificado. A faixa usa o total do payload: declarar 100 com item de 5000 deu alíquota 0% (Q-01).

**D-10. Entrada sem validação** · Identificado. Campo obrigatório ausente gera 500; valores negativos, lista vazia e quantidade fracionária truncada são aceitos; o 400 existente vem com `message: null` (Q-07).

**D-11. Frete inconsistente sem endereço de entrega** · Identificado. Sem endereço de entrega o frete sai 0; endereço sem região gera 500 (Q-05).

**D-12. Valores monetários em `double`** · Identificado. Frete 33.33 retornou 36.16305; tributo de 100 a 7% retornou 7.000000000000001 (Q-08).

**D-13. Sem observabilidade** · Identificado
- **Evidência:** `/actuator/health` retorna 404; uma requisição bem-sucedida não gera log; o projeto não tem logger nem métricas.
- **Impacto:** problemas como D-01 e D-02 só são percebidos pelos consumidores.

### Média

**D-14. Alta complexidade e concentração de responsabilidades** · Informado
- **Causa:** um método de cerca de 120 linhas concentra alíquota PF e PJ, frete, montagem da nota e integrações, com `if/else` em 3 níveis e 4 tabelas de faixas quase iguais.
- **Impacto:** toda regra nova altera a mesma classe, o que explica a instabilidade relatada.

**D-15. Spring Boot fora de suporte** · Identificado. Suporte OSS do 2.6 encerrado em 30/11/2022 e comercial em 29/02/2024; o Java 11 ainda tem suporte.

### Baixa

**D-16. Campos do endereço descartados** · Identificado. `bairro`, `cidade` e `pais` não existem em `Endereco` e somem da resposta; não quebra contrato, porque só o de entrada está congelado.

**D-17. Data da nota sem fuso horário** · Identificado. `LocalDateTime.now()` depende do fuso do servidor.

**D-18. Menores** · Identificado. Injeção por campo, variável sem uso, integrações criadas com `new`, pasta `paylods` e nome do artefato divergente; tratados nas specs.

**Não é defeito, precisa de decisão do Fiscal:** o tributo incide sobre o valor unitário (Q-03).
