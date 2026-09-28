# Tasks F-02: modernização

Spec: [spec.md](spec.md). Pacote base `br.com.itau.geradornotafiscal` (abreviado `p`). Classes na estrutura atual até a fase 3. Cada task é concluída com `./mvnw verify` verde e a saída no PR.

## Back

### T-01. Respostas de referência
- **Cobre:** F02-NF-02.
- **Classes:** criar `p.web.GeradorNFControllerReferenciaTest` e as respostas de referência em `src/test/resources/referencia/`: sucesso PF e PJ (pedidos de `src/main/resources/paylods/`), sucesso com barra no fim da URL, recusa (exemplo de validação 22 da spec E-01), corpo que não é JSON e erro 500. Relógio fixo por configuração de teste; `id_nota_fiscal` conferido como UUID e retirado da comparação, por ser aleatório. Comparação campo a campo, na mesma ordem, com o texto dos números (`100.00` diferente de `100.0`).
- **Pronto quando:** verde na versão atual (Java 11, Spring Boot 2.6), antes da T-02.

### T-02. Etapa 1: Java 21 e Spring Boot 3.5
- **Cobre:** F02-NF-01 (parcial), F02-NF-03, F02-NF-04, F02-NF-06.
- **Arquivos e classes:**
  - `pom.xml`: parent 3.5.x e `java.version` 21; `.sdkmanrc` em Java 21;
  - `p.web.erro.TratadorDeErros`: assinatura nova da classe base do Spring 6;
  - criar `p.config.BarraFinalConfig`: mantém aceita a URL com barra no fim, que o Spring 6 deixa de aceitar, pelo filtro do próprio Spring;
  - testes com `@MockBean` passam a `@MockitoBean`.
- **Pronto quando:** suíte e respostas de referência verdes, sem warning novo.

### T-04. Etapa 2: Spring Boot 4.1 e Jackson 3
- **Cobre:** F02-NF-01, F02-NF-03, F02-NF-04, F02-NF-06.
- **Arquivos e classes:**
  - `pom.xml`: parent 4.1.x; starters renomeados no Boot 4 contam como troca de versão, não como dependência nova;
  - `p.config.JacksonConfig`: customizador do Jackson 3, mantendo decimais exatos e a ordem atual dos campos;
  - imports do Jackson 3 em `p.web.controller.GeradorNFController`, `p.service.validacao.ValidadorPedido` e nos testes.
- **Pronto quando:** suíte e respostas de referência verdes, sem warning novo.

### T-05. Documentação
- `src/CLAUDE.md`: Java 21 e Spring Boot 4.1; spec F-02 com status "Concluída".

## Infra

### T-03. CI em Java 21
- **Cobre:** F02-NF-01.
- **Arquivos:** `.github/workflows/ci.yml` com Java 21, no mesmo commit da T-02.

## QA

### T-06. Rastreabilidade
Um agente de contexto limpo confere se cada F02-NF tem evidência. Também confere se o diff dos testes da fase 1 traz só imports e anotações (F02-NF-03) e se nenhuma linha com `Thread.sleep` mudou (F02-NF-05). Pronto quando não há lacuna, ou quando cada lacuna tem justificativa aceita.
