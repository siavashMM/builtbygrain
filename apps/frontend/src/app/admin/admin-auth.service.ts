import { HttpClient, HttpHeaders, HttpInterceptorFn } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map, tap } from 'rxjs';

interface AdminLoginResponse {
  username: string;
  admin: boolean;
}

interface StoredAdminSession {
  username: string;
  token: string;
}

const SESSION_STORAGE_KEY = 'builtbygrain.admin.session.v2';
const LEGACY_TOKEN_STORAGE_KEY = 'builtbygrain.admin.basicToken';
const LEGACY_USERNAME_STORAGE_KEY = 'builtbygrain.admin.username';

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

  validateSession(): Observable<boolean> {
    return this.http.post<AdminLoginResponse>('/api/admin/auth/login', null).pipe(
      map((response) => response.admin === true)
    );
  }

  logout(): void {
    const storage = this.storage();
    storage?.removeItem(SESSION_STORAGE_KEY);
    storage?.removeItem(LEGACY_TOKEN_STORAGE_KEY);
    storage?.removeItem(LEGACY_USERNAME_STORAGE_KEY);
  }

  isLoggedIn(): boolean {
    return this.getToken() !== null;
  }

  getToken(): string | null {
    return this.getSession()?.token ?? null;
  }

  getUsername(): string | null {
    return this.getSession()?.username ?? null;
  }

  private setSession(username: string, token: string): void {
    const storage = this.storage();
    storage?.setItem(SESSION_STORAGE_KEY, JSON.stringify({ username, token } satisfies StoredAdminSession));
    storage?.removeItem(LEGACY_TOKEN_STORAGE_KEY);
    storage?.removeItem(LEGACY_USERNAME_STORAGE_KEY);
  }

  private getSession(): StoredAdminSession | null {
    const storage = this.storage();
    const raw = storage?.getItem(SESSION_STORAGE_KEY);
    if (!raw) return null;
    try {
      const session = JSON.parse(raw) as Partial<StoredAdminSession>;
      if (typeof session.username === 'string' && typeof session.token === 'string') {
        return { username: session.username, token: session.token };
      }
    } catch {
      // Invalid or obsolete browser state is handled like a signed-out session.
    }
    storage?.removeItem(SESSION_STORAGE_KEY);
    return null;
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
