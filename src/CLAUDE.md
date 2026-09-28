# Back

## Build e testes
- `./mvnw verify` precisa passar antes de qualquer entrega. Java 11 até a fase 2 (depois Java 21).

## Código
- Classes na estrutura atual de pacotes até a fase 3 ([ADR-0003](../docs/03-engenharia/adr/0003-arquitetura-hexagonal-enxuta.md)).
- Colaboradores injetados por construtor com `@RequiredArgsConstructor` e campos `final`; nunca criados com `new`.
- Bean sem estado mutável: nada de dado de requisição em campo, principalmente `static`.
- Valor monetário sempre decimal exato; arredondamento só pela classe `Arredondamento` (NBR 5891).
- Dependência nova só com aprovação.

## Contrato e erros
- Entrada da API imutável; o teste de contrato não pode ser enfraquecido.
- Erros no formato Problem Details ([ADR-0009](../docs/03-engenharia/adr/0009-erros-no-formato-problem-details.md)); `type` novo entra em `docs/api/erros.md`.
- Mensagem de erro e log nunca trazem dado pessoal (nome, documento, endereço).

## Testes
- Defeito: primeiro o teste que reproduz, depois o conserto.
- Teste unitário sem as esperas simuladas (integrações por mock); as esperas nunca são removidas do código.
- Nome do teste cita a regra ou o requisito da spec que cobre (ex.: `E01-RN-07`), para a rastreabilidade da task de QA.
