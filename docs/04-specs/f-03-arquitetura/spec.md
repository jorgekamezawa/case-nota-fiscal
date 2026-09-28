# Spec F-03: arquitetura

| | |
|---|---|
| **Épico** | [Nota fiscal confiável](../../02-produto/epico-nota-fiscal-confiavel.md) (habilitador técnico) |
| **Fase** | 3 |
| **Status** | Em revisão |
| **Decisão** | [ADR-0003](../../03-engenharia/adr/0003-arquitetura-hexagonal-enxuta.md) |

## Objetivo
Isolar as regras de negócio de HTTP e das integrações, para que uma regra nova seja código novo.

## Parte funcional
A fase muda uma regra da [spec E-01](../e-01-nota-correta/spec.md): a recusa passa a listar os motivos por etapa de validação, e não mais todos de uma vez; na etapa 1, erros de tipo vêm um por vez (E01-RN-09, E01-RN-10 e exemplo de validação 23, já atualizados na spec E-01). Na etapa 1 (preenchimento e formato), o pedido nem chega às regras de negócio; na etapa 2, todas as regras de negócio violadas vêm juntas. As demais regras da E-01 continuam valendo.

## Parte não funcional

| ID | Requisito | Origem |
|---|---|---|
| F03-NF-01 | Código separado em domínio, aplicação e adaptadores, nos pacotes do ADR-0003. A aplicação define as portas (interfaces) e não conhece as implementações, que ficam nos adaptadores. O domínio não depende de framework, HTTP, formato JSON nem de outras camadas, exceto as anotações do framework que só declaram componentes. Um teste de arquitetura no build quebra quando a regra é violada. | ADR-0003; D-14 |
| F03-NF-02 | Regra de tributação nova é código novo: cada tipo de pessoa ou regime tem sua regra, e incluir uma regra não altera as existentes. Um teste com uma regra fictícia prova isso. | RFC O-04; D-14 |
| F03-NF-03 | Regras do domínio testadas sem subir a aplicação e sem as esperas simuladas. | ADR-0003; RFC O-04 |
| F03-NF-04 | Contrato inalterado: respostas de referência da fase 2 e teste de contrato verdes, sem alteração. | RFC R-01, R-04, O-07 |
| F03-NF-05 | Os testes existentes continuam verdes com as mesmas asserções; só mudam imports, pacotes e nomes das classes movidas ou renomeadas. Exceção: os testes da regra alterada na parte funcional passam a esperar a recusa por etapa. | RFC seção 7 |
| F03-NF-06 | Objetos do domínio e do contrato não mudam depois de criados. | D-01 (estado compartilhado) |
| F03-NF-07 | Esperas simuladas das integrações inalteradas, inclusive a da entrega com 6 linhas de item ou mais. | RFC R-02 |
| F03-NF-08 | Renomeações restantes do D-18: pedidos de exemplo nos recursos de teste, com o nome da pasta corrigido, fora do pacote de produção; nome do artefato igual ao nome do serviço. | D-18 |
| F03-NF-09 | Validação em camadas: preenchimento e formato no adaptador de entrada; regras de negócio no domínio, acionadas pela aplicação só com pedido que passou na etapa 1. A aplicação não recebe pedido com formato inválido. | E01-RN-09; ADR-0003 |
| F03-NF-10 | Entidades do domínio só nascem por métodos de fábrica, que aplicam as regras de negócio delas; não há construtor público. | ADR-0003 |

### Dependências
| Item | Uso | Escopo |
|---|---|---|
| ArchUnit (JUnit 5) | Teste de arquitetura (F03-NF-01) | Só testes |
| Spring Boot Starter Validation (Jakarta Validation) | Preenchimento e casas decimais da entrada, por anotações (F03-NF-09) | Aplicação |

## Fora deste entregável
- Validação que depende de estado externo (ex.: reenvio do E-03) fica na aplicação e entra na fase 5.
- Persistência (fase 5) e observabilidade (fase 4).
- Mudança de contrato ou de desempenho.
