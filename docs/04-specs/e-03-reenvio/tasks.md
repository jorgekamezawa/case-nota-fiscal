# Tasks E-03: reenvio devolve a mesma nota

Spec: [spec.md](spec.md). Base `br.com.itau.geradornotafiscal` (abreviado `p`). Cada task termina com `./mvnw -B clean verify` verde, sem aviso novo, e a saída no PR. A QA da fase fica na [T-10 do E-02](../e-02-resposta-sem-esperar/tasks.md).

## Back

### T-03. Hash do pedido
- **Cobre:** E03-RN-04, E03-NF-02.
- **Classes:**
  - `p.adapter.in.web.reenvio.LeitorDoCorpo` e `PedidoRecebido`: lê o corpo antes da conversão (a etapa 1 da validação acontece nela) e guarda na requisição o `id_pedido`, quando legível, e o hash;
  - `p.adapter.in.web.reenvio.HashDoPedido`: a partir da árvore do Jackson (números como decimal exato), mantém só os campos do contrato, tira os nulos, ordena os campos e escreve sem espaços como na RFC 8785, com os números pelo valor decimal exato sem zeros à direita, e aplica SHA-256.
- **Testes:** exemplos 1 a 9 do E-03 no nível do hash; número com mais de 15 dígitos significativos gera hash diferente do vizinho (E03-NF-02).

### T-04. Reenvio
- **Cobre:** E03-RN-01 a E03-RN-03, E03-RN-05, E03-RN-06, E03-NF-01, E03-NF-03, E03-NF-04.
- **Classes:**
  - `p.domain.service.reenvio.RegraDoReenvio` e `p.domain.exception.PedidoDivergenteException`: mesmo hash vale a nota emitida; outro hash é divergência (E03-RN-02, E03-RN-03);
  - `p.application.port.in.ReenvioUseCase` e `p.application.usecase.ReenvioUseCaseImpl`: procura a nota pelo `id_pedido` e aplica a `RegraDoReenvio`; devolve a nota ou nada (segue a emissão);
  - `p.application.exception.NotaJaGuardadaException` e `ConflitoDeGravacaoException`;
  - `p.application.port.in.result.ResultadoDaEmissao`: a nota e se ela foi emitida ou devolvida;
  - `p.adapter.in.web.controller.GeradorNFController`: o `GerarNotaFiscalUseCase` procura a nota e aplica a `RegraDoReenvio` antes de emitir; conta `notas.emitidas` só para nota emitida; `id_pedido` obrigatório no `PedidoRequest`;
  - `p.application.port.in.command.GerarNotaFiscalCommand`: ganha o hash do pedido;
  - `p.adapter.in.web.handler.TratadorDeErros`: na recusa da etapa 1 com `id_pedido` legível, consulta o `ReenvioUseCase` antes de responder, e falha do armazenamento nessa consulta responde 503 ali mesmo; 422 `pedido-divergente`; métrica e log (E03-NF-04);
  - `p.application.usecase.GerarNotaFiscalUseCaseImpl`: em conflito na gravação condicional, nova tentativa curta e o mesmo caminho do reenvio; devolve se a nota foi emitida ou devolvida;
  - `NotaFiscalPersistenciaPort` e `NotaFiscalDynamoAdapter`: gravam e devolvem o hash; leitura fortemente consistente.
- **Docs:** `docs/api/erros.md` com o `pedido-divergente` e o `id_pedido` obrigatório.
- **Testes:** exemplos 10 a 16 do E-03 contra o DynamoDB Local, inclusive envios simultâneos; conflito de transação por simulação (o emulador não o gera); resposta do reenvio idêntica byte a byte à original; nota vencida e ainda não apagada é substituída por nota nova (E03-RN-06).
- **Testes existentes:** `e01Calculo28_mesmoPedidoDuasVezes` passa a conferir o E03-RN-02 (exemplo 28 do E-01 substituído).
