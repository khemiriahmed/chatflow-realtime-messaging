export type ReceiptStatus = 'DELIVERED' | 'READ';

export interface SendMessageRequest {
  conversationId: number;
  content: string;
  clientId: string;
}

export interface MessageDto {
  id: number;
  conversationId: number;
  senderId: number;
  senderName: string;
  content: string;
  createdAt: string;
  status: string;
  clientId: string | null;
}
