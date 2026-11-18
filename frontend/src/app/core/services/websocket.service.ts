import { inject, Injectable, OnDestroy } from '@angular/core';
import { Client, IMessage, StompSubscription } from '@stomp/stompjs';
import { BehaviorSubject, Subject, Subscription } from 'rxjs';
import {
  MessageDto,
  NotificationDto,
  PresenceEvent,
  ReadEvent,
  SendMessageRequest,
  TypingEvent
} from '../models';
import { AuthService } from './auth.service';
import { NotificationService } from './notification.service';

@Injectable({ providedIn: 'root' })
export class WebSocketService implements OnDestroy {
  private readonly auth = inject(AuthService);
  private readonly notifications = inject(NotificationService);
  private readonly messageSubject = new Subject<MessageDto>();
  private readonly typingSubject = new Subject<TypingEvent>();
  private readonly presenceSubject = new Subject<PresenceEvent>();
  private readonly notificationSubject = new Subject<NotificationDto>();
  private readonly connectionStateSubject = new BehaviorSubject<boolean>(false);
  private readonly logoutSubscription: Subscription;

  private client: Client | null = null;
  private readonly subscriptions = new Map<string, StompSubscription>();
  private readonly activeConversations = new Set<number>();
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  private reconnectAttempts = 0;
  private manualDisconnect = false;

  readonly messages$ = this.messageSubject.asObservable();
  readonly typing$ = this.typingSubject.asObservable();
  readonly presence$ = this.presenceSubject.asObservable();
  readonly notifications$ = this.notificationSubject.asObservable();
  readonly connected$ = this.connectionStateSubject.asObservable();

  constructor() {
    this.logoutSubscription = this.auth.loggedOut$.subscribe(() => this.disconnect());
  }

  connect(): void {
    const token = this.auth.accessToken;

    if (!token || this.manualDisconnect || this.client?.active) {
      return;
    }

    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }

    this.manualDisconnect = false;
    this.client = new Client({
      webSocketFactory: () => new WebSocket(this.resolveUrl()),
      connectHeaders: { Authorization: `Bearer ${token}` },
      reconnectDelay: 0,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => {
        this.reconnectAttempts = 0;
        this.connectionStateSubject.next(true);
        this.subscribeTopics();
      },
      onWebSocketClose: () => {
        this.connectionStateSubject.next(false);
        this.clearSubscriptions();
        this.scheduleReconnect();
      },
      onStompError: () => {
        this.connectionStateSubject.next(false);
        this.notifications.error('La connexion temps réel a été interrompue.');
        this.scheduleReconnect();
      }
    });
    this.client.activate();
  }

  disconnect(): void {
    this.manualDisconnect = true;

    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }

    this.clearSubscriptions();

    const client = this.client;
    this.client = null;

    if (client?.active) {
      client.deactivate();
    }

    this.connectionStateSubject.next(false);
  }

  subscribeToConversation(conversationId: number): void {
    this.activeConversations.add(conversationId);
    this.subscribe(`/topic/conversation/${conversationId}`, (frame) => {
      const message = this.parse<MessageDto>(frame);
      if (message) {
        this.messageSubject.next(message);
      }
    });
    this.subscribe(`/topic/conversation/${conversationId}/typing`, (frame) => {
      const event = this.parse<TypingEvent>(frame);
      if (event) {
        this.typingSubject.next(event);
      }
    });
  }

  unsubscribeFromConversation(conversationId: number): void {
    this.activeConversations.delete(conversationId);
    this.unsubscribe(`/topic/conversation/${conversationId}`);
    this.unsubscribe(`/topic/conversation/${conversationId}/typing`);
  }

  sendMessage(conversationId: number, content: string, clientId: string): boolean {
    const payload: SendMessageRequest = { conversationId, content, clientId };
    return this.publish('/app/chat.send', payload);
  }

  sendTyping(conversationId: number, typing: boolean): void {
    const payload: TypingEvent = { conversationId, typing };
    this.publish('/app/chat.typing', payload);
  }

  sendDelivered(conversationId: number, messageId: number): void {
    const payload: ReadEvent = { conversationId, messageId };
    this.publish('/app/chat.delivered', payload);
  }

  sendRead(conversationId: number, messageId: number): void {
    const payload: ReadEvent = { conversationId, messageId };
    this.publish('/app/chat.read', payload);
  }

  ngOnDestroy(): void {
    this.disconnect();
    this.logoutSubscription.unsubscribe();
    this.messageSubject.complete();
    this.typingSubject.complete();
    this.presenceSubject.complete();
    this.notificationSubject.complete();
    this.connectionStateSubject.complete();
  }

  private subscribeTopics(): void {
    this.subscribe('/user/queue/messages', (frame) => {
      const message = this.parse<MessageDto>(frame);
      if (message) {
        this.messageSubject.next(message);
        this.notifications.messageNotification(message.senderName, message.content, message.conversationId);
      }
    });
    this.subscribe('/user/queue/notifications', (frame) => {
      const notification = this.parse<NotificationDto>(frame);
      if (notification) {
        this.notificationSubject.next(notification);
        this.notifications.info(notification.message);
      }
    });
    this.subscribe('/topic/presence', (frame) => {
      const event = this.parse<PresenceEvent>(frame);
      if (event) {
        this.presenceSubject.next(event);
      }
    });
    this.activeConversations.forEach((id) => this.subscribeToConversation(id));
  }

  private subscribe(destination: string, handler: (frame: IMessage) => void): void {
    if (!this.client?.connected || this.subscriptions.has(destination)) {
      return;
    }

    this.subscriptions.set(destination, this.client.subscribe(destination, handler));
  }

  private unsubscribe(destination: string): void {
    this.subscriptions.get(destination)?.unsubscribe();
    this.subscriptions.delete(destination);
  }

  private clearSubscriptions(): void {
    this.subscriptions.forEach((subscription) => subscription.unsubscribe());
    this.subscriptions.clear();
  }

  private publish(destination: string, payload: unknown): boolean {
    if (!this.client?.connected) {
      return false;
    }

    this.client.publish({ destination, body: JSON.stringify(payload) });
    return true;
  }

  private scheduleReconnect(): void {
    if (this.manualDisconnect || this.reconnectTimer) {
      return;
    }

    const delay = Math.min(30000, 1000 * Math.pow(2, this.reconnectAttempts));
    this.reconnectAttempts += 1;

    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null;
      this.auth.refreshAccessToken().subscribe((token) => {
        if (token && !this.manualDisconnect) {
          this.connect();
        }
      });
    }, delay);
  }

  private parse<T>(frame: IMessage): T | null {
    try {
      return JSON.parse(frame.body) as T;
    } catch {
      return null;
    }
  }

  private resolveUrl(): string {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    return `${protocol}//${window.location.host}/ws`;
  }
}
