# Spec F-02: modernização

| | |
|---|---|
| **Épico** | [Nota fiscal confiável](../../02-produto/epico-nota-fiscal-confiavel.md) (habilitador técnico) |
| **Fase** | 2 |
| **Status** | Em revisão |
| **Decisão** | [ADR-0001](../../03-engenharia/adr/0001-java-21-e-spring-boot-com-maior-suporte.md) |

## Objetivo
Levar o serviço para versões com suporte de Java e Spring Boot, sem mudar nenhum comportamento que os consumidores enxergam.

## Parte não funcional
Não há parte funcional: a fase não cria nem altera regra de negócio. As regras da [spec E-01](../e-01-nota-correta/spec.md) continuam valendo.

| ID | Requisito | Origem |
|---|---|---|
| F02-NF-01 | Java 21 e Spring Boot 4.1.x no build, no CI e no ambiente local. | ADR-0001; RFC R-03, O-05; D-15 |
| F02-NF-02 | Mesmo comportamento: para os mesmos pedidos, as respostas de sucesso (200), recusa (400), corpo que não é JSON (400) e erro inesperado (500) são iguais às da versão anterior, campo a campo, com identificador e data da nota fixados. As respostas de referência são geradas na versão anterior, antes de qualquer troca de versão. | ADR-0001; RFC O-07 |
| F02-NF-03 | Os testes da fase 1 continuam verdes com as mesmas asserções; só mudam imports e anotações que a troca de versão obriga. | RFC seção 7 |
| F02-NF-04 | Migração em duas etapas, cada uma com suíte e CI verdes: primeiro Spring Boot 3.5 com Java 21; depois Spring Boot 4.1. | ADR-0001 (plano da migração) |
| F02-NF-05 | Esperas simuladas das integrações inalteradas, inclusive a da entrega com 6 linhas de item ou mais. | RFC R-02 |
| F02-NF-06 | Nenhuma dependência nova; só troca de versão das existentes, conforme a tabela de versões. Nenhuma biblioteca em modo de compatibilidade legado. | Decisão do time |
| F02-NF-07 | Nenhum recurso novo do Java 21 nesta fase: virtual threads, records e similares entram na fase que os justifica. | RFC R-03 |

### Versões
| Item | Antes | Depois |
|---|---|---|
| Java | 11 | 21 (Temurin) |
| Spring Boot | 2.6.2 | 3.5.x (etapa 1), 4.1.x (etapa 2), último patch no início da fase |
| Jackson | 2 (gerenciado pelo Boot) | 3 (gerenciado pelo Boot) |
| Lombok, Mockito, JUnit, surefire | gerenciados pelo Boot 2.6 | gerenciados pelo Boot 4.1 |

**Exceção aceita ao F02-NF-02:** as respostas 405 (método não permitido) e 415 (tipo de conteúdo não suportado), hoje sem corpo, passam a trazer corpo no formato Problem Details, padrão do [ADR-0009](../../03-engenharia/adr/0009-erros-no-formato-problem-details.md).

## Fora deste entregável
- Desempenho: não é medido nesta fase; latência é objetivo da fase 5 (RFC O-02).
- Refatorações, renomeações e arquitetura: fase 3.
- README: fora desta spec.
