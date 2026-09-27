---
status: proposto
---
# ADR-0012: Persistir as notas no DynamoDB

**Decisão:** guardar as notas e as tarefas de integração no **DynamoDB**, porque o serviço tem carga de escrita predominante (write-heavy), com volume desconhecido, e consultas poucas e conhecidas, que é o cenário em que um banco chave-valor gerenciado entrega latência estável e escala sem intervenção.

## Contexto
Hoje o serviço não guarda nada. As decisões do [levantamento](../../01-levantamento/levantamento-regras-negocio.md) passam a exigir persistência. A análise completa, com o desenho das tabelas, está no [Spike-0001](../spikes/0001-persistencia-e-acionamento-das-integracoes.md).

O que pesa na escolha:
- **Escrita predominante:** cada pedido gera 1 nota e 4 tarefas, e cada tarefa é atualizada ao menos uma vez; a leitura se limita ao reenvio e à operação.
- **Volume desconhecido** (P-04), num serviço que tende a ser muito chamado.
- **Consultas poucas e conhecidas:** nota pelo `id_pedido` (Q-09, Q-12), tarefas de um pedido, tarefas pendentes (Q-11).
- **Nota e tarefas gravadas juntas,** sem gravar uma sem a outra (Q-10, Q-11).
- **Guarda por 5 anos e expurgo depois** (Q-13).
- **Poucos relacionamentos:** a nota carrega seus itens e o destinatário; as tarefas só se ligam à nota pelo `id_pedido`. Nenhuma consulta cruza tabelas.

## Alternativas descartadas
- **PostgreSQL no RDS:** o ponto forte de um banco relacional é cruzar tabelas (joins), garantir relacionamentos e responder consultas imprevistas em SQL. Aqui quase não há relacionamento e as consultas são conhecidas, então esse ganho fica sem uso. O que pesa contra:
  - toda gravação passa por uma única máquina, e escalar é trocar por uma maior, com parada;
  - na falha, a troca para a cópia de reserva deixa o serviço sem gravar por 1 a 2 minutos;
  - apagar notas de 5 anos em massa deixa espaço morto nas tabelas (bloat), o que obriga a particionar por mês;
  - conexões limitadas pelo banco, com pool dimensionado por tarefa do ECS;
  - versões e janela de manutenção para o time gerenciar.
- **MongoDB:** o modelo de documento encaixa bem, porque a nota com seus itens vira um documento só, e há transação, índice TTL e change streams (aviso quando um dado muda). O que pesa contra:
  - o esquema flexível, principal vantagem, não traz ganho para uma nota de estrutura fixa;
  - **DocumentDB** (serviço da AWS): compatível só em parte com o MongoDB, cobrado por instância mesmo sem uso, e escalar a escrita exige configurar o particionamento (sharding);
  - **Atlas** (MongoDB como serviço): roda fora da conta AWS, com outro fornecedor, contrato e avaliação de risco próprios, e a rede privada até ele precisa de configuração dedicada (PrivateLink).

## Consequências
- **Ganhos:**
  - latência de milissegundos que não cresce com o volume, porque os dados se espalham por partições pela chave;
  - capacidade sob demanda: cobra por gravação e leitura, sem dimensionar máquina;
  - replicado em várias zonas, sem troca em falha;
  - gravação condicional para impedir nota duplicada;
  - transação entre tabelas;
  - expurgo automático por TTL, sem custo de gravação;
  - acesso por papel IAM, sem senha;
  - Streams disponível para publicar cada gravação.
- **Custos:**
  - consulta nova fora da chave exige índice novo, planejado antes;
  - auditoria fora do padrão passa pela exportação para o S3;
  - limite de 400 KB por item;
  - transação custa o dobro de capacidade;
  - o modelo de dados fica preso ao DynamoDB: sair dele é reescrever a camada de persistência.
- **Passa a ser obrigatório:**
  - criptografia em repouso com chave KMS própria;
  - backup contínuo (point-in-time recovery: restauração para qualquer momento dos últimos 35 dias);
  - pool HTTP do SDK dimensionado junto com as virtual threads ([ADR-0002](0002-virtual-threads-para-esperas-de-io.md)), porque elas não limitam sozinhas quantas chamadas disputam as conexões;
  - índices com chave distribuída, para não concentrar gravações numa partição;
  - cada consulta nova mapeada para a chave ou um índice antes de entrar no código;
  - testes com o emulador oficial em container.
