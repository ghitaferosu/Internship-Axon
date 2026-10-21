# Ticketmaster — Internship AXON

Aplicație pentru descoperirea evenimentelor, selectarea locurilor și rezervarea și achiziția biletelor, dezvoltată în echipă în cadrul stagiului de practică la AXON. Proiectul folosește o arhitectură de microservicii, cu un API Gateway, servicii pentru evenimente și utilizatori și o interfață React.

## Responsabilitățile mele

**Adela Coblișan · 20 iulie – 28 august 2026 · 120 de ore**

Contribuțiile mele în cadrul proiectului, conform raportului de activitate:

- **Documentare și configurare:** am studiat arhitectura microserviciilor, comunicarea sincronă REST și asincronă, Spring Boot, Spring Data JPA, Spring Security și Apache Kafka și am configurat proiectul local.
- **Modelarea datelor pentru Event Service:** am definit entitățile JPA `Event`, `Venue`, `Seat`, `Performer` și `EventPerformer`, relațiile și enumerările, migrarea inițială Flyway și repository-urile Spring Data JPA.
- **Căutarea evenimentelor:** am implementat controllerul, serviciul, criteriile de filtrare și repository-ul cu SQL nativ și extensia PostgreSQL `pg_trgm`, pentru căutare tolerantă la greșeli de scriere.
- **Paginare și performanță:** am implementat DTO-urile de răspuns paginat, validarea parametrilor și integrarea în controller. Am optimizat numărarea rezultatelor prin estimări din planul de execuție PostgreSQL.
- **Rezervări și plăți:** am implementat API-ul de rezervare temporară a locurilor până la plată și API-ul de finalizare a achiziției, cu idempotență asigurată printr-o constrângere unică în baza de date. Am adăugat eliberarea automată a rezervărilor expirate printr-un job programat cu actualizare condiționată.
- **Comunicare între servicii:** am implementat publicarea evenimentelor Kafka la schimbarea stării biletelor, numai după confirmarea tranzacției din baza de date, filtrul de autentificare pentru apelurile interne, clienții OpenFeign pentru rezervare și administrare și controllerul de booking.
- **Notificări și comenzi:** am studiat și implementat idempotența trimiterii emailurilor cu Redis în serviciul de utilizatori. Am grupat plățile dintr-un coș sub un identificator unic de comandă și am implementat consumatorul Kafka și agregarea biletelor în Redis pentru trimiterea unui singur email de confirmare per comandă.
- **Roluri și vizibilitate:** am adăugat migrarea pentru rolul de organizator și am ascuns evenimentele nepublicate față de clienți prin filtrare SQL și verificarea rolului în gateway.
- **Date și interfață:** am adaptat jobul de populare a datelor pentru rânduri și locuri per rând și am modificat panoul de bilete.
- **Validare și prezentare:** am testat manual endpoint-urile cu Postman și fluxul complet al aplicației și am pregătit prezentarea finală.

## Structura proiectului

| Componentă | Rol | Port local |
| --- | --- | --- |
| [`api-gateway`](api-gateway) | Punct de acces pentru clienți, autentificare, rutare și integrarea serviciilor | 8080 |
| [`event-service`](event-service) | Evenimente, locații, locuri, căutare, rezervări și plăți | 8081 |
| [`user-service`](user-service) | Utilizatori, roluri, autentificare și notificări prin email | 8082 |
| [`app-ui`](app-ui) | Interfață React pentru clienți și administrare | 5173 |

Directorul `event-service/frontend` conține și o interfață pentru editarea și vizualizarea locațiilor.

## Tehnologii

- **Backend:** Java 21, Spring Boot, Spring Data JPA, Spring Security, Spring Cloud OpenFeign, Maven.
- **Date și mesagerie:** PostgreSQL, `pg_trgm`, Flyway, Apache Kafka și Redis.
- **Frontend:** React, TypeScript, Vite, Tailwind CSS și Axios.
- **Dezvoltare și testare:** Docker Compose, JUnit, Mockito, Testcontainers, Cypress și Postman.

## Rulare locală

Sunt necesare Java 21, Maven, Node.js compatibil cu Vite 8 și Docker Compose.

1. Pornește infrastructura din rădăcina repository-ului:

   ```sh
   docker compose -f api-gateway/docker-compose.yml up -d
   ```

   Aceasta include PostgreSQL, Kafka, Redis, pgAdmin, Kafka UI și Mailpit.

2. Pornește fiecare serviciu într-un terminal separat, din rădăcina repository-ului:

   ```sh
   mvn -f user-service/pom.xml spring-boot:run
   ```

   ```sh
   mvn -f event-service/pom.xml spring-boot:run
   ```

   ```sh
   mvn -f api-gateway/pom.xml spring-boot:run
   ```

3. Instalează dependențele și pornește interfața:

   ```sh
   cd app-ui
   npm ci
   npm run dev
   ```

4. Accesează aplicația la [localhost:5173](http://localhost:5173). Emailurile locale pot fi consultate în [Mailpit](http://localhost:8025), iar mesajele Kafka în [Kafka UI](http://localhost:8085).

Configurațiile incluse sunt pentru dezvoltare locală. Pentru alt mediu, configurează variabilele de mediu din fișierele `application.yaml`, inclusiv `JWT_SECRET_KEY` (aceeași valoare în gateway și user-service) și `INTERNAL_SHARED_SECRET` (aceeași valoare în gateway și event-service). Un exemplu de configurare SMTP este disponibil în `user-service/src/main/resources/application-local.yaml.example`.

## Verificări disponibile

Testele backend se pot rula cu `mvn -f <serviciu>/pom.xml test`; testele care pornesc contextul Spring necesită infrastructura și configurația corespunzătoare. Pentru frontend, din `app-ui`, sunt disponibile `npm run build`, `npm run lint` și testele Cypress din `cypress/e2e`.
