---
status: proposto
---
# ADR-0007: Instrumentar com OpenTelemetry e coletar por um collector sidecar

**Decisão:** gerar logs, métricas e traces com **OpenTelemetry pelo starter do Spring Boot 4** e enviá-los a um **OpenTelemetry Collector como sidecar** (container auxiliar na mesma tarefa), porque é o padrão aberto de mercado e a ferramenta de destino vira configuração, sem mudar código.

## Contexto
O serviço não tem nenhum log, métrica ou health check ([RFC-0001, D-13](../rfc/0001-modernizacao-gerador-nota-fiscal.md#3-diagnóstico)), e defeitos como D-01 e D-02 só seriam percebidos pelos consumidores. Uma nota passa por mais de um sistema e precisa ser rastreável de ponta a ponta. A ferramenta de monitoramento de destino não está definida.

O que pesa: seguir uma nota de ponta a ponta; logs ligados ao trace e sem documento pessoal em claro (LGPD); trocar a ferramenta sem mudar código; custo controlado de volume de dados.

## Alternativas descartadas
- **Agente Java do OpenTelemetry:** instrumenta sozinho, alterando o bytecode na inicialização, e cobre mais bibliotecas. Em troca aumenta o tempo de subida, tem comportamento difícil de depurar e acopla a versão do agente às versões das bibliotecas.
- **Agente do fornecedor (ex.: Datadog):** o APM (monitoramento de performance da aplicação) mais completo com pouca configuração, mas trocar de ferramenta exige reinstrumentar.
- **AWS X-Ray SDK:** em manutenção desde 25/02/2026 e sem suporte a partir de 25/02/2027; a AWS recomenda migrar para OpenTelemetry ([aviso oficial](https://docs.aws.amazon.com/xray/latest/devguide/xray-daemon-eos.html)).
- **Enviar direto para a ferramenta, sem collector:** uma peça a menos, mas o serviço fica preso ao destino, perde amostragem e filtro, e descarta dados se o destino ficar indisponível.

## Consequências
- **Ganhos:** os três sinais em padrão aberto; o mesmo fluxo local e em produção; um incidente é seguido do log ao trace pelo `trace_id`.
- **Custos:** um container a mais por tarefa, com CPU e memória próprias; o que o Spring e o Micrometer não instrumentam exige instrumentação manual.
- **Passa a ser obrigatório:**
  - contexto propagado pelo cabeçalho W3C `traceparent`;
  - logs em JSON estruturado, com `trace_id` e `span_id` em cada linha;
  - documento pessoal nunca vai para log, com o mascaramento garantido num ponto central;
  - 100% dos traces com erro guardados, e a amostragem dos bem-sucedidos calibrada pela operação;
  - ambiente local com a imagem `grafana/otel-lgtm` (collector, Prometheus, Loki, Tempo e Grafana num container).

  A ferramenta de destino em produção fica em aberto.
