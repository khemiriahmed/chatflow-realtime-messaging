# Communication temps réel — WebSocket / STOMP

Point d'entrée : `/ws` (WebSocket natif, sans SockJS). Le frontend Angular utilise `@stomp/stompjs`.

## Connexion authentifiée

Le JWT **n'est jamais placé dans l'URL**. Il est transmis dans l'en-tête natif STOMP de la trame `CONNECT` :

```
CONNECT
Authorization:Bearer eyJ...
accept-version:1.2
host:localhost

^@
```

`JwtChannelInterceptor` vérifie la signature, l'émetteur, l'audience, le type `ACCESS`, l'expiration et l'état actif du compte, puis positionne le principal Spring sur la session. `AuthorizationChannelInterceptor` contrôle ensuite chaque `SUBSCRIBE` et chaque `SEND` : l'appartenance à la conversation est vérifiée côté serveur, indépendamment de la destination demandée.

Une trame `CONNECT` sans jeton valide est rejetée. Les comptes désactivés sont refusés, y compris sur une session déjà ouverte côté client.

## Destinations

| Destination | Sens | Description |
| --- | --- | --- |
| `/app/chat.send` | client → serveur | Envoyer un message `{ conversationId, content, clientId }` |
| `/app/chat.typing` | client → serveur | `{ conversationId, typing }` |
| `/app/chat.delivered` | client → serveur | `{ conversationId, messageId }` — distribution confirmée |
| `/app/chat.read` | client → serveur | `{ conversationId, messageId }` — lecture confirmée |
| `/topic/conversation/{id}` | serveur → abonnés | Messages et mises à jour d'accusés de la conversation |
| `/topic/conversation/{id}/typing` | serveur → abonnés | Indicateur de saisie |
| `/user/queue/messages` | serveur → utilisateur | Copie personnelle des nouveaux messages + notification navigateur |
| `/user/queue/notifications` | serveur → utilisateur | Notifications personnelles (conversations, présence) |
| `/topic/presence` | serveur → abonnés | `{ userId, online, at }` |

L'abonnement à `/topic/conversation/{id}` est refusé si l'utilisateur n'est pas membre. Les destinations `/user/**` sont réservées à la session authentifiée.

## Cycle de vie d'un message

1. Le client publie sur `/app/chat.send` avec un `clientId` unique généré localement.
2. Le serveur déduit l'expéditeur du principal authentifié (un éventuel `senderId` client est ignoré).
3. Le service vérifie l'appartenance, valide le contenu (1 à 4000 caractères), applique l'idempotence par `(sender_id, client_id)` et **persiste le message en base avant toute diffusion**.
4. Le DTO est diffusé sur `/topic/conversation/{id}` et sur `/user/queue/messages` de chaque membre.
5. Le destinataire acquitte la distribution via `/app/chat.delivered`, puis la lecture via `/app/chat.read` ou `POST /api/conversations/{id}/read`. Le serveur met à jour `MessageReceipt` et republie le DTO mis à jour sur le topic.
6. Les clients dédupliquent les événements par `id` (ou `clientId` pour le message optimiste) et mettent à jour l'interface sans rechargement.

## États et multi-appareils

| État | Signification |
| --- | --- |
| `SENT` | Message enregistré côté serveur, aucun accusé reçu. |
| `DELIVERED` | Au moins une session destinataire a reçu le message et envoyé un accusé de distribution. |
| `READ` | Le destinataire a ouvert/consulté la conversation et confirmé la lecture. |

Le DTO retourne toujours l'état le plus avancé : un `DELIVERED` ne peut pas écraser un `READ`. Sur plusieurs appareils, un seul accusé est conservé par couple `(message, utilisateur)` : les sessions partagent donc le même état, qui ne régresse jamais. La présence est maintenue tant qu'au moins une session WebSocket est ouverte pour l'utilisateur ; `lastSeenAt` est mis à jour à la déconnexion de la dernière session.

## Fiabilité

- Reconnexion côté client avec délai progressif : 1 s, 2 s, 4 s, … plafonné à 30 s.
- Le token d'accès est renouvelé avant chaque tentative de reconnexion via `POST /api/auth/refresh`.
- Après reconnexion, le client se réabonne à ses topics et recharge l'historique via `GET /api/conversations/{id}/messages`, ce qui garantit la récupération des messages manqués.
- Le `clientId` rend l'envoi idempotent en cas de double envoi réseau.

## Test manuel multi-utilisateurs

1. Ouvrir deux sessions (deux navigateurs ou un onglet privé), créer deux comptes et démarrer une conversation.
2. Vérifier la réception instantanée, l'indicateur de saisie, la pastille de présence et les accusés.
3. Couper le réseau d'un client, envoyer des messages depuis l'autre, rétablir la connexion et vérifier la récupération de l'historique.
4. Tenter de s'abonner à `/topic/conversation/{id}` d'une conversation étrangère : la session doit être rejetée.
