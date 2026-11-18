import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export type QueryValue = string | number | boolean | null | undefined;
export type QueryParams = Record<string, QueryValue>;

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);

  get<T>(path: string, params?: QueryParams): Observable<T> {
    return this.http.get<T>(path, { params: this.toHttpParams(params) });
  }

  post<T>(path: string, body?: unknown): Observable<T> {
    return this.http.post<T>(path, body ?? {});
  }

  patch<T>(path: string, body?: unknown, params?: QueryParams): Observable<T> {
    return this.http.patch<T>(path, body ?? {}, { params: this.toHttpParams(params) });
  }

  delete<T>(path: string, params?: QueryParams): Observable<T> {
    return this.http.delete<T>(path, { params: this.toHttpParams(params) });
  }

  private toHttpParams(params?: QueryParams): HttpParams {
    let result = new HttpParams();

    Object.entries(params ?? {}).forEach(([key, value]) => {
      if (value !== null && value !== undefined && value !== '') {
        result = result.set(key, String(value));
      }
    });

    return result;
  }
}
