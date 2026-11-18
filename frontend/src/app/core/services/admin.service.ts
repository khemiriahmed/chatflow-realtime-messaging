import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { AdminStatsDto, PageResponse, UserDto } from '../models';
import { ApiService } from './api.service';

@Injectable({ providedIn: 'root' })
export class AdminService {
  private readonly api = inject(ApiService);

  stats(): Observable<AdminStatsDto> {
    return this.api.get<AdminStatsDto>('/api/admin/stats');
  }

  users(query: string, page: number, size: number): Observable<PageResponse<UserDto>> {
    return this.api.get<PageResponse<UserDto>>('/api/admin/users', { query, page, size });
  }

  setStatus(id: number, enabled: boolean): Observable<UserDto> {
    return this.api.patch<UserDto>(`/api/admin/users/${id}/status`, {}, { enabled });
  }

  changeRole(id: number, role: string): Observable<UserDto> {
    return this.api.patch<UserDto>(`/api/admin/users/${id}/role`, {}, { role });
  }
}
