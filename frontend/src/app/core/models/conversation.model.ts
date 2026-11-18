import { MessageDto } from './message.model';
import { UserDto } from './user.model';

export type ConversationType = 'PRIVATE' | 'GROUP';
export type MemberRole = 'OWNER' | 'ADMIN' | 'MEMBER';

export interface CreateConversationRequest {
  type: ConversationType;
  title: string | null;
  memberIds: number[];
}

export interface AddMemberRequest {
  userId: number;
}

export interface ConversationDto {
  id: number;
  type: ConversationType;
  title: string | null;
  createdAt: string;
  updatedAt: string;
  members: UserDto[];
  memberRoles: Record<number, MemberRole>;
  lastMessage: MessageDto | null;
  unreadCount: number;
}
