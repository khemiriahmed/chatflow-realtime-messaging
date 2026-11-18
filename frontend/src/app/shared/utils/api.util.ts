import { HttpErrorResponse } from '@angular/common/http';
import { ApiError } from '../../core/models';

export function extractApiError(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    const apiError = error.error as Partial<ApiError> | null;

    if (apiError?.message) {
      const fieldErrors = Object.values(apiError.fieldErrors ?? {});
      return fieldErrors.length > 0 ? `${apiError.message} ${fieldErrors.join(' ')}` : apiError.message;
    }

    if (apiError?.error) {
      return apiError.error;
    }

    return error.status === 0 ? 'Serveur indisponible.' : 'Une erreur est survenue.';
  }

  return 'Une erreur est survenue.';
}
