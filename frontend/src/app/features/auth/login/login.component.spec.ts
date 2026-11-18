import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideRouter, Router } from '@angular/router';
import { AuthResponse, UserDto } from '../../../core/models';
import { LoginComponent } from './login.component';

describe('LoginComponent', () => {
  let fixture: ComponentFixture<LoginComponent>;
  let component: LoginComponent;
  let http: HttpTestingController;
  let router: Router;

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

  beforeEach(async () => {
    sessionStorage.clear();

    await TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting(), provideNoopAnimations()]
    }).compileComponents();

    fixture = TestBed.createComponent(LoginComponent);
    component = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    fixture.detectChanges();
  });

  afterEach(() => {
    http.verify();
    sessionStorage.clear();
  });

  it('does not submit an invalid form', () => {
    spyOn(router, 'navigateByUrl');
    component.submit();

    http.expectNone('/api/auth/login');
    expect(component.form.controls.email.touched).toBeTrue();
  });

  it('logs in and redirects to the chat page', () => {
    const navigate = spyOn(router, 'navigateByUrl').and.resolveTo(true);
    component.form.setValue({ email: 'alice@example.com', password: 'password8' });

    component.submit();

    const request = http.expectOne('/api/auth/login');
    expect(request.request.body).toEqual({ email: 'alice@example.com', password: 'password8' });
    request.flush(authResponse);

    expect(navigate).toHaveBeenCalledWith('/chat');
    expect(component.loading).toBeFalse();
  });

  it('shows an error message when authentication fails', () => {
    component.form.setValue({ email: 'alice@example.com', password: 'wrong-password' });
    component.submit();

    http.expectOne('/api/auth/login').flush(
      { timestamp: '2026-11-18T10:00:00Z', status: 401, error: 'Unauthorized', message: 'Identifiants invalides', path: '/api/auth/login', fieldErrors: {} },
      { status: 401, statusText: 'Unauthorized' }
    );

    expect(component.errorMessage).toBe('Identifiants invalides');
    expect(component.loading).toBeFalse();
  });
});
