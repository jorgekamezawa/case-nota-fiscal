---
status: proposto
---
# ADR-0002: Usar virtual threads para as esperas de I/O

**Decisão:** atender requisições e chamadas às integrações com **virtual threads** (threads leves da JVM, criadas aos milhares sem custo relevante), porque o serviço passa quase todo o tempo esperando I/O e uma espera deixa de prender uma thread do sistema operacional.

## Contexto
Cada requisição espera o registro (0,5 s) e, hoje, as integrações (até 5,35 s). No modelo tradicional, cada requisição ocupa uma thread do sistema operacional durante toda a espera; o pool padrão do servidor (Tomcat) tem 200 threads, então a capacidade fica limitada ao número de requisições esperando ao mesmo tempo ([RFC-0001, D-05](../rfc/0001-modernizacao-gerador-nota-fiscal.md#3-diagnóstico)). O Java 21 ([ADR-0001](0001-java-21-e-spring-boot-com-maior-suporte.md)) traz virtual threads, e o Spring Boot as habilita por configuração.

No Java 21 há duas limitações conhecidas, resolvidas no JDK 24 pela [JEP 491](https://openjdk.org/jeps/491) e portanto no Java 25 LTS:
- **Pinning com `synchronized`:** uma virtual thread que bloqueia dentro de `synchronized` não libera a thread do sistema operacional que a executa. Como há cerca de uma por núcleo de CPU, poucas presas ao mesmo tempo podem travar a aplicação ([deadlock relatado com Postgres](https://news.ycombinator.com/item?id=39008026)).
- **Bibliotecas com `synchronized` em ponto crítico:** o HikariCP (pool de conexões) aguardou a correção da JVM em vez de mudar o próprio código ([PR #2055](https://github.com/brettwooldridge/HikariCP/pull/2055)).

## Alternativas descartadas
- **Pool de threads tradicional com mais threads:** cada thread do sistema operacional custa memória, e o número necessário cresce com o tempo de espera; aumentar o pool só adia o gargalo.
- **Programação reativa (WebFlux):** resolve a espera sem bloquear, mas troca o modelo de programação inteiro (fluxos assíncronos em vez de código sequencial), dificulta depuração e teste, e exige drivers reativos em todas as integrações.

## Consequências
- **Ganhos:** esperas simuladas e de banco não limitam a capacidade do servidor; o código continua sequencial e simples de ler.
- **Custos:** no Java 21, risco de pinning em código com `synchronized`, próprio ou de bibliotecas. Não há ganho para trabalho de CPU, só para espera de I/O.
- **Passa a ser obrigatório:**
  - nenhum bloqueio dentro de `synchronized` no código do serviço; usar `ReentrantLock` quando houver trava, e não usar `@Synchronized` do Lombok;
  - monitorar o evento `jdk.VirtualThreadPinned` do JFR nos testes de carga e ativar `-Djdk.tracePinnedThreads` em desenvolvimento;
  - limitar explicitamente a concorrência, porque virtual threads não limitam: tamanho do pool de conexões e limite de chamadas simultâneas por integração.
