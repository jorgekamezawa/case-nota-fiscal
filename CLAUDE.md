# Gerador de nota fiscal

Serviço que recebe um pedido, calcula tributo e frete, emite a nota fiscal e aciona registro, estoque, entrega e financeiro.

## Documentos
- `docs/00-demanda`: escopo e restrições.
- `docs/01-levantamento`: regras vigentes e decisões do PO (Q-01 a Q-16).
- `docs/02-produto`: épico e entregáveis.
- `docs/03-engenharia`: RFC, ADRs, spikes e diagramas.
- `docs/04-specs/<entregável>/`: `spec.md` (parte funcional do PO e não funcional do time) e `tasks.md`.
- `docs/api`: contrato da API (catálogo de erros).

## Regras
- Regra de negócio vive só na spec; RFC e ADR não definem regra.
- Tasks por camada (back, infra), com classes e pacotes, sem código; citam o ID da regra, não a repetem. A última task é de QA (rastreabilidade).
- Documentos em português do Brasil, escritos como demanda do time; sem travessão nem meia-risca; sem datas no planejamento (fases).
- Commits no padrão `tipo: descrição` em português (ex.: `docs: ...`, `fix: ...`).
- Java pelo sdkman (`.sdkmanrc`).

## Ambiente local
- `docker compose up -d` sobe o Grafana local (`otel-lgtm`) em http://localhost:3000, com o dashboard e os alertas de `observabilidade/grafana`, o DynamoDB Local (porta 8000) e o ElasticMQ, compatível com o SQS (porta 9324, sem painel web).
- `./mvnw spring-boot:run -Dspring-boot.run.profiles=local` roda a aplicação com log em texto, envio da telemetria ao Grafana local e tabelas e filas criadas na subida. Sem Pipe no local, a reconciliação leva as tarefas às filas em cerca de 10 segundos.
- `simulacao.entrega-fora-do-ar=true` no perfil local derruba a entrega, para ver o alerta da DLQ.
- Os testes sobem os próprios emuladores por Testcontainers; só precisam do Docker.
- Ao terminar: `docker compose down` (sem `-v`).
