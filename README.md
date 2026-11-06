# AIVES Backend

Spring Boot 4 · Java 21 · PostgreSQL + pgvector. Docs: repo `SWD_Docs` (`requirements/`, `dev-guide/backend.md`).

```bash
cp .env.example .env                                  # điền DB_PASSWORD, MINIO_ROOT_PASSWORD, JWT_SECRET
docker compose up -d --wait                           # PostgreSQL + MinIO
./mvnw spring-boot:run -Dspring-boot.run.profiles=demo   # http://localhost:8080/swagger-ui.html
./mvnw verify                                         # test (cần Docker)
```
