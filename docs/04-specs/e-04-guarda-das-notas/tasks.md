# Tasks E-04: guarda das notas por 5 anos

Spec: [spec.md](spec.md). Base `br.com.itau.geradornotafiscal` (abreviado `p`). Cada task termina com `./mvnw -B clean verify` verde, sem aviso novo, e a saída no PR. A QA da fase fica na [T-10 do E-02](../e-02-resposta-sem-esperar/tasks.md).

## Back

### T-01. Persistência da nota
- **Cobre:** E04-RN-01 a E04-RN-03, E04-NF-01, E04-NF-03, E04-NF-05.
- **Dependência:** AWS SDK 2.x (DynamoDB e `apache5-client`, excluindo o `apache-client`); de teste, Testcontainers 2.x e a imagem `amazon/dynamodb-local` com tag fixa (em `GenericContainer`, sem módulo próprio).
- **Classes:**
  - `p.application.port.out.NotaFiscalPersistenciaPort`: buscar a nota pelo `id_pedido` e gravar a nota só se o `id_pedido` não existir ou a nota guardada estiver vencida (E03-RN-06);
  - `p.adapter.out.dynamodb.NotaFiscalDynamoAdapter` e o mapper em `p.adapter.out.dynamodb.mappers`: a nota completa (E04-RN-02) e o `expira_em`; valores monetários voltam com 2 casas (o tipo número do banco perde os zeros à direita), nulos preservados e `data` guardada como texto, para a resposta sair idêntica;
  - `p.config.DynamoDbConfig`: o cliente do SDK com `apache5-client` e pool dimensionado, endpoint por configuração;
  - `p.domain.service.guarda.PrazoDeGuarda`: calcula a data de expurgo (E04-RN-03);
  - `p.domain.entity.NotaFiscal`: nova fábrica `reconstituir`, que remonta a nota lida sem gerar identificador novo;
  - `p.application.usecase.GerarNotaFiscalUseCaseImpl`: grava a nota antes de devolvê-la.
- **Testes:** `PrazoDeGuardaTest` com os exemplos 1 a 3; adaptador contra o DynamoDB Local, com os containers iniciados uma vez para toda a suíte e compartilhados pelos testes com o contexto do Spring; nota lida igual à gravada, campo a campo.
- **Testes existentes:** as classes com `@SpringBootTest` passam a usar os containers; `PedidoBase` e os payloads de `src/test/resources/payloads` passam a ter `id_pedido` único por teste, para um teste não virar reenvio de outro; em `GeradorNFControllerReferenciaTest`, o caso `erro-inesperado` passa a vir de falha inesperada da persistência.

### T-02. Banco indisponível e pedido grande demais
- **Cobre:** E04-RN-01, E04-RN-04, E04-NF-02, E04-NF-04, E04-NF-06.
- **Classes:**
  - `p.application.exception.ArmazenamentoIndisponivelException` e `NotaGrandeDemaisException`: o adaptador traduz nelas as falhas do SDK e o tamanho da nota calculado antes da transação;
  - `p.adapter.in.web.handler.TratadorDeErros`: 503 `servico-indisponivel` e 400 `pedido-grande-demais` (sem campos);
  - `p.domain.service.validacao.RegrasDoPedido` e `p.domain.exception.MotivoRegra`: máximo de 990 linhas, motivo por campo do `pedido-invalido` (etapa 2 da E01-RN-09);
  - adaptador do banco com métrica de falhas por operação; alerta no Grafana (arquivo da T-08 do E-02).
- **Docs:** `docs/api/erros.md` com os `type` novos.
- **Testes:** exemplos 4 a 6; emulador parado responde 503 sem detalhe interno; nota acima do limite de tamanho responde 400; medição do tamanho de 990 linhas com os textos no tamanho do leiaute da NF-e, com a saída no PR (E04-NF-06); se couber, o levantamento volta a "Respondido pelo PO".
