import { inject, Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';
import { map, tap } from 'rxjs/operators';
import {
  AddMemberRequest,
  ConversationDto,
  CreateConversationRequest,
  MessageDto,
  PageResponse,
  PresenceEvent
} from '../models';
import { sortConversations } from '../../shared/utils/conversation.util';
import { ApiService } from './api.service';
import { AuthService } from './auth.service';

@Injectable({ providedIn: 'root' })
export class ConversationService {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);
  private readonly conversationsSubject = new BehaviorSubject<ConversationDto[]>([]);

  readonly conversations$ = this.conversationsSubject.asObservable();

  get conversationsValue(): ConversationDto[] {
    return this.conversationsSubject.value;
  }

  list(): Observable<PageResponse<ConversationDto>> {
    return this.api.get<PageResponse<ConversationDto>>('/api/conversations', { page: 0, size: 100 });
  }

  refreshConversations(): Observable<ConversationDto[]> {
    return this.list().pipe(
      map((response) => response.content),
      tap((conversations) => this.conversationsSubject.next(sortConversations(conversations)))
    );
  }

  get(id: number): Observable<ConversationDto> {
    return this.api.get<ConversationDto>(`/api/conversations/${id}`);
  }

  messages(id: number, page: number, size: number): Observable<PageResponse<MessageDto>> {
    return this.api.get<PageResponse<MessageDto>>(`/api/conversations/${id}/messages`, { page, size });
  }

  create(payload: CreateConversationRequest): Observable<ConversationDto> {
    return this.api.post<ConversationDto>('/api/conversations', payload).pipe(
      tap((conversation) => this.upsertConversation(conversation))
    );
  }

  startPrivateConversation(userId: number): Observable<ConversationDto> {
    return this.create({ type: 'PRIVATE', title: null, memberIds: [userId] });
  }

  createGroup(title: string, memberIds: number[]): Observable<ConversationDto> {
    return this.create({ type: 'GROUP', title, memberIds });
  }

  addMember(conversationId: number, userId: number): Observable<ConversationDto> {
    const payload: AddMemberRequest = { userId };
    return this.api.post<ConversationDto>(`/api/conversations/${conversationId}/members`, payload).pipe(
      tap((conversation) => this.upsertConversation(conversation))
    );
  }

  removeMember(conversationId: number, userId: number): Observable<void> {
    return this.api.delete<void>(`/api/conversations/${conversationId}/members/${userId}`).pipe(
      tap(() => {
        const current = this.conversationsSubject.value;
        const updated = current.map((conversation) =>
          conversation.id === conversationId
            ? {
                ...conversation,
                members: conversation.members.filter((member) => member.id !== userId)
              }
            : conversation
        );
        this.conversationsSubject.next(updated);
      })
    );
  }

  leave(conversationId: number): Observable<void> {
    return this.api.post<void>(`/api/conversations/${conversationId}/leave`).pipe(
      tap(() => {
        const updated = this.conversationsSubject.value.filter((conversation) => conversation.id !== conversationId);
        this.conversationsSubject.next(updated);
      })
    );
  }

  markRead(conversationId: number, messageId?: number): Observable<void> {
    return this.api.post<void>(`/api/conversations/${conversationId}/read`).pipe(
      tap(() => this.setUnreadCount(conversationId, 0))
    );
  }

  upsertConversation(conversation: ConversationDto): void {
    const current = this.conversationsSubject.value;
    const index = current.findIndex((item) => item.id === conversation.id);
    const updated = index >= 0
      ? current.map((item) => (item.id === conversation.id ? conversation : item))
      : [conversation, ...current];
    this.conversationsSubject.next(sortConversations(updated));
  }

  updateLastMessage(conversationId: number, message: MessageDto): void {
    const currentUserId = this.auth.currentUser?.id ?? null;
    const updated = this.conversationsSubject.value.map((conversation) => {
      if (conversation.id !== conversationId) {
        return conversation;
      }

      const isOwnMessage = message.senderId === currentUserId;
      return {
        ...conversation,
        lastMessage: message,
        updatedAt: message.createdAt,
        unreadCount: isOwnMessage ? conversation.unreadCount : conversation.unreadCount + 1
      };
    });
    this.conversationsSubject.next(sortConversations(updated));
  }

  setUnreadCount(conversationId: number, count: number): void {
    const updated = this.conversationsSubject.value.map((conversation) =>
      conversation.id === conversationId ? { ...conversation, unreadCount: count } : conversation
    );
    this.conversationsSubject.next(updated);
  }

  updatePresence(event: PresenceEvent): void {
    const updated = this.conversationsSubject.value.map((conversation) => ({
      ...conversation,
      members: conversation.members.map((member) =>
        member.id === event.userId ? { ...member, online: event.online, lastSeenAt: event.at } : member
      )
    }));
    this.conversationsSubject.next(updated);
  }
}
