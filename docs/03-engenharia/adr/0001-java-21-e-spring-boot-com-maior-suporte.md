---
status: aceito
---
# ADR-0001: Usar Java 21 e a versão estável do Spring Boot com maior janela de suporte

**Decisão:** usar **Java 21**, versão LTS (suporte longo) definida pela arquitetura para este serviço, com o **Spring Boot 4.1.x**, porque é a versão estável com o maior prazo de suporte OSS (correções de segurança gratuitas) e atende à exigência da demanda.

## Contexto
A aplicação roda em Java 11 com Spring Boot 2.6.2, sem suporte OSS desde 2022 ([RFC-0001, D-15](../rfc/0001-modernizacao-gerador-nota-fiscal.md#3-diagnóstico)). A demanda exige Java 21 e a versão estável mais recente do Spring Boot.

O que pesa, nesta ordem:
1. atender à exigência da demanda;
2. maior prazo de suporte OSS;
3. o contrato JSON continua idêntico após a migração.

Suporte OSS por versão, segundo a [API oficial do Spring](https://api.spring.io/projects/spring-boot/generations): 3.5.x encerrado em 30/06/2026; 4.0.x até 31/12/2026; **4.1.x até 31/07/2027**; 4.2.x ainda não lançado (previsto para 30/11/2026).

## Alternativas descartadas
- **Spring Boot 3.5.x:** tem suporte estendido pago até 2032, mas o suporte OSS já terminou; correção de segurança passaria a depender de contrato comercial.
- **Spring Boot 4.0.x:** mesma base do 4.1, mas o suporte OSS acaba em 31/12/2026, o que obrigaria outra migração em poucos meses.
- **Java 25:** também LTS e suportado pelo Boot 4.1, e resolve limitações das virtual threads presentes no 21. Contraria o direcionamento da arquitetura; a troca futura é simples porque o Boot 4.1 já o suporta.

## Consequências
- **Ganhos:** suporte OSS até 31/07/2027; records, pattern matching e virtual threads disponíveis.
- **Custos:** duas quebras grandes de uma vez: `javax` para `jakarta` (Boot 3) e Jackson 2 para 3 (Boot 4), que muda padrões de serialização. `@MockBean` foi removido (substituto: `@MockitoBean`).
- **Passa a ser obrigatório:** o teste de contrato comparar o JSON antes e depois do upgrade; nenhum comportamento muda na fase de modernização. O plano da migração (salto direto ou em etapas) é decidido na spec da fase 2.
