import { HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { HttpInterceptorFn } from '@angular/common/http';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';

export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AuthService);

  if (isPublicAuthRequest(request.url)) {
    return next(request);
  }

  const token = auth.accessToken;
  const authenticatedRequest = token
    ? request.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : request;

  return next(authenticatedRequest).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status !== 401 || !token || isPublicAuthRequest(request.url)) {
        return throwError(() => error);
      }

      return auth.refreshAccessToken().pipe(
        switchMap((refreshedToken) =>
          refreshedToken
            ? next(request.clone({ setHeaders: { Authorization: `Bearer ${refreshedToken}` } }))
            : throwError(() => error)
        )
      );
    })
  );
};

function isPublicAuthRequest(url: string): boolean {
  return url.includes('/auth/login') || url.includes('/auth/register') || url.includes('/auth/refresh');
}
