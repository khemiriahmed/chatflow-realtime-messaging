import { inject, Injectable } from '@angular/core';
import { BehaviorSubject, Observable, of, Subject } from 'rxjs';
import { catchError, finalize, map, shareReplay, tap } from 'rxjs/operators';
import {
  AuthResponse,
  ChangePasswordRequest,
  LoginRequest,
  LogoutRequest,
  RefreshRequest,
  RegisterRequest,
  UpdateProfileRequest,
  UserDto
} from '../models';
import { ApiService } from './api.service';

const ACCESS_TOKEN_KEY = 'chatflow.accessToken';
const REFRESH_TOKEN_KEY = 'chatflow.refreshToken';
const TOKEN_TYPE_KEY = 'chatflow.tokenType';
const EXPIRES_IN_KEY = 'chatflow.expiresIn';
const USER_KEY = 'chatflow.user';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly api = inject(ApiService);
  private readonly currentUserSubject = new BehaviorSubject<UserDto | null>(this.readStoredUser());
  private readonly loggedOutSubject = new Subject<void>();
  private readonly sessionExpiredSubject = new Subject<void>();
  private refreshInProgress$: Observable<string | null> | null = null;

  readonly currentUser$ = this.currentUserSubject.asObservable();
  readonly loggedOut$ = this.loggedOutSubject.asObservable();
  readonly sessionExpired$ = this.sessionExpiredSubject.asObservable();

  get currentUser(): UserDto | null {
    return this.currentUserSubject.value;
  }

  get accessToken(): string | null {
    return sessionStorage.getItem(ACCESS_TOKEN_KEY);
  }

  get refreshToken(): string | null {
    return sessionStorage.getItem(REFRESH_TOKEN_KEY);
  }

  get isAuthenticated(): boolean {
    return Boolean(this.accessToken && this.currentUser);
  }

  get isAdmin(): boolean {
    return this.currentUser?.role === 'ADMIN';
  }

  register(payload: RegisterRequest): Observable<AuthResponse> {
    return this.api.post<AuthResponse>('/api/auth/register', payload);
  }

  login(payload: LoginRequest): Observable<AuthResponse> {
    return this.api.post<AuthResponse>('/api/auth/login', payload).pipe(tap((response) => this.storeSession(response)));
  }

  refresh(): Observable<AuthResponse> {
    const payload: RefreshRequest = { refreshToken: this.refreshToken ?? '' };
    return this.api.post<AuthResponse>('/api/auth/refresh', payload).pipe(tap((response) => this.storeSession(response)));
  }

  logout(): Observable<void> {
    const payload: LogoutRequest = { refreshToken: this.refreshToken ?? '' };
    const request$ = this.refreshToken ? this.api.post<void>('/api/auth/logout', payload) : of<void>(undefined);

    return request$.pipe(
      catchError(() => of<void>(undefined)),
      tap(() => this.clearSession())
    );
  }

  me(): Observable<UserDto> {
    return this.api.get<UserDto>('/api/users/me').pipe(tap((user) => this.setCurrentUser(user)));
  }

  updateProfile(payload: UpdateProfileRequest): Observable<UserDto> {
    return this.api.patch<UserDto>('/api/users/me', payload).pipe(tap((user) => this.setCurrentUser(user)));
  }

  changePassword(payload: ChangePasswordRequest): Observable<void> {
    return this.api.post<void>('/api/users/me/password', payload);
  }

  refreshAccessToken(): Observable<string | null> {
    const token = this.refreshToken;

    if (!token) {
      this.clearSession(true);
      return of(null);
    }

    if (!this.refreshInProgress$) {
      const payload: RefreshRequest = { refreshToken: token };
      this.refreshInProgress$ = this.api.post<AuthResponse>('/api/auth/refresh', payload).pipe(
        tap((response) => this.storeSession(response)),
        map((response) => response.accessToken),
        catchError(() => {
          this.clearSession(true);
          return of(null);
        }),
        shareReplay({ bufferSize: 1, refCount: false }),
        finalize(() => {
          this.refreshInProgress$ = null;
        })
      );
    }

    return this.refreshInProgress$;
  }

  setCurrentUser(user: UserDto): void {
    this.currentUserSubject.next(user);
    sessionStorage.setItem(USER_KEY, JSON.stringify(user));
  }

  clearSession(expired = false): void {
    sessionStorage.removeItem(ACCESS_TOKEN_KEY);
    sessionStorage.removeItem(REFRESH_TOKEN_KEY);
    sessionStorage.removeItem(TOKEN_TYPE_KEY);
    sessionStorage.removeItem(EXPIRES_IN_KEY);
    sessionStorage.removeItem(USER_KEY);
    this.currentUserSubject.next(null);
    this.loggedOutSubject.next();

    if (expired) {
      this.sessionExpiredSubject.next();
    }
  }

  private storeSession(response: AuthResponse): void {
    sessionStorage.setItem(ACCESS_TOKEN_KEY, response.accessToken);
    sessionStorage.setItem(REFRESH_TOKEN_KEY, response.refreshToken);
    sessionStorage.setItem(TOKEN_TYPE_KEY, response.tokenType);
    sessionStorage.setItem(EXPIRES_IN_KEY, String(response.expiresIn));
    this.setCurrentUser(response.user);
  }

  private readStoredUser(): UserDto | null {
    try {
      const raw = sessionStorage.getItem(USER_KEY);
      return raw ? (JSON.parse(raw) as UserDto) : null;
    } catch {
      return null;
    }
  }
}
