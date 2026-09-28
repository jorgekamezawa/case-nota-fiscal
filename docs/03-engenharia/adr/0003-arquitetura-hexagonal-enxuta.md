---
status: proposto
---
# ADR-0003: Organizar o serviço em arquitetura hexagonal enxuta

**Decisão:** separar **domínio, aplicação e adaptadores por pacotes, num único módulo**, com integrações e persistência entrando por portas (interfaces), porque as regras de tributação mudam com frequência e hoje estão misturadas com HTTP e integrações numa classe só.

## Contexto
O serviço concentra regras de tributação e frete, conversa com 4 sistemas externos e deve passar a ter persistência. Tudo está numa única classe ([RFC-0001, D-14](../rfc/0001-modernizacao-gerador-nota-fiscal.md#3-diagnóstico)), o que impede testar as regras isoladamente (D-06) e faz toda regra nova alterar o mesmo código.

O que pesa: regra testável sem infraestrutura; trocar integração ou banco sem tocar nas regras; cerimônia proporcional a um serviço pequeno.

## Alternativas descartadas
- **Camadas MVC (controller, service, model):** simples e conhecida, mas o service continua sendo o lugar de regra, orquestração e chamadas externas ao mesmo tempo, que é exatamente a causa do D-14.
- **Hexagonal completa, com módulos Maven separados e mapeamento entre todas as camadas:** o compilador impede dependências proibidas, mas cada mudança passa por vários módulos e conversões; desproporcional para um serviço deste tamanho.

## Consequências
- **Ganhos:** regras testáveis sem Spring e sem as esperas simuladas; trocar uma integração ou o banco não toca o domínio.
- **Custos:** mais pacotes e interfaces; mapeamento entre o contrato JSON e o domínio.
- **Passa a ser obrigatório:** o domínio não depende de Spring, HTTP nem banco, exceto as anotações de estereótipo do Spring (`@Component`, `@Service`), que só declaram beans, e o Lombok, que só gera código na compilação; um teste de arquitetura (ArchUnit) no build quebra quando essa regra é violada. Mapeamento só onde o formato externo difere do domínio. A estrutura de pacotes, com base `br.com.itau.geradornotafiscal`:

| Pacote | Responsabilidade |
|---|---|
| `domain.entity` | Objetos com identidade própria: pedido e nota. Construtor privado; só nascem por métodos de fábrica (ex.: `Pedido.criar`), que aplicam as regras de negócio |
| `domain.valueobject` | Objetos definidos só pelos valores, como records: item, item da nota, destinatário, documento, endereço e enums |
| `domain.service.tributacao` | Regras de alíquota, uma classe por tipo de pessoa ou regime, e cálculo do tributo |
| `domain.service.frete` | Cálculo do frete por região |
| `domain.service.validacao` | Regras de negócio do pedido, chamadas pelo método de fábrica |
| `domain.service.calculo` | Arredondamento |
| `domain.exception` | Violações de regra de negócio |
| `application.port.in` | Caso de uso de geração da nota; o comando de entrada fica em `command` |
| `application.port.out` | Portas para registro, estoque, entrega, financeiro e persistência |
| `application.usecase` | Orquestração: confere as regras de negócio, calcula, registra e aciona as integrações |
| `adapter.in.web.controller` | Controller |
| `adapter.in.web.dto.request` / `dto.response` | Contrato de entrada e de saída (`snake_case`), inclusive o corpo de erro |
| `adapter.in.web.mappers` | Conversão entre o contrato e o domínio |
| `adapter.in.web.validacao` | Preenchimento e formato da entrada |
| `adapter.in.web.handler` | Tratamento de erros |
| `adapter.out.<sistema>` | Integrações (estoque, registro, entrega, financeiro) e, depois, persistência |
| `config` | Configuração do Spring |

A aplicação pode usar anotações do Spring (ex.: `@Service`, `@Transactional`); o domínio só as de estereótipo, que declaram beans.
