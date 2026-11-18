# ChatFlow

Application web de messagerie instantanée full-stack : **Spring Boot 3 / Java 21** côté backend, **Angular 17** côté frontend, communications **REST + WebSocket/STOMP**, persistance **PostgreSQL**, sécurité **JWT + rôles**.

Le projet suit le cahier des charges disponible dans [`docs/Cahier_des_charges_ChatFlow_v2_Design.pdf`](docs/Cahier_des_charges_ChatFlow_v2_Design.pdf).

## Sommaire

- [Architecture](#architecture)
- [Prérequis](#prérequis)
- [Démarrage rapide avec Docker](#démarrage-rapide-avec-docker)
- [Démarrage en local sans Docker](#démarrage-en-local-sans-docker)
- [Configuration](#configuration)
- [Compte administrateur](#compte-administrateur)
- [Tests et qualité](#tests-et-qualité)
- [Structure du dépôt](#structure-du-dépôt)
- [Documentation complémentaire](#documentation-complémentaire)
- [Choix techniques et limites](#choix-techniques-et-limites)

## Architecture

```
Navigateur (Angular 17)
   │  HTTP /api/**            │  WebSocket /ws (STOMP)
   ▼                          ▼
        Spring Boot 3 (monolithe en couches)
        Controller → Service → Repository → PostgreSQL
              │         │
              │         └── règles métier, transactions, événements STOMP
              └── Spring Security + JWT (HTTP et STOMP CONNECT)
```

Couches backend : `config`, `controller`, `websocket`, `service`, `repository`, `entity`, `dto`, `mapper`, `security`, `exception` (package racine `com.ahmed.chatflow`).

Couches frontend : `core` (modèles, services, guards, intercepteur), `shared` (composants, pipes, utilitaires), `features` (auth, chat, contacts, groupes, profil, admin), `layouts`.

Fonctionnalités livrées :

- inscription, connexion, renouvellement avec rotation des refresh tokens, déconnexion ;
- profil, changement de mot de passe, recherche d'utilisateurs paginée ;
- conversations privées sans doublon, groupes, rôles `OWNER`/`ADMIN`/`MEMBER`, gestion des membres ;
- historique paginé, envoi temps réel, accusés `SENT`/`DELIVERED`/`READ`, indicateur de saisie ;
- présence en ligne/hors ligne, notifications personnelles, reconnexion avec backoff exponentiel ;
- administration : activation/désactivation, attribution des rôles, indicateurs ;
- documentation OpenAPI, healthcheck Actuator, migrations Flyway.

## Prérequis

| Outil | Version recommandée |
| --- | --- |
| Docker + Docker Compose | Docker 24+ / Compose v2 |
| JDK | 21 (le wrapper Maven cible Java 21) |
| Maven | wrapper inclus (`backend/mvnw`) |
| Node.js / npm | 20 LTS (Angular 17) |

## Démarrage rapide avec Docker

```bash
cp .env.example .env
# Remplacer impérativement JWT_SECRET (openssl rand -base64 48) et DB_PASSWORD dans .env
docker compose up --build
```

- Interface : <http://localhost>
- API : <http://localhost:8080/api>
- Swagger UI : <http://localhost:8080/swagger-ui.html>
- Health : <http://localhost:8080/actuator/health>
- PostgreSQL : `localhost:5432` (base `chatflow`)

Le conteneur `backend` attend que PostgreSQL soit prêt ; le frontend attend le healthcheck du backend. Les données sont conservées dans le volume nommé `postgres_data`.

Arrêt et nettoyage :

```bash
docker compose down
docker compose down -v   # supprime aussi les données
```

## Démarrage en local sans Docker

Terminal 1 — PostgreSQL (exemple avec Docker) :

```bash
docker run --name chatflow-db -e POSTGRES_DB=chatflow -e POSTGRES_USER=chatflow \
  -e POSTGRES_PASSWORD=chatflow -p 5432:5432 postgres:16-alpine
```

Terminal 2 — backend :

```bash
cd backend
# Exporter JWT_SECRET, DB_URL, DB_USERNAME et DB_PASSWORD (voir .env.example)
./mvnw spring-boot:run        # Windows : .\mvnw.cmd spring-boot:run
```

Terminal 3 — frontend :

```bash
cd frontend
npm ci
npm start                     # http://localhost:4200, proxy /api et /ws vers :8080
```

## Configuration

Toutes les variables peuvent être définies dans `.env` (Docker) ou dans l'environnement du processus. Voir [`.env.example`](.env.example).

| Variable | Défaut | Rôle |
| --- | --- | --- |
| `DB_URL` | `jdbc:postgresql://localhost:5432/chatflow` | URL JDBC |
| `DB_USERNAME` / `DB_PASSWORD` | `chatflow` / `chatflow` | Identifiants PostgreSQL |
| `JWT_SECRET` | obligatoire | Clé Base64 de signature (aucune valeur par défaut : le backend refuse de démarrer sans elle) |
| `JWT_ACCESS_EXPIRATION` | `900s` | Durée de vie du token d'accès |
| `JWT_REFRESH_EXPIRATION` | `7d` | Durée de vie du refresh token |
| `JWT_ISSUER` / `JWT_AUDIENCE` | `chatflow` / `chatflow-web` | Claims vérifiés à chaque requête |
| `CORS_ALLOWED_ORIGINS` | `http://localhost,http://localhost:4200` | Origines autorisées (séparées par des virgules) |
| `SERVER_PORT` | `8080` | Port du backend |

Le secret JWT doit être généré par exemple avec `openssl rand -base64 48`. En production, fournir les secrets via un gestionnaire de secrets, jamais via Git. HTTPS/WSS sont requis derrière un reverse proxy.

## Compte administrateur

Aucun compte n'est pré-créé (aucun mot de passe par défaut). Procédure recommandée :

1. Démarrer l'application et créer un compte depuis `/register` avec l'email `admin@chatflow.local` (ou adapter le SQL ci-dessous).
2. Promouvoir ce compte avec [`docs/seed-admin.sql`](docs/seed-admin.sql) :

```bash
docker compose exec -T postgres psql -U chatflow -d chatflow -f - < docs/seed-admin.sql
```

3. Se reconnecter : le rôle `ADMIN` est alors présent dans le JWT et le menu d'administration devient accessible.

## Tests et qualité

```bash
cd backend && ./mvnw test            # 35 tests JUnit 5 / Mockito / MockMvc (H2)
cd frontend && npm ci && npm test -- --watch=false --browsers=ChromeHeadless
cd frontend && npm run typecheck && npm run build
```

Les tests backend utilisent H2 en mode PostgreSQL : aucune dépendance à un serveur PostgreSQL externe n'est nécessaire pour `mvn test`. Le schéma de production est validé par Hibernate (`ddl-auto: validate`) et géré par Flyway.

CI GitHub Actions : `.github/workflows/ci.yml` exécute les tests backend puis le typecheck, les tests headless et le build frontend.

## Structure du dépôt

```
chatflow/
├── backend/            # Spring Boot 3, Maven wrapper, Flyway, tests JUnit
├── frontend/           # Angular 17 standalone, Material, SCSS, tests Jasmine
├── docker/             # nginx.conf
├── docs/               # cahier des charges, API, WebSocket, architecture, seed admin
├── .github/workflows/  # CI
├── .env.example
├── docker-compose.yml
└── README.md
```

## Documentation complémentaire

- [API REST](docs/api.md) — endpoints, payloads, codes de réponse, format des erreurs.
- [WebSocket / STOMP](docs/websocket.md) — handshake authentifié, destinations, cycle de vie d'un message, multi-appareils.
- [Architecture et décisions](docs/architecture.md) — modèle de données, règles métier, choix et compromis.

## Choix techniques et limites

- Le refresh token est renvoyé dans le corps de la réponse et conservé dans `sessionStorage` côté navigateur : cela simplifie le parcours pédagogique. En production, préférer un cookie `HttpOnly` + `Secure` + `SameSite` et une protection CSRF adaptée.
- Les tokens sont en mémoire côté serveur uniquement sous forme de hash pour les refresh tokens ; la révocation d'un compte est vérifiée à chaque requête HTTP et à chaque connexion STOMP.
- Le statut `DELIVERED` est enregistré quand le destinataire acquitte la réception via `/app/chat.delivered` ; `READ` est posé explicitement à l'ouverture de la conversation. Un accusé ne peut jamais faire régresser le statut.
- Le mode sombre, les pièces jointes, les appels et le chiffrement de bout en bout sont hors périmètre de la v1, conformément au cahier des charges.
