import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { AuthService } from '../services/auth.service';
import { authInterceptor } from './auth.interceptor';

describe('authInterceptor', () => {
  let client: HttpClient;
  let http: HttpTestingController;
  let authStub: { accessToken: string | null; refreshAccessToken: jasmine.Spy };

  beforeEach(() => {
    authStub = {
      accessToken: 'old-token',
      refreshAccessToken: jasmine.createSpy('refreshAccessToken')
    };

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: authStub }
      ]
    });

    client = TestBed.inject(HttpClient);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('adds the bearer token to authenticated requests', () => {
    client.get('/api/users/me').subscribe();

    const request = http.expectOne('/api/users/me');
    expect(request.request.headers.get('Authorization')).toBe('Bearer old-token');
    request.flush({});
  });

  it('refreshes the token and retries the request after a 401', () => {
    authStub.refreshAccessToken.and.returnValue(of('new-token'));

    client.get('/api/users/me').subscribe();

    http.expectOne('/api/users/me').flush(null, { status: 401, statusText: 'Unauthorized' });

    const retry = http.expectOne('/api/users/me');
    expect(retry.request.headers.get('Authorization')).toBe('Bearer new-token');
    expect(authStub.refreshAccessToken).toHaveBeenCalled();
    retry.flush({});
  });

  it('does not attach a token to public auth requests', () => {
    client.post('/api/auth/login', {}).subscribe();

    const request = http.expectOne('/api/auth/login');
    expect(request.request.headers.has('Authorization')).toBeFalse();
    request.flush({});
  });
});
