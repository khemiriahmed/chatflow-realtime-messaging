import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, ElementRef, ViewChild, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatMenuModule } from '@angular/material/menu';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { BehaviorSubject, combineLatest, of } from 'rxjs';
import { catchError, debounceTime, map, startWith, switchMap, tap } from 'rxjs/operators';
import { ConversationDto, MessageDto, PageResponse } from '../../../core/models';
import { AuthService } from '../../../core/services/auth.service';
import { ConversationService } from '../../../core/services/conversation.service';
import { WebSocketService } from '../../../core/services/websocket.service';
import { AvatarComponent } from '../../../shared/components/avatar/avatar.component';
import { EmptyStateComponent } from '../../../shared/components/empty-state/empty-state.component';
import { LoadingStateComponent } from '../../../shared/components/loading-state/loading-state.component';
import { MessageBubbleComponent } from '../../../shared/components/message-bubble/message-bubble.component';
import { TimeAgoPipe } from '../../../shared/pipes/time-ago.pipe';
import { extractApiError } from '../../../shared/utils/api.util';
import {
  conversationSubtitle,
  conversationTitle,
  otherMember,
  truncateText
} from '../../../shared/utils/conversation.util';
import { createClientId } from '../../../shared/utils/id.util';

type ConversationFilter = 'ALL' | 'PRIVATE' | 'GROUP';

@Component({
  selector: 'app-chat-page',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatMenuModule,
    MatTooltipModule,
    AvatarComponent,
    EmptyStateComponent,
    LoadingStateComponent,
    MessageBubbleComponent,
    TimeAgoPipe
  ],
  templateUrl: './chat-page.component.html',
  styleUrl: './chat-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ChatPageComponent {
  private readonly destroyRef = inject(DestroyRef);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly conversationService = inject(ConversationService);

  readonly auth = inject(AuthService);
  readonly ws = inject(WebSocketService);
  readonly maxMessageLength = 4000;
  readonly composerControl = new FormControl('', {
    nonNullable: true,
    validators: [Validators.maxLength(this.maxMessageLength)]
  });
  readonly searchControl = new FormControl('', { nonNullable: true });
  readonly filterSubject = new BehaviorSubject<ConversationFilter>('ALL');
  readonly conversations$ = combineLatest([
    this.conversationService.conversations$,
    this.searchControl.valueChanges.pipe(startWith(''), debounceTime(120)),
    this.filterSubject
  ]).pipe(
    map(([conversations, query, filter]) => {
      const normalizedQuery = query.trim().toLocaleLowerCase('fr-FR');
      return conversations.filter((conversation) => {
        const matchesType = filter === 'ALL' || conversation.type === filter;
        const label = conversationTitle(conversation, this.currentUserId).toLocaleLowerCase('fr-FR');
        const preview = (conversation.lastMessage?.content ?? '').toLocaleLowerCase('fr-FR');
        return matchesType && (normalizedQuery === '' || label.includes(normalizedQuery) || preview.includes(normalizedQuery));
      });
    })
  );

  activeConversationId: number | null = null;
  activeConversation: ConversationDto | null = null;
  messages: MessageDto[] = [];
  loadingConversations = true;
  loadingMessages = false;
  loadingOlder = false;
  hasOlderMessages = false;
  errorMessage = '';
  typing = false;

  private page = 0;
  private subscribedConversationId: number | null = null;
  private typingStopTimer: ReturnType<typeof setTimeout> | null = null;
  private stopTypingTimer: ReturnType<typeof setTimeout> | null = null;
  private lastTypingSentAt = 0;
  private activeFilter: ConversationFilter = 'ALL';

  @ViewChild('thread') threadElement?: ElementRef<HTMLElement>;

  constructor() {
    this.conversationService.refreshConversations().subscribe({
      next: () => (this.loadingConversations = false),
      error: (error: unknown) => {
        this.loadingConversations = false;
        this.errorMessage = extractApiError(error);
      }
    });

    this.route.paramMap
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        switchMap((params) => {
          const rawId = params.get('conversationId');

          if (!rawId || !/^\d+$/.test(rawId)) {
            this.prepareConversation(null);
            return of(null);
          }

          const id = Number(rawId);
          this.prepareConversation(id);
          return this.conversationService.messages(id, 0, 20).pipe(
            tap((response) => this.applyMessages(response)),
            catchError((error: unknown) => {
              this.loadingMessages = false;
              this.errorMessage = extractApiError(error);
              return of(null);
            })
          );
        })
      )
      .subscribe();

    this.ws.messages$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((message) => this.handleIncomingMessage(message));

    this.ws.typing$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((event) => {
        if (event.conversationId === this.activeConversationId) {
          this.showTyping(event.typing);
        }
      });

    this.ws.presence$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((event) => this.conversationService.updatePresence(event));

    this.ws.connected$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((connected) => {
        if (connected && this.activeConversationId !== null) {
          const conversationId = this.activeConversationId;
          this.conversationService.messages(conversationId, 0, 20).subscribe({
            next: (response) => {
              if (this.activeConversationId === conversationId) {
                this.applyMessages(response);
              }
            },
            error: (error: unknown) => (this.errorMessage = extractApiError(error))
          });
        }
      });

    this.conversationService.conversations$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((conversations) => {
        if (this.activeConversationId !== null) {
          this.activeConversation = conversations.find((item) => item.id === this.activeConversationId) ?? null;
        }
      });

    this.destroyRef.onDestroy(() => {
      this.clearTimer('typingStop');
      this.clearTimer('stopTyping');

      if (this.subscribedConversationId !== null) {
        this.ws.unsubscribeFromConversation(this.subscribedConversationId);
      }
    });
  }

  get currentUserId(): number | null {
    return this.auth.currentUser?.id ?? null;
  }

  get remainingCharacters(): number {
    return this.maxMessageLength - this.composerControl.value.length;
  }

  get canSendMessage(): boolean {
    const content = this.composerControl.value.trim();
    return this.activeConversationId !== null && content.length > 0 && content.length <= this.maxMessageLength;
  }

  get typingLabel(): string {
    return this.activeConversation?.type === 'GROUP'
      ? 'Un membre est en train d’écrire…'
      : 'Votre contact est en train d’écrire…';
  }

  conversationLabel(conversation: ConversationDto): string {
    return conversationTitle(conversation, this.currentUserId);
  }

  conversationSubtitle(conversation: ConversationDto): string {
    return conversationSubtitle(conversation, this.currentUserId);
  }

  conversationPreview(conversation: ConversationDto): string {
    const message = conversation.lastMessage;

    if (!message) {
      return 'Aucun message pour le moment';
    }

    const prefix = message.senderId === this.currentUserId ? 'Vous : ' : '';
    return truncateText(`${prefix}${message.content}`, 64);
  }

  contactOnline(conversation: ConversationDto): boolean | null {
    const member = otherMember(conversation, this.currentUserId);
    return member?.online ?? null;
  }

  setFilter(filter: ConversationFilter): void {
    this.activeFilter = filter;
    this.filterSubject.next(filter);
  }

  isFilterActive(filter: ConversationFilter): boolean {
    return this.activeFilter === filter;
  }

  trackByConversation(_index: number, conversation: ConversationDto): number {
    return conversation.id;
  }

  trackByMessage(_index: number, message: MessageDto): string | number {
    return message.clientId ?? message.id;
  }

  openConversation(conversation: ConversationDto): void {
    void this.router.navigate(['/chat', conversation.id]);
  }

  openContacts(): void {
    void this.router.navigate(['/contacts']);
  }

  openGroups(): void {
    void this.router.navigate(['/groups']);
  }

  loadOlderMessages(): void {
    if (this.activeConversationId === null || !this.hasOlderMessages || this.loadingOlder) {
      return;
    }

    const conversationId = this.activeConversationId;
    const nextPage = this.page + 1;
    const previousHeight = this.threadElement?.nativeElement.scrollHeight ?? 0;
    this.loadingOlder = true;

    this.conversationService.messages(conversationId, nextPage, 20).subscribe({
      next: (response) => {
        if (this.activeConversationId === conversationId) {
          const older = [...response.content].reverse();
          this.messages = [...older, ...this.messages];
          this.page = response.page;
          this.hasOlderMessages = !response.last;
        }
        this.loadingOlder = false;
        setTimeout(() => {
          const element = this.threadElement?.nativeElement;
          if (element) {
            element.scrollTop = element.scrollHeight - previousHeight;
          }
        });
      },
      error: (error: unknown) => {
        this.loadingOlder = false;
        this.errorMessage = extractApiError(error);
      }
    });
  }

  onComposerInput(value: string): void {
    if (this.activeConversationId === null) {
      return;
    }

    const now = Date.now();

    if (value.trim() && now - this.lastTypingSentAt > 1500) {
      this.ws.sendTyping(this.activeConversationId, true);
      this.lastTypingSentAt = now;
    }

    this.clearTimer('stopTyping');
    this.stopTypingTimer = setTimeout(() => {
      if (this.activeConversationId !== null) {
        this.ws.sendTyping(this.activeConversationId, false);
      }
    }, 2500);
  }

  onEnter(event: Event): void {
    const keyboardEvent = event as KeyboardEvent;

    if (!keyboardEvent.shiftKey) {
      keyboardEvent.preventDefault();
      this.sendMessage();
    }
  }

  sendMessage(): void {
    const content = this.composerControl.value.trim();

    if (!this.canSendMessage || this.activeConversationId === null) {
      return;
    }

    const conversationId = this.activeConversationId;
    const clientId = createClientId();
    const optimisticMessage: MessageDto = {
      id: -Date.now(),
      conversationId,
      senderId: this.currentUserId ?? 0,
      senderName: this.auth.currentUser?.name ?? 'Moi',
      content,
      createdAt: new Date().toISOString(),
      status: 'PENDING',
      clientId
    };

    this.messages = [...this.messages, optimisticMessage];
    this.errorMessage = '';

    if (!this.ws.sendMessage(conversationId, content, clientId)) {
      this.messages = this.messages.filter((message) => message.clientId !== clientId);
      this.errorMessage = 'La connexion temps réel est indisponible.';
      return;
    }

    this.composerControl.setValue('');
    this.ws.sendTyping(conversationId, false);
    this.scrollToBottom();
  }

  private prepareConversation(id: number | null): void {
    if (this.subscribedConversationId !== null && this.subscribedConversationId !== id) {
      this.ws.unsubscribeFromConversation(this.subscribedConversationId);
      this.subscribedConversationId = null;
    }

    this.activeConversationId = id;
    this.activeConversation =
      id === null ? null : this.conversationService.conversationsValue.find((item) => item.id === id) ?? null;
    this.messages = [];
    this.page = 0;
    this.hasOlderMessages = false;
    this.loadingMessages = id !== null;
    this.errorMessage = '';
    this.typing = false;
    this.composerControl.setValue('');
    this.clearTimer('typingStop');
    this.clearTimer('stopTyping');

    if (id !== null) {
      this.subscribedConversationId = id;
      this.ws.subscribeToConversation(id);
    }
  }

  private applyMessages(response: PageResponse<MessageDto>): void {
    this.messages = [...response.content].reverse();
    this.page = response.page;
    this.hasOlderMessages = !response.last;
    this.loadingMessages = false;
    this.scrollToBottom();

    const lastMessage = this.messages[this.messages.length - 1];
    if (lastMessage && lastMessage.id > 0) {
      this.markAsRead(lastMessage.id);
    }
  }

  private handleIncomingMessage(message: MessageDto): void {
    this.conversationService.updateLastMessage(message.conversationId, message);

    if (message.id > 0 && message.senderId !== this.currentUserId) {
      this.ws.sendDelivered(message.conversationId, message.id);
    }

    if (message.conversationId !== this.activeConversationId) {
      return;
    }

    const existingIndex = this.messages.findIndex(
      (item) => item.id === message.id || (message.clientId !== null && item.clientId === message.clientId)
    );

    if (existingIndex >= 0) {
      this.messages = this.messages.map((item, index) => (index === existingIndex ? message : item));
    } else {
      this.messages = [...this.messages, message];
    }

    this.conversationService.setUnreadCount(message.conversationId, 0);
    this.showTyping(false);

    if (message.id > 0) {
      this.markAsRead(message.id);
    }

    this.scrollToBottom();
  }

  private markAsRead(messageId: number): void {
    if (this.activeConversationId === null) {
      return;
    }

    this.conversationService.markRead(this.activeConversationId, messageId).subscribe({ error: () => undefined });
    this.ws.sendRead(this.activeConversationId, messageId);
  }

  private showTyping(typing: boolean): void {
    this.typing = typing;
    this.clearTimer('typingStop');

    if (typing) {
      this.typingStopTimer = setTimeout(() => (this.typing = false), 3000);
    }
  }

  private scrollToBottom(): void {
    setTimeout(() => {
      const element = this.threadElement?.nativeElement;
      if (element) {
        element.scrollTop = element.scrollHeight;
      }
    });
  }

  private clearTimer(name: 'typingStop' | 'stopTyping'): void {
    if (name === 'typingStop' && this.typingStopTimer) {
      clearTimeout(this.typingStopTimer);
      this.typingStopTimer = null;
    }

    if (name === 'stopTyping' && this.stopTypingTimer) {
      clearTimeout(this.stopTypingTimer);
      this.stopTypingTimer = null;
    }
  }
}
