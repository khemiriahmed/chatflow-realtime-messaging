import { ConversationDto, UserDto } from '../../core/models';

export function sortConversations(conversations: ConversationDto[]): ConversationDto[] {
  return [...conversations].sort((left, right) => {
    const difference = new Date(right.updatedAt).getTime() - new Date(left.updatedAt).getTime();
    return difference === 0 ? right.id - left.id : difference;
  });
}

export function otherMember(conversation: ConversationDto, currentUserId: number | null): UserDto | null {
  return conversation.members.find((member) => member.id !== currentUserId) ?? conversation.members[0] ?? null;
}

export function conversationTitle(conversation: ConversationDto, currentUserId: number | null): string {
  if (conversation.type === 'GROUP' && conversation.title) {
    return conversation.title;
  }

  const member = otherMember(conversation, currentUserId);
  return member?.name ?? conversation.title ?? 'Conversation';
}

export function conversationSubtitle(conversation: ConversationDto, currentUserId: number | null): string {
  if (conversation.type === 'GROUP') {
    return `${conversation.members.length} membre${conversation.members.length > 1 ? 's' : ''}`;
  }

  const member = otherMember(conversation, currentUserId);
  return member?.online ? 'En ligne' : 'Hors ligne';
}

export function truncateText(value: string | null | undefined, maxLength = 60): string {
  if (!value) {
    return '';
  }

  return value.length > maxLength ? `${value.slice(0, maxLength).trimEnd()}…` : value;
}
