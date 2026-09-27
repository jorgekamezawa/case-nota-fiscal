---
status: proposto
---
# ADR-0009: Responder erros no formato Problem Details (RFC 9457)

**Decisão:** toda resposta de erro segue o **Problem Details** ([RFC 9457](https://www.rfc-editor.org/rfc/rfc9457), padrão da IETF para corpo de erro em APIs HTTP, com `type`, `title`, `status`, `detail` e campos extras), com a lista de campos inválidos quando houver, porque os consumidores passam a receber recusas novas e precisam de um formato estável e legível por máquina para tratá-las.

## Contexto
Hoje, entrada inválida gera 500 com `NullPointerException` ou 400 com `message: null` ([RFC-0001, D-10](../rfc/0001-modernizacao-gerador-nota-fiscal.md#3-diagnóstico)). A proposta faz vários casos que hoje respondem 200 ou 500 passarem a 400, conforme as decisões do PO. Todos os consumidores vão ler esse formato, e mudá-lo depois é quebrar contrato com cada um.

O que pesa: formato estável e conhecido; motivo da recusa legível por máquina (qual regra, qual campo); nenhum detalhe interno exposto.

## Alternativas descartadas
- **Formato próprio (ex.: `codigo`, `mensagem`, `campos`):** resolve o problema, mas cada consumidor aprende um formato só deste serviço, e cada serviço do banco tende a inventar o seu.
- **Manter o corpo de erro padrão do Spring:** traz `timestamp`, `status`, `error` e `path`, mas a mensagem vem vazia por padrão e não há campo para o motivo de negócio; o consumidor não sabe o que corrigir.

## Consequências
- **Ganhos:** padrão aberto, suportado nativamente pelo Spring; o consumidor trata a recusa pelo `type` sem interpretar texto.
- **Custos:** a mudança no corpo de erro também precisa ser comunicada aos consumidores (risco 3 da RFC).
- **Passa a ser obrigatório:**
  - erro de validação e de regra de negócio responde 400 com um `type` estável por motivo;
  - erro inesperado responde 500 sem stack trace nem mensagem interna;
  - os `type` possíveis ficam documentados no contrato da API.
