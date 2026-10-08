# AIVES Backend

Spring Boot 4 · Java 21 · PostgreSQL. Chi tiết xem repo `SWD_Docs` (dev-guide/backend.md).

```bash
cp .env.example .env          # điền DB_PASSWORD và JWT_SECRET
docker compose up -d --wait   # database
./mvnw spring-boot:run        # http://localhost:8080
./mvnw verify                 # test (cần Docker)
```
