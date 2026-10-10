# API REST para Gerenciamento de Tarefas (Task Management)

> **Domínio:** Backend &nbsp;|&nbsp; **Nível:** Iniciante / Intermediário &nbsp;|&nbsp; **Linguagem:** Java 21 LTS &nbsp;|&nbsp; **Padrão:** Clean Architecture & Production-Ready

---

## 📖 Visão Geral

Construção de uma API REST de alta performance e robustez técnica para gerenciar tarefas inteiramente em memória, utilizando **Java 21**, **Virtual Threads (Project Loom)**, **HTTP Server nativo do JDK** e **Jackson Databind**.

A aplicação segue princípios sólidos de engenharia de software (Clean Architecture, Fail-Fast, Imutabilidade, Injeção de Dependências via Construtor e Semântica HTTP estrita de acordo com as RFCs 9110 e 7396).

---

## 🛠️ Pré-requisitos & Tecnologias

- **Java 21 LTS** (Eclipse Temurin, OpenJDK ou via SDKMAN)
- **Apache Maven 3.9+**
- **Docker** ou **Podman** (opcional, para execução conteinerizada)
- Ferramenta de teste HTTP: `curl`, HTTPie ou o script automatizado `./http.bash`

---

## 🎯 Objetivos de Aprendizado & Engenharia

- Mapeamento correto de operações CRUD para métodos HTTP (`GET`, `POST`, `PUT`, `PATCH`, `DELETE`).
- Semântica precisa de códigos de status (`200 OK`, `201 Created`, `204 No Content`, `400 Bad Request`, `404 Not Found`, `413 Payload Too Large`, `415 Unsupported Media Type`, `405 Method Not Allowed`).
- Diferenciação estrita entre **`PUT`** (substituição integral) e **`PATCH`** (atualização parcial).
- Segurança de memória e prevenção de DoS impondo limite no streaming do corpo da requisição (Buffer Bounded em 64 KB).
- Manipulação e serialização JSON estruturada com tratamento de erros robusto.
- Proteção contra condições de corrida (*race conditions*) em memória através de entidades imutáveis e operações atômicas sob lock de bucket (`ConcurrentHashMap.computeIfPresent`).
- Arquitetura limpa com isolamento de responsabilidades (HTTP Handler, Serviço de Domínio e Repositório).
- Testes automatizados cobrindo testes unitários, concorrência e integração HTTP ponta a ponta.

---

## ⚙️ Requisitos Funcionais

- [x] O sistema deve expor um endpoint para listar todas as tarefas com suporte a filtro `?completed=true|false`.
- [x] O sistema deve expor um endpoint para buscar uma tarefa pelo ID e retornar `404` quando ela não existir.
- [x] O sistema deve criar uma tarefa a partir de um corpo JSON, atribuir um ID único (UUID) e retornar `201 Created` com o recurso criado.
- [x] O sistema deve rejeitar requisições de criação sem campo obrigatório com `400 Bad Request` e mensagem explicativa.
- [x] O sistema deve atualizar uma tarefa existente via `PUT` (substituição total) ou `PATCH` (atualização parcial) e retornar `404` se o ID for desconhecido.
- [x] O sistema deve remover uma tarefa pelo ID e retornar `204 No Content` sem corpo (ou `404` se ausente).
- [x] O sistema deve retornar JSON válido com o cabeçalho `Content-Type: application/json; charset=UTF-8` em toda resposta que possuir corpo (respostas `204 No Content` não retornam cabeçalho de conteúdo, conforme RFC 9110).

---

## 📐 Contrato da API e Estrutura de Dados

### Modelo `Task` (Entidade Imutável)

| Campo | Tipo | Obrigatório | Descrição |
| :--- | :--- | :---: | :--- |
| `id` | `String` (UUID) | Sim | Atribuído exclusivamente pelo servidor |
| `title` | `String` | Sim | Título descritivo da tarefa (não-nulo, não-vazio) |
| `completed` | `boolean` | Sim | Status da tarefa (padrão: `false` na criação) |
| `createdAt` | `String` (ISO-8601) | Sim | Timestamp UTC gerado na criação |

---

### Endpoints da API

| Método | Endpoint | Status HTTP | Headers | Payload / Query / Descrição |
| :--- | :--- | :--- | :--- | :--- |
| `GET` | `/tasks` | `200 OK` | `Content-Type: application/json` | Suporta filtro opcional: `?completed=true` ou `?completed=false` |
| `GET` | `/tasks/{id}` | `200 OK`<br>`404 Not Found` | `Content-Type: application/json` | Busca uma tarefa específica pelo ID |
| `POST` | `/tasks` | `201 Created`<br>`400 Bad Request`<br>`413 Payload Too Large`<br>`415 Unsupported Media Type` | `Content-Type: application/json` | Cria nova tarefa.<br>Body: `{"title": "Nome da tarefa"}` |
| `PUT` | `/tasks/{id}` | `200 OK`<br>`400 Bad Request`<br>`404 Not Found`<br>`413 Payload Too Large`<br>`415 Unsupported Media Type` | `Content-Type: application/json` | **Substituição completa** do recurso.<br>Exige obrigatoriamente `title` e `completed`.<br>Body: `{"title": "Novo título", "completed": true}` |
| `PATCH` | `/tasks/{id}` | `200 OK`<br>`400 Bad Request`<br>`404 Not Found`<br>`413 Payload Too Large`<br>`415 Unsupported Media Type` | `Content-Type: application/json` | **Atualização parcial**.<br>Aceita `title`, `completed`, ou ambos.<br>Body: `{"title": "..."}` ou `{"completed": true}` |
| `DELETE` | `/tasks/{id}` | `204 No Content`<br>`404 Not Found` | *(Sem headers de corpo)* | Remove a tarefa. Não retorna corpo na resposta de sucesso. |

---

### Formato Padrão de Erro

Todas as respostas de erro retornam `Content-Type: application/json; charset=UTF-8` no seguinte padrão:

```json
{
  "error": "Field 'title' is required and cannot be blank"
}
```

---

## 🚀 Desafios Extras

- [x] Adicionar filtro `?completed=true|false` no endpoint de listagem.
- [x] Suportar `PATCH` para atualizações seletivas além do `PUT` completo.
- [ ] Adicionar paginação com parâmetros de query `limit` e `offset`.
- [x] Escrever testes automatizados que exercitem cada endpoint, concorrência e código de status.

---

## ✅ Definição de Pronto (DoD)

- [x] Todos os endpoints CRUD funcionam e retornam os códigos de status documentados.
- [x] Buscar, atualizar ou remover um ID inexistente retorna `404 Not Found`.
- [x] Entradas inválidas (JSON malformado, campos ausentes, tipos errados) retornam `400 Bad Request` com diagnóstico claro, nunca `500`.
- [x] IDs são UUIDs únicos e estáveis gerados pelo servidor.
- [x] Respostas com conteúdo definem `Content-Type: application/json; charset=UTF-8`.
- [x] Respostas `204 No Content` não retornam corpo nem cabeçalho `Content-Type`.

---

## 🔎 Revisão Técnica & Auditoria de Engenharia (Senior Staff Review)

Esta auditoria técnica avaliou a conformidade do código com os padrões de produção (Clean Architecture, Java 21 LTS, concorrência sob Virtual Threads, semântica HTTP RFC 9110/7396, durabilidade ACID e sistemas distribuídos).

### 🚨 [BLOCKER / P0] Quebra de Build & Falhas Críticas de Durabilidade (ACID)
1. **Erro de Compilação no Maven (`mvn test` quebrado):**
   - Em `Databaseconfig.java`, o método `getDataSource` tenta converter `HikariDataSource` para `com.taskmanagement.api.infra.repository.DataSource` (classe dummy vazia criada acidentalmente). O build falha imediatamente com erro de tipos incompatíveis.
   - Violação de convenção de nomenclatura: classe nomeada como `Databaseconfig` em vez de `DatabaseConfig`.
2. **Durabilidade Zero & Violação de Consistência em `PostgresTaskRepository`:**
   - **Perda de Dados em Crash (Durabilidade Zero):** O método `save(Task)` enfileira tarefas em uma `LinkedBlockingQueue` (`batchQueue`) em memória e responde `201 Created` de imediato. Se a JVM reiniciar ou sofrer kill antes do flush (ou de 100 itens), os dados são perdidos sem nunca tocar o WAL do PostgreSQL.
   - **Quebra de Read-Your-Own-Writes:** Chamar `save()` e em seguida `findById()` retorna `404 Not Found` enquanto o lote não sofrer flush.
   - **Morte Silenciosa do Scheduler:** Se `flushBatch()` lançar uma `SQLException` não tratada, o `ScheduledExecutorService` suprime execuções subsequentes de forma silenciosa (`scheduleAtFixedRate` morre permanentemente). Além disso, o lote de tarefas drenadas é descartado.
3. **DDL em Runtime no Caminho Crítico de Leitura:**
   - Em `findAll()` e `findAll(completedFilter)`, a query `CREATE TABLE IF NOT EXISTS tasks` é executada a cada requisição HTTP `GET`. No PostgreSQL, isso adquire lock exclusivo de catálogo (`AccessExclusiveLock`), destruindo o throughput e provocando contenção massiva. DDL deve ser executado exclusivamente no startup via migrations (Flyway).
4. **Métodos Incompletos no Repositório:**
   - `update`, `deleteById` e `existsById` em `PostgresTaskRepository` lançam `UnsupportedOperationException`, quebrando as operações de `PUT`, `PATCH` e `DELETE`.
5. **Inconsistência de Orquestração & Múltiplas Réplicas (`deployment.yaml` / `compose.yaml`):**
   - `deployment.yaml` define `replicas: 3` com `image: task-management:latest`, enquanto a aplicação usa repositório em memória por padrão no `Main.java`. Cada pod mantém seu próprio heap isolado, gerando dados fantasmas e 404s intermitentes.
   - Divergência no nome da imagem: `compose.yaml` gera `task-management-api`, mas o Kubernetes aguarda `task-management:latest`.
   - `compose.yaml` referencia volume `postgres_data` no serviço `postgres`, mas omite a declaração de `postgres_data` no bloco raiz `volumes:`, quebrando a inicialização do Compose.

### ⚠️ [CRITICAL / P1] Concorrência, Sockets & Riscos de Dual-Write
1. **Thread-Safety Corrompido no Redis (`JedisTaskCache`):**
   - A classe mantém uma única instância de `redis.clients.jedis.Jedis`. A classe `Jedis` **não é thread-safe** (encapsula um único socket TCP `java.net.Socket`). Sob Virtual Threads, chamadas concorrentes corrompem o fluxo de bytes do protocolo RESP (REdis Serialization Protocol), provocando exceções de conexão ou vazamento cruzado de dados entre requisições. Deve-se adotar `JedisPool` / `JedisPooled`.
   - Ausência de TTL nos registros salvos no Redis (`jedis.set` sem expiração), levando ao esgotamento de memória (`OOM` / evicção não controlada).
2. **Handshake Churn & Arquitetura no RabbitMQ (`RabbitQueuePublisher`):**
   - Criação e destruição de `Channel` AMQP por mensagem (`connection.createChannel()` em cada `publish`). Abrir e fechar canais TCP gera múltiplos round-trips de rede (`channel.open`/`channel.close`), degradando latência e CPU do broker.
   - Violação de Clean Architecture: o record `RabbitConfig` foi posicionado no pacote `domain.contract.queue`. Detalhes de infraestrutura de mensageria não devem poluir o domínio.
   - Risco de Dual-Write: publicar eventos diretamente na thread HTTP logo após o commit do banco introduz risco de inconsistência caso o broker falhe ou a transação sofra rollback posterior. A publicação deve ser assíncrona via Transactional Outbox.

### ℹ️ [HIGH / P2] Hardening de Contrato HTTP & Boas Práticas
1. **Header `Location` no `201 Created`:** Conforme a RFC 9110, respostas `201` de criação de recurso devem informar o cabeçalho `Location: /tasks/{id}`.
2. **Validação de Tamanho Máximo de `title`:** Falta impor limite de tamanho (ex.: 200 caracteres), permitindo que payloads de até 64 KB encham o banco ou a memória.
3. **URL-Decoding de `{id}` no Path:** IDs enviados via path param não sofrem decodificação UTF-8 (`URLDecoder.decode`), falhando em caracteres codificados.
4. **Tratamento Previsível de Erros:** Erros 404 de tarefa inexistente devolvem mensagem com pontuação truncada (`"Task not found:"`). `Main.java` utiliza `System.out.println` e `System.err.println` em vez de logging estruturado (SLF4J).

---

## 📋 Roadmap de Microtarefas Pendentes (Checklist de Execução)

Abaixo estão listadas as microtarefas necessárias para estabilizar o código e evoluir a aplicação com segurança até a **Arquitetura V2** ([docs/ARCHITECTURE-V2.md](docs/ARCHITECTURE-V2.md)). O guia detalhado de cada sessão de implementação encontra-se em [docs/MICROTAREFAS.md](docs/MICROTAREFAS.md).

### 🔴 Fase 0 — Desbloqueio Imediato & Correção de Compilação (P0)
- [ ] **T00** — Corrigir falha de compilação em `Databaseconfig.java`:
  - Remover a classe dummy [DataSource.java](src/main/java/com/taskmanagement/api/infra/repository/DataSource.java).
  - Renomear [Databaseconfig.java](src/main/java/com/taskmanagement/api/infra/db/Databaseconfig.java) para `DatabaseConfig.java` e retornar `javax.sql.DataSource` ou `HikariDataSource`.
  - Corrigir a inicialização concorrente não thread-safe do pool estático HikariCP.
  - Garantir que `mvn clean test` volte a compilar com sucesso imediato.

### 🟡 Fase A — Alinhamento de Contratos & Stubs
- [ ] **T01** — Inventário técnico honesto: documentar a divergência entre o código real (`Main` só roda `InMemory`), compose (`redis`, `rabbitmq`, `postgres`) e manifestos k8s.
- [ ] **T02** — Decidir stubs e feature flags de inicialização: definir ordem estrita de wiring (`STORAGE=memory|jdbc`, `CACHE_ENABLED=true|false`, `QUEUE_ENABLED=true|false`).
- [ ] **T03** — Ajustar contratos do domínio (`TaskRepository`, `TaskCache`, `TaskQueuePublisher`) e mover [RabbitConfig.java](src/main/java/com/taskmanagement/api/domain/contract/queue/RabbitConfig.java) para o pacote de infraestrutura (`infra/queue` ou `infra/config`).

### 🟢 Fase B — Persistência Robusta com PostgreSQL & Flyway (ACID)
- [ ] **T04** — Corrigir [compose.yaml](compose.yaml):
  - Adicionar `postgres_data` na seção raiz de `volumes:`.
  - Injetar variáveis de ambiente do PostgreSQL no serviço `api` (`DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`).
  - Adicionar `postgres` na lista `depends_on` da API.
- [ ] **T05** — Implementar migrações com Flyway:
  - Adicionar dependência `flyway-core` e `flyway-database-postgresql` no [pom.xml](pom.xml).
  - Criar `src/main/resources/db/migration/V1__create_tasks.sql` com tipos adequados (`UUID` / `VARCHAR(36)`, `TIMESTAMPTZ`, constraint de título não-branco e índice em `completed`).
- [ ] **T06** — Reescrever [PostgresTaskRepository.java](src/main/java/com/taskmanagement/api/infra/repository/PostgresTaskRepository.java) com persistência síncrona e durável:
  - **Eliminar** a fila assíncrona em memória (`batchQueue`) no `save()` — comandos de escrita devem executar `INSERT` síncrono no PostgreSQL.
  - **Eliminar** comandos DDL (`CREATE TABLE IF NOT EXISTS`) de dentro de `findAll()` e de consultas de leitura.
  - Implementar métodos pendentes: `update()` com atomicidade, `deleteById()`, `existsById()`.
- [ ] **T07** — Configurar alternância dinâmica no [Main.java](src/main/java/com/taskmanagement/api/Main.java):
  - Instanciar `PostgresTaskRepository` ou `InMemoryTaskRepository` conforme variável `STORAGE`.
  - Executar migrações do Flyway no startup quando `STORAGE=jdbc`.

### 🟣 Fase C — Mensageria Resiliente & Transactional Outbox (Anti Dual-Write)
- [ ] **T08** — Desenhar modelo de Transactional Outbox para eliminar dual-write entre PostgreSQL e RabbitMQ.
- [ ] **T09** — Criar migração `V2__create_outbox.sql` e gravar eventos na mesma transação atômica do CRUD de `Task`.
- [ ] **T10** — Implementar worker assíncrono `OutboxRelay`:
  - Polling periódico ou `LISTEN/NOTIFY` com `SELECT ... FOR UPDATE SKIP LOCKED`.
  - Publicação de eventos pendentes no RabbitMQ e atualização de status para `PUBLISHED`.
- [ ] **T11** — Garantir thread-safety e reutilização de canais AMQP:
  - Evitar criação de novo `Channel` a cada mensagem em [RabbitQueuePublisher.java](src/main/java/com/taskmanagement/api/infra/queue/RabbitQueuePublisher.java).
  - Tratar reconnects e fechar recursos de forma limpa no shutdown hook.
- [ ] **T24** — Desenvolver consumidor RabbitMQ de exemplo com processamento idempotente (ack manual e dedup por ID de evento).

### 🔵 Fase D — Caching de Alta Performance com Redis (Thread-Safe)
- [ ] **T12** — Conectar [TaskCache](src/main/java/com/taskmanagement/api/domain/contract/cache/TaskCache.java) no `TaskServiceImpl` através do padrão Cache-Aside:
  - Leitura por ID: tentar Redis ➔ miss busca no DB ➔ grava em cache.
  - Escrita/Atualização/Exclusão: invalidação (`evict`) após confirmação no DB.
- [ ] **T13** — Refatorar [JedisTaskCache.java](src/main/java/com/taskmanagement/api/infra/cache/JedisTaskCache.java):
  - Substituir instância única de `Jedis` por `JedisPool` ou `JedisPooled` thread-safe para suportar Virtual Threads concorrentes.
  - Implementar TTL explícito (ex.: 60s) nas operações de `put`.
- [ ] **T14** — Garantir consistência e tolerância a falhas no cache:
  - Falhas no Redis não devem derrubar a requisição HTTP (fallback silencioso para o banco com log de advertência).

### ⚪ Fase E — Deploy & Orquestração Consistente (Docker & Kubernetes)
- [ ] **T15** — Padronizar nomes de imagem e tags em [Dockerfile](Dockerfile), [compose.yaml](compose.yaml) e [deployment.yaml](deployment.yaml) (`task-management-api:1.0.0`).
- [ ] **T16** — Tornar [deployment.yaml](deployment.yaml) seguro para `replicas: 3`:
  - Adicionar `livenessProbe` e `readinessProbe` HTTP.
  - Definir `resources.limits` e `resources.requests` (CPU e Memória).
  - Bloquear execução de múltiplas réplicas quando `STORAGE=memory`.
- [ ] **T17** — Ajustar pipeline de build no Dockerfile (separar execução de testes em stage dedicado).

### 🟠 Fase F — Hardening de Contrato HTTP, Observabilidade & Testes
- [ ] **T18** — Adicionar cabeçalho `Location: /tasks/{id}` na resposta `201 Created` do [TaskHandler.java](src/main/java/com/taskmanagement/api/infra/web/http/handler/TaskHandler.java).
- [ ] **T19** — Implementar validação de tamanho máximo de `title` (máx. 200 caracteres) em [Task.java](src/main/java/com/taskmanagement/api/domain/model/Task.java) e `TaskServiceImpl`.
- [ ] **T20** — Implementar URL-decode seguro de `segments[1]` (`taskId`) com `URLDecoder.decode(..., StandardCharsets.UTF_8)` no [TaskHandler.java](src/main/java/com/taskmanagement/api/infra/web/http/handler/TaskHandler.java).
- [ ] **T21** — Refinar mapeamento de exceções no handler:
  - Corrigir mensagem de `TaskNotFoundException` (remover `:` truncado).
  - Substituir saídas `System.out`/`System.err` no [Main.java](src/main/java/com/taskmanagement/api/Main.java) por logger SLF4J / `java.util.logging`.
- [ ] **T22** — Expandir suíte de testes de concorrência com Virtual Threads para validar ausência de condições de corrida e lost updates no repositório PostgreSQL.
- [ ] **T23** — Implementar suporte a paginação (`limit` e `offset`) no endpoint `GET /tasks`.

---

## 🏃 Como Executar a Aplicação

### 1. Execução Local via Maven & Java

```bash
# Compilar e rodar a suíte de testes
mvn clean test

# Gerar o pacote executável (shaded fat jar)
mvn clean package -DskipTests

# Executar a API na porta padrão 8080 (ou informe outra porta como argumento)
java -jar target/task-management-api.jar
```

A API estará disponível em `http://localhost:8080/tasks`.

---

### 2. Executando os Testes Manuais com o `http.bash`

Com o servidor rodando em outro terminal:

```bash
./http.bash http://localhost:8080/tasks
```

O script executará cenários verificando listagem, criação, atualização com `PUT`, atualização com `PATCH`, filtros e testes de erro (400, 404, 415).

---

### 3. Executando com Podman (ou Docker)

```bash
# Construir a imagem conteinerizada
podman build -t task-management-api:latest .
# ou: docker build -t task-management-api:latest .

# Subir a stack completa com Compose (PostgreSQL, Redis, RabbitMQ e API)
podman compose up --build
# ou: docker compose up --build

# Modo debug remoto (porta 5005 exposta para attach do IDE)
podman compose -f compose.yaml -f compose.debug.yaml up --build
```

---

### 4. Executando com Kubernetes ou `podman kube play`

```bash
# Opção A — Rodar o manifesto Kubernetes diretamente via Podman (sem cluster pesado)
podman kube play deployment.yaml
# Para derrubar: podman kube down deployment.yaml

# Opção B — Aplicar em um cluster Kubernetes local (Kind, Minikube, k3s)
kubectl apply -f deployment.yaml
kubectl port-forward service/task-api-service 8080:8080
```

---

## 🧰 Executando no VS Code com Dev Containers

1. Abra o diretório do projeto no VS Code com a extensão **Dev Containers**.
2. Selecione **Dev Containers: Reopen in Container** (`Ctrl+Shift+P`).
3. O ambiente configurará Java 21, Maven e abrirá a porta `8080`.
4. Pressione `F5` para iniciar o debug ou `Shift+F5` para parar.

---

## ✅ Roteiro de evolução em minitarefas

Recomendações da revisão feita pelo Arquiteto Java, pelo Programador Java Senior e pelo Especialista em DevOps. Siga as seções na ordem; os números entre parênteses apontam para a seção de arquitetura correspondente.

### Arquitetura

**1. Fonte da verdade**
- [ ] Desenhar o caminho de um `POST /tasks`, do `Main` até o `save`, e marcar onde o dado fica guardado hoje.
- [ ] Escrever a tabela `tasks` com tipos e constraints, usando `TIMESTAMPTZ` em todas as datas.
- [ ] Criar a primeira migração Flyway e tirar o `CREATE TABLE` de dentro do `findAll`.
- [ ] Trocar o `save` em lote por um `INSERT` síncrono, para o 201 só sair depois do commit.
- [ ] Implementar `update` e `deleteById`, que hoje lançam `UnsupportedOperationException`.
- [ ] No `Main`, escolher o repositório por variável de ambiente e deixar o em memória só para testes.

**2. Camadas**
- [ ] Mover o `RabbitConfig` de `domain` para `infra`.
- [ ] Criar o pacote `application` e levar o `TaskServiceImpl` para lá.
- [ ] Remover o `Task.getDescription()`, que só lança exceção.
- [ ] Montar tudo no `Main` sem singleton estático, passando o `DataSource` por construtor.

**3. Eventos sem dual-write**
- [ ] Escrever no papel as duas falhas de "grava no Postgres e publica no Rabbit".
- [ ] Criar a tabela `outbox_events` e gravar o evento na mesma transação da tarefa.
- [ ] Escrever um relay que lê a outbox com `FOR UPDATE SKIP LOCKED` e publica no Rabbit.
- [ ] Fazer um consumidor que ignora evento repetido pelo id.

**4. Cache**
- [ ] Usar o Redis só no `GET /tasks/{id}`: tentar o cache e, no miss, buscar no Postgres e guardar com TTL.
- [ ] Depois do commit de create, update e delete, apagar a chave do Redis.

**5. Concorrência Otimista no Banco e HTTP (depois que a seção 1 estiver pronta)**
- [ ] Acrescentar uma coluna técnica `version` (`BIGINT` ou `INT`) na tabela `tasks` para controle de concorrência otimista (*Optimistic Locking*) com `UPDATE ... WHERE id = ? AND version = ?`.
- [ ] Devolver `409 Conflict` quando duas requisições concorrentes tentarem atualizar a mesma versão do registro.

### Código Java

**Testes primeiro, como rede de segurança**
- [ ] Escrever o exercício do `scheduleAtFixedRate`, com uma exceção na 3ª execução, e ver a tarefa parar sem nenhum aviso.
- [ ] Adicionar o Testcontainers PostgreSQL ao `pom.xml` e criar a classe `PostgresTaskRepositoryIT`.
- [ ] Escrever um teste que faz `save` e logo em seguida `findById`; ele tem que falhar com o código atual.
- [ ] Transformar os testes do `InMemoryTaskRepositoryTest` numa classe base abstrata e rodar a mesma suíte contra a versão em memória e contra a do Postgres.

**Repositório JDBC (1)**
- [ ] Apagar o `findAll()` sem `@Override`, que é uma cópia do `findAll(Boolean)`.
- [ ] Ler e gravar as datas com `setObject(i, OffsetDateTime)` e `getObject(i, OffsetDateTime.class)` em vez de `Timestamp`.
- [ ] No `update`, usar `SELECT ... FOR UPDATE` e depois o `UPDATE`, com `setAutoCommit(false)` e `commit` na mesma conexão.
- [ ] Escrever um teste com 10 threads alternando o `completed` da mesma tarefa e conferir que nenhuma alteração se perde.
- [ ] Criar uma `RepositoryException` em `infra` no lugar da `RuntimeException` crua.
- [ ] Colocar `LIMIT` e `OFFSET` no `findAll`, com um teto de 100.
- [ ] Apagar a fila, o scheduler e o `initTable()`.

**Pool e configuração (2)**
- [ ] Entender por que o `if (dataSource == null)` do `Databaseconfig` não é thread-safe e por que duas threads podem criar dois pools.
- [ ] Ler `DATABASE_URL`, `DATABASE_USER` e `DATABASE_PASSWORD` das variáveis de ambiente no `Main`.
- [ ] Fechar o pool no shutdown hook só quando ele tiver sido criado.

**HTTP (`TaskHandler`)**
- [ ] Trocar o `replaceAll` com regex, que roda a cada requisição, por um `Pattern` estático ou por manipulação simples de string.
- [ ] Fazer um erro ao serializar a resposta virar 500, e não 400.
- [ ] Devolver o cabeçalho `Location: /tasks/{id}` junto com o 201.
- [ ] Aplicar `URLDecoder.decode` na query string antes de interpretar o `completed`.
- [ ] Mapear a `RepositoryException` para 503, com uma mensagem neutra e sem stack trace.

**Rabbit e Redis (3 e 4)**
- [ ] No relay, abrir um único channel com `confirmSelect()` e só marcar o evento como publicado depois do `waitForConfirmsOrDie`.
- [ ] No `JedisTaskCache`, capturar a `JedisException` e buscar no banco; Redis fora do ar não pode derrubar o GET.
- [ ] Escrever um teste com o Redis desligado e conferir que o GET continua respondendo.

**Concorrência Otimista (5)**
- [ ] Fazer o `PATCH` / `PUT` ler o cabeçalho HTTP `If-Match` com a `version` atual e devolver `409 Conflict` em caso de escrita concorrente conflitante.

### Podman, Compose, Kubernetes e CI

**Podman Compose (dá para começar hoje)**
- [ ] Rodar `podman compose config` (ou `docker compose config`) e corrigir o erro do volume `postgres_data`, que é usado sem estar declarado.
- [ ] Trocar `porSts` por `ports` (e ajustar `address=*:5005` para Java 21+) no `compose.debug.yaml`.
- [ ] Colocar `healthcheck` no Postgres, no Redis e no RabbitMQ, e usar `depends_on` com `condition: service_healthy` na API.
- [ ] Passar as variáveis `DATABASE_URL`, `DATABASE_USER` e `DATABASE_PASSWORD` para a API e incluir o `postgres` no `depends_on`.
- [ ] Mover as senhas para um arquivo `.env` fora do Git.
- [ ] Remover os `container_name` fixos e testar múltiplas instâncias no Compose.
- [ ] Publicar as portas do banco, do Redis e do Rabbit só em `127.0.0.1`.

**Containerfile / Dockerfile (Podman Rootless)**
- [ ] Garantir nomes de imagem totalmente qualificados (`docker.io/library/...`) para o Podman não falhar na resolução de registries.
- [ ] Copiar o `pom.xml`, rodar `mvn dependency:go-offline` e só depois copiar o `src`; medir o tempo de um rebuild alterando uma linha de Java.
- [ ] Rodar a aplicação com um usuário que não seja root (`USER 10001`), compatível com Podman rootless e Kubernetes.
- [ ] Definir `JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"`, subir com `podman run --memory=512m` e ver a JVM respeitar o limite.
- [ ] Rodar `hadolint Dockerfile` e entender cada aviso.

**Kubernetes (com `podman kube play`, kind ou minikube)**
- [ ] Criar os endpoints `/health/live` e `/health/ready` no `Server`, com o ready checando o Postgres (depende da seção 1).
- [ ] No `deployment.yaml`, alinhar o nome da imagem (`task-management-api:1.0.0`), usar `replicas: 1` e `imagePullPolicy: IfNotPresent` para funcionar localmente no Podman/Kind/Minikube (ou imagem do GHCR com tag `sha-...` em cluster remoto).
- [ ] Testar o manifesto localmente com `podman kube play deployment.yaml` e depois no cluster com `kubectl apply -f deployment.yaml`.
- [ ] Adicionar `startupProbe`, `readinessProbe` e `livenessProbe`; derrubar o Postgres e ver o pod sair do Service sem reiniciar.
- [ ] Definir `requests` e `limits` de CPU e memória.
- [ ] Criar um `Service`, um `ConfigMap` e um `Secret`, e tirar as variáveis de dentro do Deployment.
- [ ] Adicionar `securityContext` com `runAsNonRoot` e `readOnlyRootFilesystem`.
- [ ] Rodar a migração Flyway num `Job` antes do rollout (depende da migração da seção 1).
- [ ] Só depois do `INSERT` síncrono e da outbox, subir para 2 réplicas e adicionar um `PodDisruptionBudget` e um `HPA`.

**CI**
- [ ] Incluir os testes do Testcontainers (configurado para suportar Podman/Docker) no `mvn verify`.
- [ ] Validar no pipeline o Compose com `docker compose config -q`, os manifestos com `kubeconform` e o Dockerfile com `hadolint`.
- [ ] Fazer o scan da imagem com Trivy, quebrando o build em vulnerabilidade crítica.
- [ ] Ativar o Dependabot para actions, Maven e imagens base.
- [ ] Ativar a proteção da `main` exigindo o CI verde.
