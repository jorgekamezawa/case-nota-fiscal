# Back

## Build e testes
- `./mvnw clean verify` precisa passar antes de qualquer entrega. Java 21 e Spring Boot 4.1 (Jackson 3).

## Código
- Arquitetura hexagonal do [ADR-0003](../docs/03-engenharia/adr/0003-arquitetura-hexagonal-enxuta.md), com subpacotes por tipo em cada camada (ex.: `adapter.in.web.dto.request`, `dto.response`, `mappers`; `domain.entity`, `domain.valueobject`, `domain.service.<assunto>`). O `ArquiteturaTest` confere as regras no build.
- Domínio sem framework, exceto `@Component` e Lombok. Entidade não é record: construtor privado e métodos de fábrica que aplicam as regras dela; value object pode ser record.
- Caso de uso: interface `XxxUseCase` em `application.port.in` (comando em `command`) e implementação `XxxUseCaseImpl` em `application.usecase`. A web só fala com a porta de entrada.
- Colaboradores injetados por construtor com `@RequiredArgsConstructor` e campos `final`; nunca criados com `new`.
- Bean sem estado mutável: nada de dado de requisição em campo, principalmente `static`.
- Valor monetário sempre decimal exato; arredondamento só pela classe `Arredondamento` (NBR 5891).
- Dependência nova só com aprovação.
- Telemetria (logs, métricas e spans) só nos adaptadores e em `config`; aplicação e domínio não a conhecem (`ArquiteturaObservabilidadeTest`). Log com dado do pedido usa campo próprio (`addKeyValue`), nunca a mensagem; `addKeyValue` nunca leva dado pessoal, porque os campos não passam pela máscara.

## Contrato e erros
- Entrada da API imutável; o teste de contrato não pode ser enfraquecido.
- Erros no formato Problem Details ([ADR-0009](../docs/03-engenharia/adr/0009-erros-no-formato-problem-details.md)); `type` novo entra em `docs/api/erros.md`.
- Mensagem de erro e log nunca trazem dado pessoal (nome, documento, endereço).

## Testes
- Defeito: primeiro o teste que reproduz, depois o conserto.
- Teste unitário sem as esperas simuladas (integrações por mock); as esperas nunca são removidas do código.
- Nome do teste cita a regra ou o requisito da spec que cobre (ex.: `E01-RN-07`), para a rastreabilidade da task de QA.
