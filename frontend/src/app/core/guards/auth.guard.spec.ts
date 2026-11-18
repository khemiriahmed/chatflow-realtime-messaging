import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, provideRouter, RouterStateSnapshot, UrlTree } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { UserDto } from '../models';
import { AuthService } from '../services/auth.service';
import { authGuard } from './auth.guard';

describe('authGuard', () => {
  const user: UserDto = {
    id: 1,
    name: 'Alice Martin',
    email: 'alice@example.com',
    role: 'USER',
    enabled: true,
    online: false,
    lastSeenAt: null
  };

  let currentUser: BehaviorSubject<UserDto | null>;

  beforeEach(() => {
    currentUser = new BehaviorSubject<UserDto | null>(null);
    const authStub = {
      currentUser,
      get isAuthenticated(): boolean {
        return currentUser.value !== null;
      }
    };

    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: AuthService, useValue: authStub }]
    });
  });

  function runGuard(): boolean | UrlTree {
    return TestBed.runInInjectionContext(() =>
      authGuard({} as ActivatedRouteSnapshot, { url: '/chat' } as RouterStateSnapshot)
    ) as boolean | UrlTree;
  }

  it('redirects an unauthenticated user to login with the return url', () => {
    const result = runGuard();

    expect(result instanceof UrlTree).toBeTrue();

    if (result instanceof UrlTree) {
      expect(result.toString()).toContain('login');
      expect(result.toString()).toContain('returnUrl');
    }
  });

  it('allows an authenticated user', () => {
    currentUser.next(user);
    expect(runGuard()).toBeTrue();
  });
});
