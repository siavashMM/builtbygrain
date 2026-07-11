import { HttpClient, HttpHeaders, HttpInterceptorFn } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, tap } from 'rxjs';

interface AdminLoginResponse {
  username: string;
  admin: boolean;
}

const TOKEN_STORAGE_KEY = 'builtbygrain.admin.basicToken';
const USERNAME_STORAGE_KEY = 'builtbygrain.admin.username';

@Injectable({
  providedIn: 'root'
})
export class AdminAuthService {
  private readonly http = inject(HttpClient);

  login(username: string, password: string): Observable<AdminLoginResponse> {
    const token = globalThis.btoa(`${username}:${password}`);
    const headers = new HttpHeaders({ Authorization: `Basic ${token}` });

    return this.http.post<AdminLoginResponse>('/api/admin/auth/login', null, { headers }).pipe(
      tap((response) => {
        if (response.admin) {
          this.setSession(username, token);
        }
      })
    );
  }

  logout(): void {
    this.storage()?.removeItem(TOKEN_STORAGE_KEY);
    this.storage()?.removeItem(USERNAME_STORAGE_KEY);
  }

  isLoggedIn(): boolean {
    return this.getToken() !== null;
  }

  getToken(): string | null {
    return this.storage()?.getItem(TOKEN_STORAGE_KEY) ?? null;
  }

  getUsername(): string | null {
    return this.storage()?.getItem(USERNAME_STORAGE_KEY) ?? null;
  }

  private setSession(username: string, token: string): void {
    this.storage()?.setItem(TOKEN_STORAGE_KEY, token);
    this.storage()?.setItem(USERNAME_STORAGE_KEY, username);
  }

  private storage(): Storage | null {
    return typeof globalThis.sessionStorage === 'undefined' ? null : globalThis.sessionStorage;
  }
}

export const adminAuthInterceptor: HttpInterceptorFn = (request, next) => {
  if (!request.url.startsWith('/api/admin/') || request.headers.has('Authorization')) {
    return next(request);
  }

  const token = inject(AdminAuthService).getToken();
  if (token === null) {
    return next(request);
  }

  return next(request.clone({
    setHeaders: {
      Authorization: `Basic ${token}`
    }
  }));
};
