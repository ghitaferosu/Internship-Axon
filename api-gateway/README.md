# api-gateway

The API Gateway serves as the single entry point for all client requests to the microservices ecosystem. It routes incoming requests to the appropriate backend service while abstracting the internal service architecture from clients

## Services

| Service | Default Port |
|---|---|
| api-gateway | 8080 |
| user-service | 8082 |
| event-service | 8081 |

## Infrastructure

All infrastructure is defined in this repo's `docker-compose.yml`. Run it once before starting any service.

| Container | Host Port | Used by |
|---|---|---|
| postgres | 5432 | user-service (`user_db`), event-service (`event_db`) |
| kafka | 9092 | user-service, event-service |
| redis | 6379 | event-service |

## Getting started

### 1. Start infrastructure

```bash
docker compose up -d
```

### 2. Start services

Each service can be started independently once the infrastructure is up:

```bash
# user-service
cd /path/to/user-service && mvn spring-boot:run

# event-service
cd /path/to/event-service && mvn spring-boot:run

# api-gateway
cd /path/to/api-gateway && mvn spring-boot:run
```

