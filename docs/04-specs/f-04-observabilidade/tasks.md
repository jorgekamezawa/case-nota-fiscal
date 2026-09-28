# Tasks F-04: observabilidade

Spec: [spec.md](spec.md). Base `br.com.itau.geradornotafiscal` (abreviado `p`). Cada task termina com `./mvnw -B clean verify` verde, sem aviso novo, e a saída no PR. Nos testes, tracing e métricas do Boot vêm desligados; os testes que conferem telemetria os religam pela anotação do Spring Boot Starter OpenTelemetry Test.

## Back

### T-01. Saúde
- **Cobre:** F04-NF-01.
- **Dependência:** Spring Boot Starter Actuator.
- **Configuração:** `application.properties` expõe só `health`, com as probes de vida (liveness) e prontidão (readiness) ligadas fora do Kubernetes e sem indicador de sistema externo; a página de descoberta `/actuator` desligada.
- **Testes:** `p.SaudeTest`: vida e prontidão respondem `UP`; `/actuator` e outro endpoint de gestão respondem 404.

### T-02. Traces e métricas
- **Cobre:** F04-NF-02, F04-NF-05 (spans), F04-NF-06, F04-NF-07, F04-NF-09.
- **Dependência:** Spring Boot Starter OpenTelemetry; de teste, Spring Boot Starter OpenTelemetry Test e OpenTelemetry SDK Testing.
- **Configuração:**
  - amostragem de 100%;
  - histograma e limites de 800 ms e 1,5 s na duração das requisições (base dos alertas da T-05);
  - exportação OTLP de traces, métricas e logs desligada no padrão (a de métricas vem ligada no Boot e é desligada explicitamente) e ligada no `application-local.properties`, que aponta para o `otel-lgtm`. Na fase 7 o destino vem da configuração do ambiente.
- **Classes:**
  - `p.adapter.out.estoque`, `.registro`, `.entrega` e `.financeiro`: cada `*Adapter` envolve a chamada numa observação `integracao`, com rótulo `sistema`, que gera o span e a métrica de duração. As linhas com as esperas não mudam;
  - `p.adapter.in.web.controller.GeradorNFController`: conta `notas.emitidas`;
  - `p.adapter.in.web.handler.TratadorDeErros`: conta `recusas` em cada resposta 400, uma vez por `type` distinto dos campos; sem campos (`json-invalido`), com o `type` geral;
  - `p.config.ExcecaoSemMensagemNoSpanConfig`: filtro de observação que troca o erro registrado no span por um com o mesmo tipo e sem mensagem (F04-NF-05).
- **Testes:**
  - `traceparent` recebido é continuado; um span por integração; erro marcado no span com a integração falhando por mock do `EntregaAgendamentoCliente`;
  - span com erro não traz a mensagem da exceção;
  - métricas HTTP por endpoint e status e métricas da JVM presentes (F04-NF-06);
  - contadores e rótulos sem `id_pedido`;
  - com a exportação ligada e destino inexistente, a resposta continua 200 (F04-NF-09).

### T-03. Logs
- **Cobre:** F04-NF-03, F04-NF-04, F04-NF-05 (logs), F04-NF-09.
- **Dependência:** OpenTelemetry Logback Appender `2.28.1-alpha`.
- **Configuração:**
  - `src/main/resources/logback-spring.xml`: o console usa o JSON estruturado nativo do Boot; o perfil `local` usa texto legível. Os logs também saem por OTLP;
  - `application.properties`: no JSON, as chaves `traceId` e `spanId` do Micrometer renomeadas para `trace_id` e `span_id`;
  - appender OTLP copiando o contexto do log e os pares chave-valor, para `id_pedido` e o identificador da nota chegarem como campos.
- **Classes:**
  - `p.config.MascaraDadosPessoais`: a regra única, que troca sequências de 11 e 14 dígitos (com ou sem pontuação);
  - `p.config.AppenderMascarado`: o ponto central. Aceita appenders dentro dele (é por aí que a instalação do appender do OpenTelemetry o encontra). Mascara a mensagem e entrega ao console e ao OTLP uma cópia da exceção com as mensagens mascaradas, inclusive as das causas;
  - `p.config.LogsOpenTelemetryConfig`: liga o appender do OpenTelemetry ao SDK do Boot;
  - `GeradorNFController` loga a nota emitida; `TratadorDeErros` loga cada recusa, inclusive `json-invalido` (F04-NF-04).
- **Testes:**
  - `MascaraDadosPessoaisTest`: CPF e CNPJ, com e sem pontuação;
  - `p.DadosPessoaisForaDaTelemetriaTest`: pedido válido, recusado na etapa 1 e recusado na etapa 2; nome, documento e logradouro ausentes do console, dos logs OTLP e dos spans; um erro logado com CPF na mensagem e na causa sai mascarado no console e no OTLP; cada linha com `trace_id` e `span_id`;
  - `p.LogPorRequisicaoTest`: nota emitida, recusa e corpo inválido geram log com os campos do F04-NF-04.

### T-04. Teste de arquitetura
- **Cobre:** F04-NF-08.
- **Classes:** `p.ArquiteturaObservabilidadeTest`, classe nova para não alterar o `ArquiteturaTest` (F04-NF-13): a aplicação não depende de `io.micrometer..`, `io.opentelemetry..` nem do Actuator. O domínio já está coberto pela regra da F-03.

## Infra

### T-05. Grafana local, dashboard e alertas
- **Cobre:** F04-NF-10, F04-NF-11, F04-NF-12.
- **Arquivos:**
  - `compose.yaml` na raiz, só com `grafana/otel-lgtm:0.34.0` (tag fixa) e as portas do Grafana e do OTLP;
  - `observabilidade/grafana/dashboards/gerador-nota-fiscal.json`, com os painéis do F04-NF-10 e a fonte de dados como variável, para o mesmo arquivo servir no local e na nuvem;
  - `observabilidade/grafana/alertas/slo.json`: burn rate em duas janelas, no modelo do SRE Workbook, para disponibilidade e latência;
  - os arquivos de provisionamento que carregam o dashboard e os alertas na imagem.

### T-06. Documentação
- `CLAUDE.md`: como subir o Grafana local e a aplicação com o perfil `local`.
- `src/CLAUDE.md`: telemetria só nos adaptadores e na configuração.
- Spec com status "Concluída".

## QA

### T-07. Rastreabilidade e evidência local
Um agente de contexto limpo confere a evidência de cada F04-NF. Também confere que nenhum teste existente mudou (F04-NF-13) e que nenhuma linha com `Thread.sleep` mudou. Para o F04-NF-12: sobe o compose e a aplicação, faz uma requisição e, pela API do Grafana, acha o log no Loki e o trace no Tempo pelo mesmo `trace_id`; depois desliga tudo. Pronto quando não há lacuna, ou quando cada lacuna tem justificativa aceita.
