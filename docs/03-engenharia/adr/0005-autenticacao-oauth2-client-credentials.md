---
status: proposto
---
# ADR-0005: Autenticar consumidores com OAuth2 client credentials, validando na borda e no serviço

**Decisão:** cada sistema consumidor obtém um **JWT por OAuth2 client credentials** (troca a própria credencial por um token assinado, de validade curta, com escopo e `client_id`), e o token é **validado na borda e de novo no serviço**, porque é o padrão para serviço a serviço, permite permissão por operação e rotação sem deploy, e o serviço não confia em nada só por estar na rede interna.

## Contexto
Os consumidores são sistemas ([RFC-0001, P-02](../rfc/0001-modernizacao-gerador-nota-fiscal.md#52-premissas)), e hoje a API aceita qualquer chamada, sem saber quem chamou.

O que pesa: só sistemas autorizados emitem nota; saber qual sistema chamou (log, auditoria e, se o PO pedir, idempotência por origem); nenhum segredo fixo no código; permissão por operação (ex.: `notafiscal:emitir`); pouco impacto na latência.

## Alternativas descartadas
- **API key:** um segredo estático enviado em cada chamada, sem expiração nem escopo; vazou, vale até alguém trocar manualmente. A AWS não recomenda API key para autenticação.
- **mTLS:** cada sistema se identifica com um certificado, a identidade de máquina mais forte. Mas exige emitir, rotacionar e revogar certificados para cada consumidor, e a permissão por operação fica por conta do serviço.
- **IAM SigV4:** cada chamada é assinada com as credenciais do papel IAM do consumidor, sem segredo guardado. Só funciona se todos os consumidores rodam na AWS, e cada conta diferente exige política entre contas; com consumidores desconhecidos, é uma aposta.
- **Validar só na borda:** evita uma dependência no serviço, mas quem alcançar o serviço pela rede interna entra sem validação, e o `client_id` não chega ao serviço de forma confiável.

## Consequências
- **Ganhos:** escopo, identidade do chamador e rotação de credencial no provedor de identidade (IdP), sem deploy; a validação no serviço é local (chave pública do emissor em cache).
- **Custos:** dependência nova no serviço (Spring Security Resource Server); os consumidores passam a obter e enviar um token.
- **Passa a ser obrigatório:** o serviço só conhece o endereço do emissor (padrão OIDC), então o IdP é configuração. Qual IdP usar em produção depende da T-02 e fica em aberto (corporativo, ou Cognito na ausência dele).
