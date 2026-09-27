---
status: proposto
---
# ADR-0010: Descrever a infraestrutura em Terraform com estado remoto no S3

**Decisão:** toda a infraestrutura AWS do serviço é escrita em **Terraform**, com **estado remoto num bucket S3 com trava nativa**, porque é a ferramenta de IaC (infraestrutura como código) mais difundida no mercado e o plano de cada mudança fica visível no pull request antes de aplicar.

## Contexto
A infraestrutura da [seção 6.3 da RFC](../rfc/0001-modernizacao-gerador-nota-fiscal.md#63-infraestrutura-aws) tem rede, ECS, borda e observabilidade, repetidos em três ambientes. Criada à mão, ela diverge entre ambientes e não tem histórico. Trocar de ferramenta depois significa reescrever e reimportar tudo.

O que pesa: mudança revisada antes de aplicar; mesmos módulos nos três ambientes; ferramenta conhecida pelo mercado; nenhuma execução concorrente alterando a mesma infraestrutura.

## Alternativas descartadas
- **CloudFormation:** nativo da AWS, com estado gerenciado pela própria AWS, mas em YAML ou JSON verboso, com reutilização mais pobre entre ambientes e sem uso fora da AWS.
- **AWS CDK:** escreve a infraestrutura em linguagem de programação (TypeScript, Java) e gera CloudFormation. É poderoso para quem já é fluente, mas o que vai para produção é o CloudFormation gerado, que é mais difícil de revisar num PR do que um plano do Terraform.
- **Estado do Terraform local ou no Terraform Cloud:** o estado local não é compartilhado e se perde com a máquina; o Terraform Cloud acrescenta um serviço externo e custo para um ganho que o S3 já entrega.

## Consequências
- **Ganhos:** o `plan` fica visível no PR, os mesmos módulos servem os três ambientes, e o histórico da infraestrutura fica no git.
- **Custos:** o estado é um arquivo sensível que precisa de bucket criptografado, versionado e com acesso restrito.
- **Passa a ser obrigatório:**
  - nenhuma mudança manual no console;
  - `fmt`, `validate` e `plan` em todo PR que toque a infraestrutura;
  - `apply` só depois do merge, e com aprovação em produção.
