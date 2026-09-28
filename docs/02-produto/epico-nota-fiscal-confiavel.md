# Épico: nota fiscal confiável

| | |
|---|---|
| **Autor** | PO |
| **Status** | Em revisão |
| **Demanda** | [demanda.md](../00-demanda/demanda.md) |
| **Regras de negócio** | [Levantamento](../01-levantamento/levantamento-regras-negocio.md) |
| **Plano técnico** | [RFC-0001](../03-engenharia/rfc/0001-modernizacao-gerador-nota-fiscal.md) |

## 1. Objetivo
Quem pede uma nota recebe uma nota correta, só com os dados do próprio pedido e sem esperar os sistemas acionados depois. Registro, estoque, entrega e financeiro recebem cada nota uma única vez.

## 2. Problema
- Itens de um pedido aparecem na nota de outro (risco de LGPD).
- Notas saem com valores errados, sem itens ou com frete zerado, e são respondidas como sucesso.
- A resposta leva até 6,5 s e piora após execuções sucessivas, até reiniciar.
- Um reenvio duplica a nota, a baixa de estoque e a cobrança.

## 3. Como o épico é aceito
- Toda nota segue as decisões Q-01 a Q-16 do levantamento.
- Pedido inválido é recusado com todos os motivos da etapa de validação em que parou; nunca vira nota nem erro interno.
- Quem pede a nota não espera registro, estoque, entrega e financeiro.
- Nenhum acionamento se perde ou se duplica; falha persistente chega à operação e ao dono do sistema no mesmo dia.

## 4. Entregáveis
Cada entregável tem uma spec em `docs/04-specs`. A parte funcional é escrita quando a fase dele começa, exceto no E-05, que não tem parte funcional.

| ID | Entregável | Resultado para o negócio | Decisões | Fase |
|---|---|---|---|---|
| E-01 | Nota correta ou recusa clara | Pedido incompleto ou incoerente é recusado com os motivos agrupados por etapa de validação, sem expor dado pessoal; pedido válido gera nota com tributo, frete e totais certos, com 2 casas, só com os itens do próprio pedido | RN-01 a RN-06, Q-01 a Q-08; demanda: itens acumulados entre execuções | 1 |
| E-02 | Resposta sem esperar os sistemas acionados | A nota é devolvida assim que gravada; os quatro sistemas são acionados depois, com nova tentativa sem duplicar, alerta no mesmo dia e reprocessamento só da etapa que falhou, sem emitir outra nota | RN-07, Q-10, Q-11 | 5 |
| E-03 | Reenvio devolve a mesma nota | Reenvio não duplica nota nem acionamentos; conteúdo diferente com o mesmo pedido é recusado | Q-09, Q-12, Q-14, Q-15 | 5 |
| E-04 | Guarda das notas por 5 anos | Notas guardadas pelo prazo fiscal e apagadas depois | Q-13, Q-16 | 5 |
| E-05 | Só sistemas autorizados emitem nota | Emissão restrita a quem tem credencial, sem derrubar os consumidores atuais | Demanda: autenticação e autorização | 6 |

**Habilitadores técnicos** (sem regra de negócio; spec escrita pelo time):
| Fase | Para que serve |
|---|---|
| 2 | Versões com suporte de Java e Spring Boot, sem mudar comportamento |
| 3 | Regras isoladas: regra nova sem alterar as existentes |
| 4 | Enxergar o serviço (logs, métricas e rastreio do caminho de cada requisição) antes da fase 5 |
| 7 | Entrega automatizada e infraestrutura em código |

## 5. Fora do escopo
- **Emissão fiscal oficial** (XML da NF-e e SEFAZ).
- **Notas já emitidas com erro:** o Fiscal avalia se precisam de correção.
- **Possível exposição de dados entre clientes:** o DPO avalia se houve incidente a comunicar.
- **Sistemas acionados reais:** registro, estoque, entrega e financeiro continuam simulados, com as mesmas esperas.
- **Mudanças no pedido de entrada:** o contrato de entrada não muda.

## 6. Premissas
Se uma premissa cair, as decisões que dependem dela são revistas.
- O `id_pedido` é único no geral e nunca é reutilizado (Q-09).
- Toda venda deste fluxo tem entrega física (Q-05).
- O prazo de guarda conta a partir de 1º de janeiro do ano seguinte à emissão (Q-13, leitura conservadora do CTN, art. 173).
- Os consumidores foram avisados de que pedidos antes aceitos passam a ser recusados e de que os valores de tributo e frete mudam (Q-01, Q-02, Q-03, Q-05, Q-07, Q-08).
