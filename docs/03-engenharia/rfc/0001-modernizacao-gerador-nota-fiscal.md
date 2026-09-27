# RFC-0001: Modernização do gerador de nota fiscal

| | |
|---|---|
| **Autor** | Jorge Kamezawa |
| **Status** | Em revisão |
| **Revisores** | Tech Lead, Arquitetura, Segurança da Informação, Plataforma / SRE |
| **Aprovadores** | *a preencher na aprovação* |
| **Condições da aprovação** | *a preencher na aprovação* |
| **Regras de negócio** | [Levantamento de regras de negócio](../../01-levantamento/levantamento-regras-negocio.md) |
| **Diagnóstico detalhado** | [Diagnóstico técnico](../../01-levantamento/diagnostico-tecnico.md) |
| **Demanda** | [demanda.md](../../00-demanda/demanda.md) |
| **ADRs** | [0001 Java e Spring Boot](../adr/0001-java-21-e-spring-boot-com-maior-suporte.md) · [0002 Virtual threads](../adr/0002-virtual-threads-para-esperas-de-io.md) · [0003 Hexagonal](../adr/0003-arquitetura-hexagonal-enxuta.md) · [0004 ECS Fargate](../adr/0004-computacao-em-ecs-fargate.md) · [0005 Autenticação](../adr/0005-autenticacao-oauth2-client-credentials.md) · [0006 Borda](../adr/0006-borda-com-api-gateway-rest.md) · [0007 Observabilidade](../adr/0007-observabilidade-com-opentelemetry.md) · [0008 Deploy](../adr/0008-deploy-canary-com-rollback-automatico.md) · [0009 Erros](../adr/0009-erros-no-formato-problem-details.md) · [0010 Terraform](../adr/0010-infraestrutura-com-terraform.md) · [0011 Grafana Cloud](../adr/0011-telemetria-no-grafana-cloud.md) |

## 1. Resumo executivo

**Problema.** O gerador de notas fiscais tem defeitos que já atingem os consumidores:
- **Dados de um pedido aparecem na nota de outro:** os itens se acumulam entre requisições, com risco de LGPD.
- **Notas saem com valores errados ou sem itens,** e mesmo assim são respondidas como sucesso.
- **O tempo de resposta sobe de 1,5 s para 6,5 s** depois de poucas execuções e só volta ao normal reiniciando.
- **Nada disso é visível para o time:** não há logs, métricas nem alertas, e a suíte de testes está quebrada.

**Causa.** Estado compartilhado entre requisições, regras concentradas numa única classe, integrações executadas em série antes da resposta e ausência de validação da entrada, sobre uma versão do Spring Boot sem suporte desde 2022 (seção 3).

**Caminhos avaliados.**
- **Só corrigir os defeitos críticos e parar:** o mais rápido, mas mantém a versão sem suporte, a falta de visibilidade e a classe que muda a cada regra; o próximo defeito seria descoberto pelos consumidores de novo.
- **Reescrever do zero:** permite o desenho ideal, mas congela as correções urgentes até o fim e troca defeitos conhecidos por desconhecidos num serviço fiscal.
- **Proposto, evolução em 7 fases** (seção 7): corrige primeiro o que atinge os consumidores, na versão atual, e depois moderniza, isola as regras, instrumenta, torna confiável, protege e automatiza a entrega, cada fase entregue e validada separadamente.

**Principais decisões.** ECS Fargate com deploy canary e rollback automático; autenticação OAuth2 validada na borda e no serviço; observabilidade em OpenTelemetry. Cada decisão tem um ADR com as alternativas descartadas.

**O que pedimos.**
- Aprovação da direção geral, dos ADRs e das **fases 1 a 4 e 6 a 7**, para iniciar as specs da fase 1.
- A **fase 5** (confiabilidade) é aprovada num segundo momento, junto com a seção 6.2, após as respostas do PO.
- Acionamento de Segurança da Informação e Fiscal sobre os riscos 1 e 2 (seção 8).

## 2. Contexto

**O que o serviço faz.** Recebe um pedido em `POST /api/pedido/gerarNotaFiscal`, calcula o tributo de cada item e o frete, emite a nota fiscal e aciona quatro sistemas: estoque, registro, entrega e financeiro. As regras vigentes estão no [levantamento](../../01-levantamento/levantamento-regras-negocio.md).

**Quem consome.** Outros sistemas (P-02), que enviam o pedido e usam a nota devolvida.

**Como funciona hoje.**
```mermaid
sequenceDiagram
    participant C as Consumidor
    participant S as Gerador de nota
    participant E as Estoque
    participant R as Registro
    participant En as Entrega
    participant F as Financeiro
    C->>S: pedido
    S->>S: calcula tributo e frete
    S->>E: baixa (0,38s)
    S->>R: registro (0,5s)
    S->>En: agendamento (0,35s ou 5,35s)
    S->>F: contas a receber (0,25s)
    S-->>C: nota (1,5s a 6,5s)
```

**Estado técnico.** Java 11 e Spring Boot 2.6.2, sem banco de dados, sem log, métrica ou health check, com a suíte de testes quebrada.

**Dependências de negócio que afetam este desenho.** Tudo o que depende de persistência, processamento em segundo plano ou idempotência está marcado como pendente da 6.2.
| Pergunta | Hipótese adotada | Se a resposta for outra |
|---|---|---|
| Q-09 (reenvio do mesmo pedido) e Q-12 (por quanto tempo) | Devolver a nota já emitida | Muda o desenho de idempotência |
| Q-10 (sistemas concluídos antes da resposta) e Q-11 (falha após a emissão) | Responder após o registro e acionar os outros 3 em seguida, com nova tentativa e alerta | A seção 6.2 é refeita, e não há processamento em segundo plano |

## 3. Diagnóstico

Resumo dos defeitos. Causa, evidência e impacto de cada um estão no [diagnóstico técnico](../../01-levantamento/diagnostico-tecnico.md), obtido executando o código original. **Informado** = listado na demanda; **Identificado** = encontrado na análise.

| ID | Defeito | Origem | Impacto |
|---|---|---|---|
| **Crítica** | | | |
| D-01 | Itens acumulados entre requisições | Informado | Nota com itens de outros pedidos; dados de um cliente expostos a outro (LGPD) |
| D-02 | Latência crescente após execuções sucessivas | Informado | De 1,5 s para 6,5 s para todos, até reiniciar |
| D-03 | Condição de corrida na lista compartilhada | Identificado | Perda silenciosa de 2% a 22% dos itens sob concorrência |
| D-04 | PJ sem regra de tributação gera nota sem itens (Q-02) | Identificado | Documento fiscal vazio aceito como sucesso |
| **Alta** | | | |
| D-05 | Integrações sequenciais | Informado | Latência é a soma das integrações; cada requisição prende uma thread durante as esperas |
| D-06 | Suíte de testes quebrada e dependente de ordem | Informado | Nenhuma proteção contra regressão |
| D-07 | Falha parcial sem tratamento | Identificado | Estoque baixado sem nota registrada |
| D-08 | Sem idempotência | Identificado | Reenvio duplica a nota e os acionamentos |
| D-09 | Total declarado não é conferido (Q-01) | Identificado | Tributo calculado a partir de dado controlado pelo cliente |
| D-10 | Entrada sem validação (Q-07) | Identificado | Erro do cliente vira 500; dado inválido vira nota |
| D-11 | Frete inconsistente sem endereço de entrega (Q-05) | Identificado | Frete perdido em silêncio, ou erro 500 |
| D-12 | Valores monetários em `double` (Q-08) | Identificado | Valores sem padrão e diferenças de centavos |
| D-13 | Sem observabilidade | Identificado | Problemas só percebidos pelos consumidores |
| **Média** | | | |
| D-14 | Alta complexidade e concentração de responsabilidades | Informado | Toda regra nova altera a mesma classe |
| D-15 | Spring Boot fora de suporte | Identificado | Sem correções de segurança no framework |
| **Baixa** | | | |
| D-16 | Campos do endereço descartados na resposta | Identificado | Dado de entrada perdido; não quebra contrato |
| D-17 | Data da nota sem fuso horário | Identificado | Horário ambíguo para os consumidores |
| D-18 | Menores (injeção por campo, `new` nas integrações, nomes) | Identificado | Dificulta testar e manter |

**Não é defeito, precisa de decisão do Fiscal:** o tributo incide sobre o valor unitário (Q-03).

## 4. Objetivos, critérios de sucesso e escopo

### 4.1 Objetivos e como provar

Cada critério é provado com evidência reproduzível (comando e saída real). Os planos de teste detalhados ficam nas specs de cada fase.

| Objetivo | Critério de sucesso | Como provar |
|---|---|---|
| **O-01. Defeitos corrigidos** (D-01 a D-13 e D-16 a D-18) | Todo defeito com comportamento reproduzível tem teste que falhava antes e passa depois (D-06 se prova pela suíte verde; D-13 pela instrumentação). Com 150 chamadas simultâneas, cada resposta traz só os itens do próprio pedido, sem perda. | Teste vermelho antes do conserto; testes manuais e de concorrência do diagnóstico reexecutados |
| **O-02. Latência independente do pedido** *(depende da Q-10)* | p95 < 800 ms com 1 linha de item e com 6 ou mais (hoje 1,5 s a 6,5 s); a espera simulada da entrega continua em cerca de 5,35 s, fora da resposta | Teste de carga local, cada cenário rodado duas vezes nas mesmas condições; tempos no trace |
| **O-03. Nada perdido nem duplicado** *(depende da Q-09 e da Q-10)* | Queda da aplicação no meio do processamento não perde integração; reenvio devolve a mesma nota sem acionar os sistemas de novo | Testes de integração que interrompem o processamento e reenviam o pedido |
| **O-04. Fácil de evoluir** | Regra de tributação nova é código novo, sem alterar as existentes; testes unitários sem as esperas simuladas | PR de exemplo com uma regra fictícia |
| **O-05. Modernização** | Java 21 e Spring Boot 4.1 com teste de contrato verde antes e depois ([ADR-0001](../adr/0001-java-21-e-spring-boot-com-maior-suporte.md)) | Pipeline da fase 2 |
| **O-06. Pronto para produção** | Falha simulada (entrega fora do ar) aparece em alerta; nota seguida do log ao trace; pipeline com quality gate e `terraform validate` verdes | Execução local e do pipeline |
| **O-07. Compatibilidade de contrato** | Entrada idêntica; sucesso continua 200 com os mesmos campos; entrada inválida passa a 400 no formato do [ADR-0009](../adr/0009-erros-no-formato-problem-details.md) | Teste de contrato em todas as fases |

### 4.2 Fora do escopo

- **Regras de negócio:** seguem as decisões do PO no levantamento; esta RFC não define regra.
- **Sistemas externos reais:** estoque, registro, entrega e financeiro continuam simulados, com as mesmas esperas.
- **Emissão fiscal oficial:** XML da NF-e e integração com a SEFAZ.
- **Produção real:** o deploy contínuo para produção é descrito, sem execução; a infraestrutura é validada num ambiente de demonstração efêmero (fase 7).
- **Nova versão da API** (`/v2`) e **multi-região**.

## 5. Premissas e restrições

### 5.1 Restrições (definidas pela demanda)
- **R-01.** Contrato de entrada imutável: mesmo endpoint, campos e formato (`snake_case`).
- **R-02.** Esperas simuladas mantidas, incluindo a regra de 6 linhas de item ou mais na entrega.
- **R-03.** Java 21 e Spring Boot estável mais recente, com cada recurso novo justificado.
- **R-04.** Resposta de sucesso compatível: 200 e os mesmos campos.

### 5.2 Premissas
- **P-01.** `id_pedido` identifica o pedido na origem; a unicidade não é garantida pelo contrato (Q-09).
- **P-02.** Os consumidores são sistemas (demanda: "sistemas consumidores"); se internos ou externos, não foi informado.
- **P-03.** O ambiente de produção é AWS (demanda).
- **P-04.** O volume não foi informado; o dimensionamento é revisado com dados de produção (T-04).
- **P-05.** Estoque, registro, entrega e financeiro estão na mesma conta AWS e têm contratos de API definidos.

## 6. Arquitetura proposta

### 6.1 Aplicação

**Estilo:** hexagonal enxuta, com domínio, aplicação e adaptadores separados por pacotes ([ADR-0003](../adr/0003-arquitetura-hexagonal-enxuta.md), com a estrutura de pacotes). O domínio não conhece HTTP, banco nem integrações.

**Intenção de design.**
- Cada regra de tributação isolada por tipo de pessoa e regime: regra nova não altera as existentes (O-04).
- Valores monetários exatos, com uma política única de arredondamento conforme a decisão do negócio (Q-08).
- Domínio imutável e nenhum estado compartilhado entre requisições, o que elimina a classe de defeito de D-01 e D-03.

**Fluxo de uma requisição** (a persistência e o acionamento das integrações seguem a hipótese da seção 2).
```mermaid
flowchart LR
    C[Consumidor] -->|POST /api/pedido/gerarNotaFiscal| W[adapter.in.web]
    W -->|DTO para domínio| U[application.usecase]
    U --> T[domain.tributacao]
    U --> F[domain.frete]
    U -->|porta, síncrono| R[Registro]
    U -->|porta| P[(Persistência, pendente 6.2)]
    U -->|nota| W
    W -->|200 + nota| C
```

**Recursos do Java 21 e o benefício de cada um.**
- **Records:** DTOs e domínio imutáveis, sem estado compartilhado (D-01, D-03).
- **Switch com pattern matching:** a escolha da regra por tipo de pessoa e regime é exaustiva; o compilador acusa caso novo sem tratamento (D-04).
- **Virtual threads:** esperas de I/O não prendem threads do servidor (D-05, [ADR-0002](../adr/0002-virtual-threads-para-esperas-de-io.md)).

### 6.2 Consistência e integrações

Pendente das respostas do PO às perguntas Q-09 a Q-13 do levantamento. Esta seção e as decisões de persistência, idempotência e entrega das integrações serão escritas após essas definições.

Independente das respostas, a persistência precisará definir:
- **Dados pessoais (LGPD):** classificação dos dados guardados (CPF, CNPJ, endereço), quem acessa, criptografia e retenção, respeitando o prazo legal de guarda do documento fiscal (Q-13).
- **Auditoria:** cada nota registra o `client_id` do sistema que a emitiu ([ADR-0005](../adr/0005-autenticacao-oauth2-client-credentials.md)).
- **Recuperação:** backup e restauração conforme o RTO e o RPO definidos (T-06).

### 6.3 Infraestrutura AWS

![Arquitetura AWS](../diagramas/arquitetura-aws.png)

**Contas:** uma conta AWS por ambiente (desenvolvimento, homologação, produção), sob AWS Organizations.

**Caminho de uma requisição.**
1. **Borda:** API Management corporativo, se existir (T-01); senão, API Gateway REST com WAF, validação do JWT e limite por consumidor ([ADR-0005](../adr/0005-autenticacao-oauth2-client-credentials.md), [ADR-0006](../adr/0006-borda-com-api-gateway-rest.md)).
2. **VPC Link e ALB interno:** o gateway alcança a rede privada sem expor o balanceador.
3. **ECS Fargate** ([ADR-0004](../adr/0004-computacao-em-ecs-fargate.md)): no mínimo 2 tarefas em AZs diferentes, escala automática, collector OpenTelemetry como sidecar, enviando a telemetria para o Grafana Cloud ([ADR-0007](../adr/0007-observabilidade-com-opentelemetry.md), [ADR-0011](../adr/0011-telemetria-no-grafana-cloud.md)).
4. **Registro:** chamado de forma síncrona, antes da resposta.
5. **Persistência:** sub-rede isolada, sem rota para a internet; tipo e alta disponibilidade pendentes (6.2).

**Rede.**
- VPC com 2 AZs e três camadas de sub-rede: pública (NAT Gateway), privada (ALB e ECS) e isolada (persistência).
- **Saída:** NAT Gateway por AZ para destinos fora da AWS: o Grafana Cloud e, se não for alcançável por rede privada, a chave pública do IdP (T-02).
- VPC endpoints para ECR, CloudWatch Logs e Secrets Manager: esse tráfego não passa pelo NAT.
- Integrações: os sistemas estão na mesma conta (P-05), alcançados pela rede privada.

**Segurança.**
- Security groups em cadeia: só o ALB fala com o ECS, e só o ECS fala com a persistência.
- Segredos no Secrets Manager; nada fixo em variável ou código.
- Criptografia com KMS em repouso; TLS em todo o tráfego.
- Papel IAM da tarefa com privilégio mínimo.
- Imagens no ECR com varredura de vulnerabilidades a cada push.

**Custo estimado.** Ordem de grandeza mensal em produção, preços on-demand de referência (us-east-1), sem a persistência (pendente) e sem descontos corporativos; validar na AWS Pricing Calculator para a região de uso.
| Item | Base | US$/mês |
|---|---|---|
| ECS Fargate | 2 tarefas de 1 vCPU e 2 GB, 24x7 | ~72 |
| NAT Gateway | 1 por AZ, sem o tráfego processado | ~66 |
| VPC endpoints | 4 de interface (ECR API, ECR Docker, Logs, Secrets) em 2 AZs | ~58 |
| ALB | 1, com baixo uso | ~22 |
| WAF | 1 ACL com regras gerenciadas | ~10 |
| **Fixo** | | **~230** |
| Grafana Cloud | plano gratuito (10 mil séries, 50 GB de logs e 50 GB de traces) | 0 |
| Variáveis | API Gateway (US$ 3,50 por milhão), tráfego no NAT, logs do CloudWatch | conforme volume (T-04) |

NAT e endpoints são metade do custo fixo; com volume baixo, um NAT só (numa AZ) corta cerca de US$ 33, ao custo de a saída depender de uma zona.

**Ambiente de demonstração (fase 7):** os mesmos módulos com 1 NAT, sem VPC endpoints e 1 tarefa, ligado só durante o teste (cerca de US$ 0,30 por hora de custo fixo) e destruído ao final.

### 6.4 Observabilidade e SLOs

Padrão técnico no [ADR-0007](../adr/0007-observabilidade-com-opentelemetry.md) e destino no Grafana Cloud ([ADR-0011](../adr/0011-telemetria-no-grafana-cloud.md)). As métricas de negócio, os alertas e os dashboards são detalhados na spec da fase 4.

**Health checks** (Actuator): liveness faz o ECS substituir a tarefa que falha; readiness inclui as dependências obrigatórias (persistência, se confirmada na 6.2), e enquanto falhar o ALB não envia tráfego.

**SLIs e SLOs iniciais** (revistos após 30 dias de dados reais)
| SLI | Como mede | SLO inicial |
|---|---|---|
| Disponibilidade | % de requisições válidas sem 5xx | 99,9% em 30 dias (cerca de 43 min de orçamento de erro) |
| Latência | p95 e p99 do tempo de resposta | p95 < 800 ms, p99 < 1,5 s (*Q-10*) |
| Conclusão das integrações | % concluídas em até 5 min após a emissão | 99% (*Q-10*) |

Erros 4xx (entrada inválida) não contam contra a disponibilidade. Os alertas seguem a queima do orçamento de erro (burn rate), no modelo do [Google SRE Workbook](https://sre.google/workbook/alerting-on-slos/), e ficam no Grafana; os alarmes do rollback do canary ficam no CloudWatch ([ADR-0008](../adr/0008-deploy-canary-com-rollback-automatico.md)).

### 6.5 Entrega

Pipeline no GitHub Actions, com autenticação na AWS por OIDC (credencial temporária por execução, sem chave guardada). Todo PR passa por testes, teste de arquitetura e de contrato, quality gate do SonarQube Cloud e `plan` do Terraform ([ADR-0010](../adr/0010-infraestrutura-com-terraform.md)); nenhum entra na `main` com etapa falhando. Cada merge gera uma única imagem, com a tag do commit, promovida de desenvolvimento a homologação e, com aprovação manual, a produção por canary ([ADR-0008](../adr/0008-deploy-canary-com-rollback-automatico.md)). A configuração por ambiente vem do Terraform e do Secrets Manager; perfis do Spring só no ambiente local. As etapas detalhadas do pipeline ficam na spec da fase 7.

## 7. Roadmap

Cada fase é entregue num PR próprio, com build e testes verdes. Tamanho relativo entre as fases: P (pequena), M (média), G (grande).

| Fase | Tamanho | Objetivo | Entregas | Pronto quando | Depende de |
|---|---|---|---|---|---|
| **1. Correções imediatas** | M | Parar os erros que atingem os consumidores, na versão atual | D-01 a D-04, D-06, D-09 a D-12, D-16, D-17 (relógio fixo em `America/Sao_Paulo`, mesmo formato de data) e a injeção de dependência do D-18; teste de contrato; CI com build e testes | Cada defeito tem teste que falhava antes; suíte verde | Q-01 a Q-08 |
| **2. Modernização** | P | Java 21 e Spring Boot 4.1 sem mudar comportamento (D-15) | Upgrade ([ADR-0001](../adr/0001-java-21-e-spring-boot-com-maior-suporte.md)) | Suíte e teste de contrato verdes, sem alterar os testes da fase 1 | Fase 1 |
| **3. Arquitetura** | M | Isolar as regras (D-14) | Hexagonal enxuta, regras isoladas, renomeações do D-18 | Teste de arquitetura verde; regra nova é código novo | Fase 2 |
| **4. Observabilidade** | M | Enxergar o serviço antes da mudança mais arriscada (D-13) | Health checks, OpenTelemetry, logs estruturados, métricas de negócio, `otel-lgtm` local | Uma requisição seguida do log ao trace no Grafana local | Fase 2 |
| **5. Confiabilidade** | G | Latência independente do pedido; nada perdido ou duplicado (D-05, D-07, D-08) | Conforme a seção 6.2 | Critérios de O-02 e O-03 | Fases 3 e 4; Q-09 a Q-13; aprovação da 6.2 |
| **6. Segurança** | M | Só sistemas autorizados emitem nota, sem derrubar os consumidores | Validação de JWT e escopo; Keycloak local; transição em três passos (abaixo) | Após a data de corte, chamada sem token ou sem escopo é recusada | Fase 3; T-02 |
| **7. Entrega** | M | Pipeline completo e infraestrutura em código | Sonar, Dependabot, Dockerfile, docker-compose, Terraform; ambiente de demonstração na AWS | PR bloqueado pelo quality gate; ambiente sobe, passa no smoke test e é destruído sem sobras (abaixo) | Fases 4 a 6 |

**Transição da autenticação (fase 6).**
1. **Observação:** o serviço aceita chamadas sem token, registra quais consumidores ainda não enviam token e expõe isso em métrica.
2. **Comunicação:** cada consumidor recebe credencial, instruções e uma data de corte acordada.
3. **Corte:** na data acordada, chamadas sem token ou sem escopo passam a ser recusadas.

**Destruição sem sobras (fase 7).** O ambiente de demonstração só é dado como destruído quando nada continua gerando cobrança:
- todos os recursos com uma etiqueta única, conferida numa busca por etiqueta após o `destroy`;
- grupos de log declarados no Terraform, inclusive os que a AWS criaria sozinha (ex.: Lambda);
- repositório do ECR apagado mesmo com imagens;
- segredos apagados sem janela de recuperação e chaves KMS agendadas para exclusão;
- bucket do estado do Terraform apagado por último, fora do ambiente.

**Rollback por fase.** As fases 1 a 4 e 6 não migram dados, então cada uma é revertida pelo deploy da imagem anterior, inclusive as mudanças de resposta (novos 400 e arredondamento). A fase 5 define o próprio rollback junto com a 6.2.

**Por que a fase 1 não inclui todos os defeitos Altos:** D-05, D-07, D-08 e D-13 exigem persistência, processamento assíncrono e OpenTelemetry, que dependem do upgrade; implementá-los na versão atual significaria fazê-los duas vezes.

**Por que observabilidade antes de confiabilidade:** a fase 5 é a de maior risco; instrumentar antes a torna visível desde o primeiro deploy.

## 8. Riscos e mitigações

| # | Risco | Impacto | Mitigação |
|---|---|---|---|
| 1 | Dados de um cliente em nota de outro já podem ter chegado a consumidores em produção (D-01, D-03) | Alto: possível incidente de LGPD | Acionar Segurança da Informação e o encarregado de dados (DPO); correção prioritária na fase 1 |
| 2 | Notas já emitidas com dados errados (itens acumulados, frete zerado, nota sem itens) | Alto: fiscal e contábil | Levantar as notas afetadas com Fiscal e Contabilidade; correção de dados históricos fora desta RFC |
| 3 | Consumidores quebram com as recusas novas (200 ou 500 passam a 400) e com a exigência de token | Alto | Comunicação antes de cada mudança; recusas por motivo e por consumidor em métrica; transição da autenticação (seção 7). ⚠️ O inventário dos consumidores e o dono da comunicação precisam ser definidos antes da fase 1. |
| 4 | O PO decide diferente nas perguntas Q-09 a Q-13 | Médio: a seção 6.2 muda | A 6.2 e a fase 5 são aprovadas num segundo momento |
| 5 | O upgrade altera o JSON sem aviso (padrões do Jackson 3) | Alto: quebra de contrato | Teste de contrato criado na fase 1, antes do upgrade |
| 6 | Pinning de virtual threads no Java 21 trava a aplicação sob carga | Médio | Obrigações do [ADR-0002](../adr/0002-virtual-threads-para-esperas-de-io.md) e teste de carga |
| 7 | Premissas de infraestrutura não confirmadas (seção 9) | Médio: retrabalho na borda e na infra | O serviço não depende do IdP nem da borda escolhidos (OIDC); confirmar antes das fases 6 e 7 |
| 8 | Canary com pouco tráfego não detecta o defeito | Médio | Smoke test antes do tráfego; porcentagem e tempo calibrados com o volume real |

## 9. Perguntas técnicas em aberto

As perguntas de negócio estão no [levantamento](../../01-levantamento/levantamento-regras-negocio.md).

| # | Pergunta | Afeta | Quem responde |
|---|---|---|---|
| T-01 | Existe plataforma corporativa de API Management? | [ADR-0006](../adr/0006-borda-com-api-gateway-rest.md) | Arquitetura |
| T-02 | Existe IdP corporativo que emita tokens por client credentials (OIDC)? Ele é alcançável pela rede privada? | [ADR-0005](../adr/0005-autenticacao-oauth2-client-credentials.md) e necessidade de NAT | Segurança da Informação |
| T-03 | Estoque, entrega e financeiro aceitam chave de idempotência para descartar chamadas repetidas? | Seção 6.2 | Donos dos sistemas |
| T-04 | Qual o volume esperado (requisições por segundo, média e pico)? | Escala, canary, custo e SLOs | Produto e Arquitetura |
| T-05 | A meta de 99,9% de disponibilidade está alinhada com a criticidade para os consumidores? | Seção 6.4 | SRE e consumidores |
| T-06 | Qual o RTO (tempo máximo fora do ar após falha grave) e o RPO (quantos minutos de notas se pode perder)? | Seção 6.2 (backup e recuperação) | SRE e negócio |
