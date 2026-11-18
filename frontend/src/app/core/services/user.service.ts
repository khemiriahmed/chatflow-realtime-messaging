import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { tap } from 'rxjs/operators';
import {
  ChangePasswordRequest,
  PageResponse,
  UpdateProfileRequest,
  UserDto
} from '../models';
import { ApiService } from './api.service';
import { AuthService } from './auth.service';

@Injectable({ providedIn: 'root' })
export class UserService {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);

  me(): Observable<UserDto> {
    return this.api.get<UserDto>('/api/users/me');
  }

  search(query: string, page: number, size: number): Observable<PageResponse<UserDto>> {
    return this.api.get<PageResponse<UserDto>>('/api/users', { query, page, size });
  }

  getById(id: number): Observable<UserDto> {
    return this.api.get<UserDto>(`/api/users/${id}`);
  }

  updateProfile(payload: UpdateProfileRequest): Observable<UserDto> {
    return this.api.patch<UserDto>('/api/users/me', payload).pipe(tap((user) => this.auth.setCurrentUser(user)));
  }

  changePassword(payload: ChangePasswordRequest): Observable<void> {
    return this.api.post<void>('/api/users/me/password', payload);
  }
}
