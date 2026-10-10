# Review de implementação — Task Management API

> Fatia: código Java (SQL, JDBC, concorrência, HTTP, testes, build).
> Arquitetura e produto: `docs/MELHORIAS-ESTRUTURA.md`. Roadmap marcável: `docs/MICROTAREFAS.md`.
> Revisado sobre o commit `400d481` + working tree. Nenhum arquivo de código foi alterado.

## Veredito

A `main` (commit `400d481`) compila e o CI passa: arquivo e classe se chamam `Databaseconfig`. O build só quebra com a renomeação para `DatabaseConfig.java` que estava **não commitada** na working tree, porque o `Main` continuou importando `Databaseconfig`. Essa mudança foi guardada pelo auto-stash da migração Java (`stash@{0}`, branch `appmod/java-upgrade-20261008211407`). O fluxo ativo (InMemory) está bom. O `PostgresTaskRepository` não deve ser ligado como está: ele perde escritas e quebra leitura logo após o POST.

## Bloqueante

- [ ] **B1 — Renomeação incompleta de `Databaseconfig`.** Se for aplicar o `stash@{0}` ou renomear de novo, faça arquivo, classe, `import` (`Main.java:7`) e chamada (`Main.java:37`) no mesmo commit. Em sistema que não diferencia maiúsculas, use `git mv` em dois passos (`Databaseconfig.java` → `Tmp.java` → `DatabaseConfig.java`).
  O `Main` também fecha um pool que nunca abriu: ele só deveria fechar o que criou.

## Alto — `PostgresTaskRepository`

- [ ] **A1 — `save` assíncrono quebra o contrato.** `save` (L162–168) só coloca na fila; o INSERT sai em até 30 s (L28). `POST /tasks` devolve 201 e um `GET /tasks/{id}` em seguida devolve 404. Se o processo cair, tudo da fila se perde.
  Correção: INSERT síncrono, uma task por chamada, dentro de transação. Batch só para importação em massa, fora do request.
- [ ] **A2 — Uma exceção mata o flush para sempre.** `scheduleAtFixedRate` (L28) **cancela as execuções seguintes** se `triggerFlush` lançar. `flushBatch` lança `RuntimeException` (L47) em qualquer erro (PK duplicada, banco fora). Depois do primeiro erro, nada mais é gravado, sem log.
  Correção: some com o scheduler (A1). Se ficar, `try/catch` com log dentro da tarefa agendada.
- [ ] **A3 — Lote tudo-ou-nada.** Um registro ruim derruba os outros 99, e eles já saíram da fila (`drainTo`, L155). Dados perdidos.
- [ ] **A4 — `update`, `deleteById`, `existsById` lançam `UnsupportedOperationException`** (L171–183). PUT/PATCH/DELETE viram 500 com esse repositório.
  Correção para `update`: `SELECT ... FOR UPDATE` + aplicar a função + `UPDATE` na mesma transação (preserva a atomicidade que o `computeIfPresent` dá hoje). Alternativa: coluna `version` e `UPDATE ... WHERE id=? AND version=?` → 409 quando falhar.
- [ ] **A5 — DDL dentro da leitura.** `CREATE TABLE IF NOT EXISTS` roda a cada `findAll` (L52–68 e L89–105), com conexão extra. Há **três** definições diferentes da tabela: `TEXT` vs `VARCHAR(255)`, `TIMESTAMP` vs `TIMESTAMPTZ` (L192–209, `initTable` nunca é chamado).
  Correção: uma migração Flyway `V1__create_tasks.sql` com `created_at TIMESTAMPTZ`, rodada no start. O repositório só faz DML.
- [ ] **A6 — Fuso horário.** `TIMESTAMP` sem fuso + `Timestamp.from(Instant)` / `toInstant()` desloca horas conforme o fuso da JVM e do servidor. Use `TIMESTAMPTZ` e `setObject(i, OffsetDateTime)` / `getObject(i, OffsetDateTime.class)`.
- [ ] **A7 — `findAll()` sem `@Override` duplicando `findAll(Boolean)`.** Código morto e copiado; remova.
- [ ] **A8 — Exceções genéricas.** `RuntimeException` crua em todo SQL. Crie `RepositoryException` (infra) e mapeie no handler para 503/500 com mensagem neutra, sem stack para o cliente.
- [ ] **A9 — Sem paginação.** `findAll` carrega a tabela inteira na memória. `LIMIT/OFFSET` com teto (ex.: 100) e índice em `(completed, created_at)`.

## Médio — outras peças

- [ ] **M1 — `Databaseconfig` singleton estático com parâmetros.** O `if (dataSource == null)` não tem `volatile` nem `synchronized`: duas threads no start podem criar dois pools e vazar um. A segunda chamada de `getDataSource(url, ...)` com outra URL devolve o pool antigo em silêncio. Prefira criar o `HikariDataSource` no `Main` (composition root) e injetar; fechar no shutdown hook. Leia config de env (`DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD`), nunca hardcoded.
- [ ] **M2 — `RabbitQueuePublisher` abre e fecha um `Channel` por mensagem** (L71). É thread-safe (resolve o risco da revisão anterior), mas caro e sem *publisher confirms*: `basicPublish` sem confirmação pode perder a mensagem. No relay do outbox use um channel dedicado com `confirmSelect()` + `waitForConfirmsOrDie`, e só marque `PUBLISHED` depois do confirm.
- [ ] **M3 — `RabbitConfig` em `domain.contract.queue`.** Host/senha de broker é infra. Mova para `infra.queue`.
- [ ] **M4 — `JedisTaskCache` agora usa `JedisPool`** — bom, o problema de thread-safety foi resolvido. Falta: falha no Redis não pode derrubar o GET (cache é opcional). Envolva em `try/catch (JedisException)`, logue e caia para o banco.
- [ ] **M5 — `TaskHandler`**: `replaceAll` com regex a cada request (L49); `JsonProcessingException` da **resposta** vira 400 (L84); sem `Location` no 201; query sem URL-decode (L100–101, L227). (Já listados no `MICROTAREFAS.md`, F.)
- [ ] **M6 — Dockerfile com `-DskipTests`** (L5). O CI já roda `verify`, então isso é aceitável **só** se a imagem for construída a partir do mesmo commit que passou no CI. Documente isso ou rode os testes no stage de build.

## Testes que faltam

- [ ] `PostgresTaskRepositoryIT` com **Testcontainers PostgreSQL**: save→findById imediato; update concorrente (N threads) sem lost update; delete inexistente → `false`; filtro `completed`.
- [ ] Teste do relay do outbox: commit → evento `PENDING` → publicado → `PUBLISHED`; rollback → nenhum evento.
- [ ] Teste do cache: Redis fora → GET ainda responde pelo banco.
- [ ] Teste de contrato HTTP rodando a mesma suíte contra InMemory **e** Postgres (classe base abstrata).

## Concorrência Otimista (Opcional — puramente técnico)

Se quiser praticar controle de concorrência otimista além do lock pessimista (`SELECT ... FOR UPDATE`):

1. Coluna técnica `version` na tabela `tasks` (`UPDATE tasks SET ..., version = version + 1 WHERE id = ? AND version = ?`).
2. `PATCH` / `PUT` com cabeçalho HTTP `If-Match` / `version` → retorna `409 Conflict` quando duas requisições concorrentes tentam sobrescrever a mesma versão.

## Ordem sugerida

1. B1 (compilar) → CI verde.
2. A1–A7 (repositório Postgres correto, síncrono, migração Flyway) + IT com Testcontainers.
3. Trocar o `Main` para Postgres via env; InMemory só em teste.
4. M1, M4, M5, M6.
5. Outbox + relay com confirms (M2).
