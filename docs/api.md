# API REST — ChatFlow

Base URL : `http://localhost:8080/api` (en Docker : `http://localhost/api` via Nginx).

Sauf indication contraire, toutes les routes nécessitent l'en-tête `Authorization: Bearer <accessToken>` et renvoient du JSON. Les horodatages sont au format ISO-8601 UTC (`Instant`).

## Authentification

| Méthode | Endpoint | Public | Description | Succès |
| --- | --- | --- | --- | --- |
| POST | `/auth/register` | oui | Créer un compte (rôle `USER`) | `201` + `AuthResponse` |
| POST | `/auth/login` | oui | Ouvrir une session | `200` + `AuthResponse` |
| POST | `/auth/refresh` | oui | Renouveler avec rotation du refresh token | `200` + `AuthResponse` |
| POST | `/auth/logout` | non | Révoquer le refresh token | `204` |

```json
// POST /auth/register
{ "name": "Alice Martin", "email": "alice@example.com", "password": "motdepasse-123" }

// POST /auth/login  — même forme avec email + password
// POST /auth/refresh
{ "refreshToken": "<opaque token>" }
// POST /auth/logout
{ "refreshToken": "<opaque token>" }

// AuthResponse
{
  "accessToken": "eyJ...",
  "refreshToken": "opaque-random-token",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "user": { "id": 1, "name": "Alice Martin", "email": "alice@example.com",
            "role": "USER", "enabled": true, "online": false, "lastSeenAt": null }
}
```

L'email est normalisé en minuscules et unique. Le refresh token est stocké haché (SHA-256), tourné à chaque renouvellement et révoqué à la déconnexion. Un compte désactivé ne peut ni se connecter ni renouveler sa session.

## Utilisateurs

| Méthode | Endpoint | Description | Succès |
| --- | --- | --- | --- |
| GET | `/users/me` | Profil courant | `200` + `UserDto` |
| PATCH | `/users/me` | Modifier le nom | `200` + `UserDto` |
| POST | `/users/me/password` | Changer le mot de passe | `204` |
| GET | `/users?query=&page=0&size=20` | Rechercher par nom ou email | `200` + `PageResponse<UserDto>` |
| GET | `/users/{id}` | Profil public d'un utilisateur | `200` + `UserDto` |

```json
// PATCH /users/me
{ "name": "Alice Dupont" }
// POST /users/me/password
{ "currentPassword": "motdepasse-123", "newPassword": "nouveau-mot-de-passe" }
```

`size` est compris entre 1 et 100 ; `page` commence à 0. `UserDto` ne contient jamais le hash du mot de passe.

## Conversations et messages

| Méthode | Endpoint | Description | Succès |
| --- | --- | --- | --- |
| GET | `/conversations?page=0&size=20` | Conversations de l'utilisateur, triées par activité | `200` + `PageResponse<ConversationDto>` |
| POST | `/conversations` | Créer ou retrouver une conversation | `201` + `ConversationDto` |
| GET | `/conversations/{id}` | Détail d'une conversation | `200` + `ConversationDto` |
| GET | `/conversations/{id}/messages?page=0&size=20` | Historique, du plus récent au plus ancien | `200` + `PageResponse<MessageDto>` |
| POST | `/conversations/{id}/members` | Ajouter un membre (groupe, OWNER/ADMIN) | `201` + `ConversationDto` |
| DELETE | `/conversations/{id}/members/{userId}` | Retirer un membre (groupe, OWNER/ADMIN) | `204` |
| POST | `/conversations/{id}/leave` | Quitter un groupe | `204` |
| POST | `/conversations/{id}/read` | Marquer la conversation comme lue | `204` |

```json
// POST /conversations — conversation privée (retrouve la conversation existante)
{ "type": "PRIVATE", "memberIds": [2] }
// POST /conversations — groupe
{ "type": "GROUP", "title": "Équipe projet", "memberIds": [2, 3, 4] }
// POST /conversations/{id}/members
{ "userId": 5 }
// POST /conversations/{id}/read — sans corps
```

```json
// ConversationDto
{
  "id": 12, "type": "GROUP", "title": "Équipe projet",
  "createdAt": "2026-11-18T09:00:00Z", "updatedAt": "2026-11-18T10:12:34Z",
  "members": [ /* UserDto[] */ ],
  "memberRoles": { "1": "OWNER", "2": "MEMBER" },
  "lastMessage": { "id": 88, "conversationId": 12, "senderId": 1, "senderName": "Alice Martin",
                   "content": "Bonjour à tous", "createdAt": "2026-11-18T10:12:34Z",
                   "status": "READ", "clientId": "..." },
  "unreadCount": 0
}
```

Le statut d'un message vaut `SENT` (persisté), `DELIVERED` (accusé de distribution) ou `READ` (accusé de lecture). Le plus avancé des accusés est retourné. L'expéditeur est toujours déduit du principal authentifié : tout `senderId` envoyé par le client est ignoré.

## Administration (rôle ADMIN)

| Méthode | Endpoint | Description | Succès |
| --- | --- | --- | --- |
| GET | `/admin/users?query=&page=&size=` | Liste paginée des utilisateurs | `200` + `PageResponse<UserDto>` |
| PATCH | `/admin/users/{id}/status?enabled=true|false` | Activer/désactiver un compte | `200` + `UserDto` |
| PATCH | `/admin/users/{id}/role?role=USER|ADMIN` | Attribuer un rôle | `200` + `UserDto` |
| GET | `/admin/stats` | Indicateurs | `200` + `AdminStatsDto` |

```json
{
  "usersCount": 12, "activeUsersCount": 11,
  "conversationsCount": 8, "messagesCount": 143
}
```

## Réponses paginées et erreurs

```json
// PageResponse<T>
{ "content": [], "page": 0, "size": 20, "totalElements": 0, "totalPages": 0, "last": true }

// ApiError
{
  "timestamp": "2026-11-18T10:00:00Z",
  "status": 403,
  "error": "Accès refusé",
  "message": "Vous n'êtes pas membre de cette conversation",
  "path": "/api/conversations/12/messages",
  "fieldErrors": {}
}
```

Codes : `400` requête/validation invalide, `401` token absent ou invalide, `403` droits insuffisants ou non-membre, `404` ressource introuvable, `409` conflit (email déjà utilisé, membre déjà présent), `500` erreur inattendue. Les réponses d'erreur ne contiennent jamais de mot de passe, de token ni de trace d'exécution.

La documentation interactive est disponible sur `/swagger-ui.html` et le descripteur OpenAPI sur `/v3/api-docs`.
