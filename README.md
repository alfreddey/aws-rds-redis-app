# To-Do App — Application (Spring Boot)

A containerized Java **Spring Boot** To-Do application: a REST API plus a small
static UI. **Writes** are persisted to **PostgreSQL through Amazon RDS Proxy**;
**reads** are accelerated by a **read-through cache in Amazon ElastiCache for
Redis**. It runs as a single container on **ECS Fargate** behind a public ALB,
and is shipped via **blue/green** deployments driven by GitHub Actions → ECR →
EventBridge → CodePipeline → CodeDeploy.

> This is the **application** repository. The CloudFormation infrastructure
> (VPC, ECS, RDS + Proxy, ElastiCache, ALB, pipeline, OIDC roles) lives in the
> separate **infra** repository. The two meet at the runtime contract below and
> at `taskdef.json` / `appspec.yaml`.

## Architecture

```
Browser ─▶ ALB ─▶ ECS Fargate task (this app, :8080)
                       ├─ read  /api/tasks ─▶ Redis (hit)  ── miss ─▶ RDS Proxy ─▶ PostgreSQL
                       └─ write /api/tasks ─▶ RDS Proxy ─▶ PostgreSQL, then evict Redis

GitHub Actions ─(OIDC)─▶ ECR (:latest) ─▶ EventBridge ─▶ CodePipeline ─▶ CodeDeploy (blue/green) ─▶ ECS
```

- **Read-through cache** — `GET /api/tasks` and `GET /api/tasks/{id}` are
  `@Cacheable`; on a miss the method hits PostgreSQL and the result is cached in
  Redis (60s TTL). The cache-miss path logs explicitly, so CloudWatch Logs show
  exactly when a request reached the database.
- **Write-through eviction** — create / update / delete persist via RDS Proxy
  and `@CacheEvict` the cached views, so the next read repopulates from the
  source of truth.
- **Graceful degradation** — if Redis is unreachable, cache operations are
  logged and swallowed (the app falls back to RDS), and the Redis health
  indicator is disabled so a cache outage never fails the ALB health check.

## Runtime contract (env vars the container expects)

These are injected by the ECS task definition (`taskdef.json`, rendered in CI
from the infra stack outputs):

| Env var | Source | Notes |
|---------|--------|-------|
| `DB_HOST` | RDS Proxy endpoint | |
| `DB_PORT` | `5432` | |
| `DB_NAME` | `todos` | |
| `DB_SSLMODE` | `require` | RDS Proxy enforces TLS |
| `DB_USERNAME` / `DB_PASSWORD` | Secrets Manager (injected by ECS) | never in plaintext |
| `REDIS_HOST` | ElastiCache primary endpoint | |
| `REDIS_PORT` | `6379` | plaintext (no in-transit encryption / AUTH) |
| `CACHE_TTL_SECONDS` | optional, default `60` | cache entry TTL |
| `SIMULATED_READ_DELAY_MS` | optional, default `0` | demo aid — adds latency to the DB read path so the cache speed-up is visible |

Health is served at `GET /actuator/health` on port `8080` (the ALB health-check path).

## API

| Method | Path | Body | Result |
|--------|------|------|--------|
| `GET` | `/api/tasks` | — | list tasks (cached) |
| `GET` | `/api/tasks/{id}` | — | one task (cached) |
| `POST` | `/api/tasks` | `{title, description?, completed?}` | `201` created |
| `PUT` | `/api/tasks/{id}` | `{title, description?, completed}` | updated task |
| `DELETE` | `/api/tasks/{id}` | — | `204` |

The UI is served at `/` from `src/main/resources/static/index.html`.

## Run locally

Requires Docker (for Postgres + Redis) and a JDK 21+.

```bash
# 1. Start backing services
docker run -d --name todo-pg \
  -e POSTGRES_DB=todos -e POSTGRES_USER=todoadmin -e POSTGRES_PASSWORD=secretpw \
  -p 5432:5432 postgres:16-alpine
docker run -d --name todo-redis -p 6379:6379 redis:7-alpine

# 2. Run the app (sslmode=disable for a plain local Postgres)
DB_HOST=localhost DB_PORT=5432 DB_NAME=todos \
DB_USERNAME=todoadmin DB_PASSWORD=secretpw DB_SSLMODE=disable \
REDIS_HOST=localhost REDIS_PORT=6379 \
SIMULATED_READ_DELAY_MS=400 \
./mvnw spring-boot:run

# 3. Open http://localhost:8080  (or curl the API)
curl -s localhost:8080/api/tasks
curl -s -XPOST localhost:8080/api/tasks -H 'content-type: application/json' \
  -d '{"title":"buy milk","description":"2%"}'
```

With `SIMULATED_READ_DELAY_MS=400`, the first `GET /api/tasks` after a write is
slow (cache miss → DB) and logs `CACHE MISS [tasks:all] ...`; the next is fast
(served from Redis, no log line).

## Build

```bash
./mvnw verify                 # compile + unit tests
docker build -t todo-app .    # multi-stage image (jar -> Amazon Corretto on Alpine, non-root)
```

## CI/CD — `.github/workflows/build-deploy.yml`

On every push to `main` the workflow authenticates to AWS with **GitHub OIDC**
(no long-lived keys), then:

1. builds and tests the jar (`./mvnw verify`);
2. builds the image and pushes an immutable `:<sha>` tag (does **not** trigger a
   deploy);
3. renders `taskdef.json` from the live infra stack outputs and uploads
   `config.zip` (`taskdef.json` + `appspec.yaml`) to the deploy-config bucket;
4. pushes `:latest` **last** — the EventBridge rule fires on `:latest`, so the
   pipeline always starts with the fresh `config.zip` already in place.

CodePipeline then runs CodeDeploy's blue/green deployment: a green task set is
registered with the new image, health-checked on the green target group, traffic
is shifted, and blue is drained.

### Required repository configuration

Settings → Secrets and variables → Actions → **Variables**:

| Variable | Value |
|----------|-------|
| `AWS_REGION` | e.g. `us-east-1` |
| `AWS_APP_ROLE_ARN` | infra bootstrap output `GitHubActionsAppRoleArn` (`arn:aws:iam::<acct>:role/todo-app-prod-gha-app`) |
| `ECR_REPOSITORY` | `todo-app-prod` |
| `INFRA_STACK_NAME` | the CloudFormation Git-sync root stack name (e.g. `todo-app-prod`) |

The OIDC trust policy on `AWS_APP_ROLE_ARN` is scoped to this repository
(`repo:<org>/<this-repo>:*`), so the repo name here must match the
`GitHubAppRepo` value passed to the infra bootstrap stack.

> **First deploy:** before the platform stack exists, the workflow can't render
> `taskdef.json`, so it just publishes the image (so the ECS service has
> something to launch). Once the platform is up, subsequent pushes perform the
> full blue/green deploy.

## Project layout

```
.
├── pom.xml                    # Spring Boot 3.3, Java 21
├── Dockerfile                 # multi-stage: Corretto build -> Corretto/Alpine runtime (non-root)
├── appspec.yaml               # CodeDeploy blue/green appspec
├── taskdef.json               # ECS task def template (placeholders rendered in CI; <IMAGE1_NAME> left for CodeDeploy)
├── .github/workflows/build-deploy.yml
└── src/main/
    ├── java/com/amalitech/todo/
    │   ├── domain/Task.java            # JPA entity
    │   ├── repository/TaskRepository.java
    │   ├── service/TaskService.java    # @Cacheable reads / @CacheEvict writes
    │   ├── web/TaskController.java      # REST API
    │   ├── web/dto/                     # TaskRequest (validated) / TaskResponse
    │   ├── web/GlobalExceptionHandler.java
    │   └── config/CacheConfig.java      # Redis JSON serialization, TTL, graceful degradation
    └── resources/
        ├── application.yml
        ├── db/migration/V1__init.sql    # Flyway schema (Hibernate validates against it)
        └── static/index.html            # UI
```
