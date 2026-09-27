# Desafio Técnico — Nota Fiscal

## Contexto

A aplicação responsável pelo processamento de notas fiscais apresenta atualmente uma série de desafios técnicos, funcionais e de manutenção.

O objetivo deste desafio é analisar o cenário existente, identificar as causas dos problemas e propor uma evolução da solução considerando não apenas a correção dos problemas atuais, mas também qualidade de código, arquitetura, performance, observabilidade, testes e todo o ciclo de desenvolvimento e entrega de software.

O candidato tem liberdade para aplicar quaisquer boas práticas, padrões ou melhorias que considerar pertinentes. Espera-se uma visão que vá além da simples correção dos problemas apresentados.

---

## Problemas conhecidos

### Manutenibilidade e qualidade de código

* O código atual apresenta alta complexidade, tornando alterações e evoluções difíceis de realizar com segurança.
* Existem diversas regras de cálculo e fluxos de processamento diferentes concentrados na aplicação.
* A classe responsável pelo fluxo principal sofre alterações frequentes e apresenta alto grau de instabilidade.
* A cobertura de testes é baixa.
* Parte dos testes existentes encontra-se quebrada ou apresenta comportamento inconsistente.

### Problemas funcionais

* A primeira execução de um processamento ocorre corretamente.
* A partir das execuções seguintes, ocorre um problema na devolução dos itens: dados processados anteriormente permanecem acumulados e passam a fazer parte das novas respostas.
* Existem relatos de sistemas consumidores recebendo informações inconsistentes relacionadas:

  * aos valores da nota fiscal;
  * ao valor total calculado;
  * à quantidade de itens processados.

### Problemas de performance

* Pedidos contendo mais de **6 itens** apresentam aumento significativo no tempo de processamento.
* Após sucessivas execuções da aplicação, o tempo de resposta tende a aumentar consideravelmente.
* Algumas integrações externas possuem latência propositalmente simulada para representar chamadas reais e esse comportamento faz parte do cenário do desafio.

---

# Objetivo do desafio

A solução proposta deve:

* Corrigir os problemas funcionais identificados.
* Melhorar a performance da aplicação.
* Aumentar a confiabilidade do processamento.
* Facilitar futuras alterações e inclusão de novas regras.
* Melhorar a experiência de desenvolvimento e manutenção da aplicação.
* Demonstrar boas práticas de engenharia de software durante todo o ciclo de desenvolvimento e entrega.

O candidato é livre para realizar quaisquer melhorias adicionais que considerar relevantes. A capacidade de identificar problemas não explicitamente descritos e propor soluções adequadas também será considerada na avaliação.

---

# Premissas e restrições

## Contrato da API

O **payload de entrada não deve ser modificado**.

A solução deve manter compatibilidade com o contrato atual da aplicação.

## Integrações externas

Algumas integrações possuem tempos de resposta simulados para representar chamadas externas reais.

Essas esperas fazem parte do comportamento esperado do cenário e **não devem simplesmente ser removidas** como forma de otimização.

## Modernização tecnológica

A aplicação deverá ser atualizada para:

* **Java 21**
* versão estável mais recente do **Spring / Spring Boot** compatível com a solução proposta.

Sempre que fizer sentido, o candidato poderá utilizar recursos disponíveis nas versões mais recentes do Java e do ecossistema Spring.

A utilização dessas funcionalidades deve ser justificada pelo benefício proporcionado à solução, evitando seu uso apenas por serem recursos novos.

---

# Visão arquitetural

Além das alterações no código da aplicação, espera-se uma proposta arquitetural representando o contexto no qual o sistema estaria executando em um ambiente produtivo.

O desenho deverá considerar, quando aplicável:

* API Gateway;
* autenticação e autorização;
* serviços/APIs consumidos pela aplicação;
* integrações externas;
* comunicação entre serviços;
* infraestrutura AWS;
* rede e segurança;
* escalabilidade;
* alta disponibilidade;
* observabilidade;
* persistência de dados;
* mecanismos de resiliência.

Não é necessário implementar toda a infraestrutura apresentada no desenho, mas as decisões arquiteturais deverão estar coerentes com a solução proposta.

---

# Engenharia e ciclo de entrega

A avaliação não estará restrita apenas ao código produzido.

Uma entrega de alta qualidade deve considerar o **ciclo completo de desenvolvimento e entrega de software**, incluindo aspectos como:

* organização e qualidade do código;
* arquitetura e separação de responsabilidades;
* estratégia de testes;
* tratamento de falhas;
* performance;
* concorrência;
* segurança;
* observabilidade;
* documentação;
* decisões arquiteturais e seus trade-offs;
* estratégia de build;
* integração contínua;
* estratégia de deployment;
* rollback;
* configuração por ambiente;
* monitoramento da aplicação em produção.

A profundidade aplicada em cada aspecto fica a critério do candidato.

O principal objetivo é demonstrar **como você pensa, investiga problemas, toma decisões técnicas e estrutura uma solução sustentável para produção**.
