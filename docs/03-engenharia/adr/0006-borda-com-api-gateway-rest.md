---
status: proposto
---
# ADR-0006: Usar API Gateway REST como borda, na ausência de API Management corporativo

**Decisão:** a borda é a **plataforma corporativa de API Management, se existir** (T-01); na ausência dela, **API Gateway REST API** com WAF, autorizador Lambda e limite por consumidor, porque é a única opção da AWS que valida JWT de qualquer IdP, aplica WAF e limita chamadas por consumidor ao mesmo tempo.

## Contexto
Todo pedido deve passar por uma porta de entrada que bloqueie ataques conhecidos, valide o token ([ADR-0005](0005-autenticacao-oauth2-client-credentials.md)), limite chamadas por consumidor e encaminhe para a rede privada. Não se sabe se os consumidores são internos ou externos.

O que pesa: validar JWT de um IdP genérico (OIDC); limite por consumidor, para que um consumidor com defeito não derrube os outros; WAF na entrada; alcançar o ALB interno sem expô-lo; o consumidor envia só o JWT.

## Alternativas descartadas
- **API Gateway HTTP API:** valida JWT de qualquer IdP sem código e custa 71% menos ([preços](https://aws.amazon.com/api-gateway/pricing/)), mas não aceita WAF nem limite por consumidor, só um limite geral por rota ([comparação oficial](https://docs.aws.amazon.com/apigateway/latest/developerguide/http-api-vs-rest.html)).
- **Sem gateway, com o ALB validando o JWT** ([disponível desde novembro de 2025](https://aws.amazon.com/about-aws/whats-new/2025/11/application-load-balancer-jwt-verification/)) **e WAF no ALB:** menos peças e sem custo de gateway. Mas o limite por consumidor fica restrito a regras de taxa do WAF, sem planos de uso nem cotas, e não há recursos de gestão de API (catálogo, versões).

## Consequências
- **Ganhos:** WAF e autenticação na borda, com a autenticação repetida no serviço; trocar pela plataforma corporativa não afeta o serviço.
- **Custos:** uma Lambda a mais para manter (o autorizador, com resultado em cache); custo por requisição maior que o HTTP API, relevante só com volume alto (T-04).
- **Passa a ser obrigatório:** o autorizador informa o plano de uso a partir do `client_id` do token, então o consumidor não recebe API key. Acesso ao ALB só pelo VPC Link. Endpoint regional restrito por política de acesso; se os consumidores forem internos, passa a privado.
