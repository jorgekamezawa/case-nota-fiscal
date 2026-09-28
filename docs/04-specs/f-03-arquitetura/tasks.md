# Tasks F-03: arquitetura

Spec: [spec.md](spec.md). Pacotes do [ADR-0003](../../03-engenharia/adr/0003-arquitetura-hexagonal-enxuta.md), base `br.com.itau.geradornotafiscal` (abreviado `p`). Cada task termina com `./mvnw clean verify` verde e a saída no PR. Testes acompanham as classes, com as mesmas asserções (F03-NF-05).

## Back

### T-01. Renomeações do D-18
- **Cobre:** F03-NF-08.
- **Arquivos:** `src/main/resources/paylods/` passa a `src/test/resources/payloads/`; os testes que leem os pedidos de exemplo mudam só o caminho. `<name>` do `pom.xml` de `calculadoratributos` para `geradornotafiscal`.

### T-02. Domínio
- **Cobre:** F03-NF-01, F03-NF-02, F03-NF-03, F03-NF-06.
- **Classes:** objetos do domínio como records, sem anotação de JSON; regras e serviços do domínio como `@Component`, única anotação do Spring permitida.
  - `p.domain.entity`: `Pedido` e `NotaFiscal`;
  - `p.domain.valueobject`: `Item`, `ItemNotaFiscal`, `Destinatario`, `Documento`, `Endereco` e os enums;
  - `p.domain.service.tributacao`: interface `RegraTributacao` e uma classe por regra (`RegraPessoaFisica`, `RegraSimplesNacional`, `RegraLucroReal`, `RegraLucroPresumido`), cada uma com as próprias faixas (E01-RN-11, E01-RN-12); `Tributacao` recebe a lista de regras pelo construtor, escolhe a que se aplica e substitui a `TabelaAliquotas`; `CalculadoraTributo` substitui a `CalculadoraAliquotaProduto` (E01-RN-14);
  - `p.domain.service.frete.CalculadoraFrete` e `p.domain.service.calculo.Arredondamento`, movidas;
  - `p.domain.service.validacao`: `RegrasDoPedido` confere a etapa 2 (E01-RN-02 a E01-RN-07) e junta todas as violações; `ValidadorDocumento` movido;
  - `p.domain.exception`: `PedidoInvalidoException`, `Violacao` e `MotivoRegra`.
- **Testes:** domínio sem subir o Spring. Um teste com uma regra fictícia na lista prova que as regras existentes não mudam; outro, com o contexto do Spring, prova que as 4 regras reais são injetadas na lista (F03-NF-02).

### T-03. Aplicação
- **Cobre:** F03-NF-01, F03-NF-09.
- **Classes:**
  - `p.application.port.in.GerarNotaFiscalUseCase`: recebe o pedido de domínio, já válido na etapa 1;
  - `p.application.port.out`: `RegistroPort`, `EstoquePort`, `EntregaPort` e `FinanceiroPort`;
  - `p.application.usecase.GerarNotaFiscalService` (`@Service`), que substitui a `GeradorNotaFiscalServiceImpl`: confere a etapa 2 pelo domínio, calcula, monta a nota e aciona as portas.
- **Testes:** os do serviço atual, com as portas por mock.

### T-04. Adaptadores
- **Cobre:** F03-NF-01, F03-NF-04, F03-NF-07, F03-NF-09.
- **Classes:**
  - `p.adapter.in.web.controller.GeradorNFController`: só fala com `GerarNotaFiscalUseCase`;
  - `p.adapter.in.web.dto.request`: `PedidoRequest`, `ItemRequest`, `DestinatarioRequest`, `DocumentoRequest` e `EnderecoRequest`;
  - `p.adapter.in.web.dto.response`: `NotaFiscalResponse`, `ItemNotaFiscalResponse`, `DestinatarioResponse`, `DocumentoResponse`, `EnderecoResponse` e `RespostaProblema`;
  - `p.adapter.in.web.mappers`: `PedidoMapper` (contrato para domínio) e `NotaFiscalMapper` (domínio para contrato);
  - `p.adapter.in.web.validacao`: `ValidadorEntrada` (etapa 1: E01-RN-01, E01-RN-08, E01-RN-10), `EntradaInvalidaException` e `MotivoEntrada`;
  - `p.adapter.in.web.handler.TratadorDeErros`: converte as duas exceções na mesma resposta `pedido-invalido`;
  - `p.adapter.out.estoque`, `.registro`, `.entrega` e `.financeiro`: um `*Adapter` por sistema, implementando a porta; em `.entrega`, `EntregaAgendamentoCliente` substitui a `EntregaIntegrationPort`. As esperas ficam iguais.
- **Testes:** controller, contrato e respostas de referência, mudando só imports e caminhos. A tabela dos exemplos de validação roda as duas etapas como a produção; mudam só o exemplo 23 e o caso "sem tipo de pessoa, dígito verificador ainda conferido" (E01-RN-09, E01-RN-10).

### T-05. Teste de arquitetura
- **Cobre:** F03-NF-01.
- **Dependência:** ArchUnit (JUnit 5), escopo de teste, última versão estável.
- **Classes:** `p.ArquiteturaTest`:
  - o domínio só depende de `java..`, do próprio domínio e de `org.springframework.stereotype..`;
  - a aplicação não depende de `adapter` nem de `config`;
  - os adaptadores não dependem uns dos outros.

### T-06. Documentação
- `src/CLAUDE.md`: a linha "classes na estrutura atual até a fase 3" passa a apontar os pacotes do ADR-0003, com subpacotes por tipo; spec F-03 com status "Concluída".

## QA

### T-07. Rastreabilidade
Um agente de contexto limpo confere a evidência de cada F03-NF e das regras alteradas da E-01. Também confere se o diff dos testes só muda o permitido pela F03-NF-05 e se nenhuma linha com `Thread.sleep` mudou. Pronto quando não há lacuna, ou quando cada lacuna tem justificativa aceita.
