import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthResponse, UserDto } from '../models';
import { AuthService } from './auth.service';

describe('AuthService', () => {
  let service: AuthService;
  let http: HttpTestingController;

  const user: UserDto = {
    id: 1,
    name: 'Alice Martin',
    email: 'alice@example.com',
    role: 'USER',
    enabled: true,
    online: false,
    lastSeenAt: null
  };

  const authResponse: AuthResponse = {
    accessToken: 'access-token',
    refreshToken: 'refresh-token',
    tokenType: 'Bearer',
    expiresIn: 900,
    user
  };

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({ imports: [HttpClientTestingModule] });
    service = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    sessionStorage.clear();
  });

  it('stores the session after a successful login', () => {
    service.login({ email: user.email, password: 'password8' }).subscribe();

    const request = http.expectOne('/api/auth/login');
    expect(request.request.method).toBe('POST');
    request.flush(authResponse);

    expect(service.accessToken).toBe('access-token');
    expect(service.refreshToken).toBe('refresh-token');
    expect(service.currentUser?.name).toBe(user.name);
    expect(service.isAuthenticated).toBeTrue();
  });

  it('clears the session after logout even when the server fails', () => {
    service.login({ email: user.email, password: 'password8' }).subscribe();
    http.expectOne('/api/auth/login').flush(authResponse);

    service.logout().subscribe();
    http.expectOne('/api/auth/logout').flush(null, { status: 500, statusText: 'Server Error' });

    expect(service.accessToken).toBeNull();
    expect(service.currentUser).toBeNull();
    expect(service.isAuthenticated).toBeFalse();
  });

  it('refreshes the access token and updates the session', () => {
    service.login({ email: user.email, password: 'password8' }).subscribe();
    http.expectOne('/api/auth/login').flush(authResponse);

    service.refreshAccessToken().subscribe((token) => expect(token).toBe('new-access'));

    const request = http.expectOne('/api/auth/refresh');
    expect(request.request.body).toEqual({ refreshToken: 'refresh-token' });
    request.flush({ ...authResponse, accessToken: 'new-access', refreshToken: 'new-refresh' });

    expect(service.accessToken).toBe('new-access');
    expect(service.refreshToken).toBe('new-refresh');
  });

  it('shares concurrent refresh requests', () => {
    service.login({ email: user.email, password: 'password8' }).subscribe();
    http.expectOne('/api/auth/login').flush(authResponse);

    const first = jasmine.createSpy('first');
    const second = jasmine.createSpy('second');
    service.refreshAccessToken().subscribe(first);
    service.refreshAccessToken().subscribe(second);

    http.expectOne('/api/auth/refresh').flush({ ...authResponse, accessToken: 'shared-access' });

    expect(first).toHaveBeenCalledWith('shared-access');
    expect(second).toHaveBeenCalledWith('shared-access');
  });
});
