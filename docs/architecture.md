# Architecture et décisions de conception

## Périmètre

Monolithe en couches, sans microservices ni broker externe. Une seule base PostgreSQL, un seul backend Spring Boot qui sert l'API REST et le broker STOMP, un frontend Angular 17 servi par Nginx en conteneur.

## Modèle de données

| Table | Rôle | Contraintes clés |
| --- | --- | --- |
| `users` | Comptes | `email` unique, `password_hash` BCrypt, `enabled`, `role` |
| `conversations` | Privée ou groupe | `type`, `title` (groupe), `updated_at` indexé |
| `conversation_members` | Appartenance et rôle dans le groupe | unique `(conversation_id, user_id)` |
| `messages` | Messages persistés | `content` ≤ 4000, unique `(sender_id, client_id)` |
| `message_receipts` | Accusés par destinataire | unique `(message_id, user_id)`, statut `DELIVERED`/`READ` |
| `refresh_tokens` | Cycle de vie des refresh tokens | hash unique, `expires_at`, `revoked_at` |

Index : `(user_id)` sur les membres, `(conversation_id, created_at)` sur les messages, `(user_id)` sur les accusés et les tokens. Les migrations sont versionnées avec Flyway (`V1__init.sql`). En tests, H2 remplace PostgreSQL avec `ddl-auto=create-drop`.

Le choix d'un rôle unique par utilisateur (`USER`/`ADMIN`) plutôt qu'une table `roles` many-to-many est volontaire : le cahier des charges autorise explicitement cette option et elle suffit au périmètre. Les rôles de groupe sont, eux, portés par `conversation_members` (`OWNER`/`ADMIN`/`MEMBER`).

## Règles métier appliquées serveur

1. Email unique et normalisé en minuscules.
2. Accès à un message ou à un historique réservé aux membres de la conversation.
3. Expéditeur déduit du principal authentifié, jamais du client.
4. Conversation privée unique pour une paire d'utilisateurs.
5. Gestion des membres réservée à `OWNER`/`ADMIN` ; le dernier propriétaire ne peut pas quitter le groupe.
6. Persistance avant diffusion.
7. Horodatages serveur en UTC (`Instant`).
8. Refresh token opaque, stocké haché, rotation à chaque usage, révocation à la déconnexion.
9. Aucun secret ni stack trace dans les réponses ou les logs.
10. Un compte désactivé est refusé sur HTTP et sur STOMP CONNECT.

## Sécurité

- `BCryptPasswordEncoder` pour les mots de passe ; aucun hash n'est exposé dans les DTO.
- JWT signé HMAC, avec `iss`, `aud`, `sub`, `typ` et expiration courte (15 min par défaut). Le secret est lu depuis l'environnement.
- Filtre HTTP stateless : seules l'inscription, la connexion et le renouvellement sont publics ; `/api/admin/**` exige `ROLE_ADMIN`.
- CORS : origines explicitement configurées, pas de wildcard avec `credentials`.
- STOMP : authentification sur `CONNECT` via en-tête `Authorization`, vérification de l'appartenance sur `SUBSCRIBE` et `SEND`.
- GlobalExceptionHandler : format d'erreur unique, aucun détail interne.
- Actuator n'expose que `/actuator/health`.

## Choix et compromis assumés

- **Refresh token dans le corps de réponse** et `sessionStorage` côté navigateur : parcours pédagogique simple. Un cookie `HttpOnly`/`Secure`/`SameSite` serait préférable en production ; voir le README.
- **H2 pour les tests automatisés** plutôt que Testcontainers : la suite de tests reste exécutable sans démon Docker, tout en utilisant un mode de compatibilité PostgreSQL. Le schéma PostgreSQL reste validé au démarrage de l'application (`ddl-auto: validate`).
- **Accusés de distribution** envoyés explicitement par le client (`/app/chat.delivered`) : cela rend la transition `SENT → DELIVERED → READ` observable et testable sans dépendre de la présence d'un abonnement actif.
- **Services Angulaires standalone** et chargement différé des routes : bundle initial réduit, tests isolés.
- **Mode sombre** : hors périmètre de la v1 (évolution visuelle optionnelle du cahier des charges).

## Qualité et livraison

- Backend : 35 tests JUnit 5 / Mockito / MockMvc couvrant auth, permissions, services, JWT et intercepteurs STOMP.
- Frontend : tests Jasmine/Karma pour AuthService, guard, intercepteur, composant de bulle et formulaire de connexion ; `npm run typecheck` et `npm run build` en CI.
- Docker Compose : PostgreSQL avec volume persistant et healthcheck, backend avec healthcheck Actuator, frontend Nginx avec proxy `/api` et `/ws`.
- CI GitHub Actions : `backend` (`mvn -B verify`) puis `frontend` (`npm ci`, typecheck, tests headless, build).
