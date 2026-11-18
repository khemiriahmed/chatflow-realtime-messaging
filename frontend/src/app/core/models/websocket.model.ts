export interface TypingEvent {
  conversationId: number;
  typing: boolean;
}

export interface ReadEvent {
  conversationId: number;
  messageId: number;
}

export interface PresenceEvent {
  userId: number;
  online: boolean;
  at: string;
}

export interface NotificationDto {
  type: string;
  message: string;
  conversationId: number | null;
  createdAt: string;
}
