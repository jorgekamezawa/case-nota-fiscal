# Tasks E-01: nota correta ou recusa clara

Spec: [spec.md](spec.md). Pacote base `br.com.itau.geradornotafiscal` (abreviado `p`). Classes novas ficam na estrutura atual; a fase 3 as move para a arquitetura hexagonal. Sem dependência nova. Cada task é concluída com `./mvnw verify` verde e a saída no PR.

## Back

### T-01. Consertar a suíte
- **Cobre:** E01-NF-07 (D-06).
- **Classes:** mover os testes de `br.com.itau.calculadoratributos` para `p` (test); renomear `CalculadoratributosApplicationTests` para `GeradorNotaFiscalApplicationTests`; apagar de `GeradorNotaFiscalServiceImplTest` os casos que dependem da lista acumulada (refeitos nas T-03 e T-07).
- **Pronto quando:** a suíte roda verde em qualquer ordem, em duas execuções seguidas.

## Infra

### T-02. CI
- **Cobre:** E01-NF-07.
- **Arquivos:** criar `.github/workflows/ci.yml`: Java 11 (Temurin) e `./mvnw verify` em todo PR e em todo push na `main`, com cache do Maven.
- **Pronto quando:** o workflow roda verde; as tasks seguintes trazem a execução dele como evidência.

## Back

### T-03. Isolamento entre pedidos
- **Cobre:** E01-RN-18, E01-NF-04 (D-01, D-02, D-03).
- **Causa:** `p.service.CalculadoraAliquotaProduto` guarda os itens numa lista `static`, uma só para a aplicação inteira; cada chamada acrescenta os seus e devolve a lista toda. A partir da 6ª chamada a nota passa de 5 itens e a entrega espera 5 s a mais; chamadas simultâneas perdem itens, porque a lista não suporta gravação concorrente.
- **Testes primeiro** (falham hoje), na própria calculadora, sem as esperas das integrações: duas chamadas seguidas, a 2ª devolvendo só os próprios itens; 150 chamadas liberadas juntas por uma trava de largada, cada uma com itens de identificadores próprios, cada resultado com exatamente os seus.
- **Classes:** em `p.service.CalculadoraAliquotaProduto`, a lista passa a ser criada dentro do método, a cada chamada; a classe fica sem estado.

### T-04. Injeção de dependência
- **Cobre:** E01-NF-05, E01-RN-18 (D-18, só a injeção; as renomeações ficam para a fase 3).
- **Classes:**
  - `p.service.impl.EstoqueService`, `RegistroService`, `EntregaService`, `FinanceiroService` e `p.service.CalculadoraAliquotaProduto` viram beans injetados em `p.service.impl.GeradorNotaFiscalServiceImpl` (hoje criados com `new`);
  - `p.port.out.EntregaIntegrationPort` vira bean injetado em `p.service.impl.EntregaService`;
  - `p.web.controller.GeradorNFController` deixa a injeção por campo e perde a variável sem uso;
  - toda injeção é por construtor, com campos `final` e `@RequiredArgsConstructor` do Lombok.
- **Testes** (serviço com integrações simuladas por mock, sem esperas): dez chamadas seguidas do mesmo pedido, com a entrega recebendo 1 linha de item em cada uma (E01-NF-05); exemplos de cálculo 28 e 29.

### T-05. Teste de contrato
- **Cobre:** E01-NF-01, E01-NF-02 (nomes e tipos dos campos).
- **Classes:** criar `p.web.GeradorNFControllerContratoTest`, com os pedidos de `src/main/resources/paylods/` como entrada: confere que a entrada atual é aceita e que a resposta de sucesso traz todos os campos, com os mesmos nomes e tipos.
- **Pronto quando:** verde no código atual, antes das T-06 a T-09. Só muda para ficar mais rígido: 2 casas fixas (T-06), campos novos do endereço (T-07) e resposta 400 (T-09).

### T-06. Valores monetários exatos
- **Cobre:** E01-RN-16, E01-NF-02 (2 casas fixas), E01-NF-06 (D-12).
- **Classes:**
  - valores de `p.model.Pedido`, `Item`, `ItemNotaFiscal` e `NotaFiscal` passam a decimal exato, com escala de 2 casas na resposta, inclusive nos valores que voltam como recebidos;
  - criar `p.service.calculo.Arredondamento`, com a regra da E01-RN-16, usado pela `CalculadoraAliquotaProduto` e pelo cálculo do frete;
  - criar `p.config.JacksonConfig`: números decimais lidos como decimal exato desde a leitura, preservando as casas enviadas (base da E01-RN-08 na T-08).
- **Testes:** exemplos de cálculo 17, 18, 19 e 34; teste unitário da regra; teste de contrato conferindo o texto `100.00`, não só o valor.

### T-07. Cálculo da nota
- **Cobre:** E01-RN-11 a E01-RN-15 e E01-RN-17; E01-NF-02 (D-16, D-17).
- **Classes:**
  - criar `p.service.tributacao.TabelaAliquotas`, que dá a alíquota por tipo de pessoa, regime e faixa (E01-RN-11 a E01-RN-13);
  - `p.service.CalculadoraAliquotaProduto` calcula o tributo pela E01-RN-14;
  - criar `p.service.frete.CalculadoraFrete` (E01-RN-15);
  - `p.service.impl.GeradorNotaFiscalServiceImpl` só orquestra e monta a nota (E01-RN-17);
  - `p.model.Endereco` ganha `bairro`, `cidade` e `pais`;
  - criar `p.config.RelogioConfig`, com relógio em `America/Sao_Paulo`, injetado no serviço.
- **Testes:** exemplos de cálculo 1 a 24 e 30 a 34 como teste parametrizado; exemplos 25 (data da emissão com relógio fixo e fuso padrão da máquina diferente de `America/Sao_Paulo`, no formato atual) e 26 (campos do endereço) como testes próprios; o teste de contrato passa a exigir os campos novos do endereço.

### T-08. Validação do pedido
- **Cobre:** E01-RN-01 a E01-RN-10 (D-04, D-09, D-10, D-11).
- **Classes:**
  - criar `p.service.validacao.ValidadorPedido`: percorre o pedido como árvore JSON e junta todas as violações antes da conversão, para listar todos os erros de formato de uma vez;
  - criar `p.service.validacao.ValidadorDocumento`, com limpeza e dígito verificador de CPF e CNPJ (E01-RN-02);
  - criar `p.service.validacao.Violacao` (campo e código do motivo, sem o valor recebido; o código vira o `type` do erro na T-09) e `PedidoInvalidoException`;
  - `p.web.controller.GeradorNFController` recebe o corpo, valida e só então converte para `Pedido`.
- **Testes primeiro** para os defeitos (falham hoje): exemplos de validação 6, 10, 11, 17, 18 e 27. Depois, os exemplos de validação 1 a 37 como teste parametrizado do validador, e o exemplo de cálculo 27 (documento devolvido sem limpeza).

### T-09. Resposta de recusa e de erro
- **Cobre:** E01-RN-09, E01-NF-03.
- **Classes:**
  - criar `p.web.erro.TratadorDeErros`, que converte `PedidoInvalidoException`, corpo que não é JSON e erro inesperado;
  - criar `p.web.erro.RespostaProblema` (`type`, `title`, `status`, `detail` e a lista de campos);
  - criar `docs/api/erros.md` com os `type` possíveis.
- **Testes:** exemplo de validação 22 pelo endpoint (dois campos, sem dado pessoal no corpo), JSON malformado (400) e exceção inesperada (500, sem stack trace); o teste de contrato passa a cobrir a resposta 400.

## QA

### T-10. Rastreabilidade
Um agente de contexto limpo confere, sem escrever testes, se cada E01-RN, cada E01-NF e cada exemplo da spec tem teste ou evidência que o cubra, e aponta as lacunas. Pronto quando não há lacuna, ou quando cada lacuna tem justificativa aceita.
