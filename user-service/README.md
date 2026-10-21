# user-service

This service manages users and their permissions.

## Prerequisites
- Java 21
- Maven
- Docker

## Running the Application

Before starting this service, run Docker Compose from the `api-gateway` project to bring up the required infrastructure:

```bash
cd /path/to/api-gateway
docker compose up -d
```

Then start the service:

```bash
mvn spring-boot:run
```

The application starts on port **8082**.