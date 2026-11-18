export interface UserDto {
  id: number;
  name: string;
  email: string;
  role: string;
  enabled: boolean;
  online: boolean;
  lastSeenAt: string | null;
}
