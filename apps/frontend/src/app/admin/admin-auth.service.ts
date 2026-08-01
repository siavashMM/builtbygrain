import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, finalize, map, shareReplay, switchMap, tap } from 'rxjs';

interface AdminLoginResponse {
  username: string;
  admin: boolean;
}

interface CsrfResponse {
  headerName: string;
  parameterName: string;
  token: string;
}

@Injectable({ providedIn: 'root' })
export class AdminAuthService {
  private readonly http = inject(HttpClient);
  private readonly currentUsername = signal<string | null>(null);
  private csrfRequest: Observable<CsrfResponse> | null = null;

  login(username: string, password: string): Observable<AdminLoginResponse> {
    return this.ensureCsrf().pipe(
      switchMap(() => this.http.post<AdminLoginResponse>('/api/admin/auth/login', { username, password })),
      tap(response => this.currentUsername.set(response.admin ? response.username : null)),
      switchMap(response => this.ensureCsrf(true).pipe(map(() => response)))
    );
  }

  validateSession(): Observable<boolean> {
    return this.http.get<AdminLoginResponse>('/api/admin/auth/session').pipe(
      tap(response => this.currentUsername.set(response.admin ? response.username : null)),
      map(response => response.admin === true)
    );
  }

  logout(): Observable<void> {
    return this.http.post<void>('/api/admin/auth/logout', null).pipe(
      switchMap(() => this.ensureCsrf(true)),
      map(() => undefined),
      finalize(() => this.clear())
    );
  }

  changePassword(currentPassword: string, newPassword: string): Observable<void> {
    return this.http.post<void>('/api/admin/auth/password', { currentPassword, newPassword }).pipe(
      switchMap(() => this.ensureCsrf(true)),
      tap(() => this.clear()),
      map(() => undefined)
    );
  }

  clear(): void {
    this.currentUsername.set(null);
  }

  getUsername(): string | null {
    return this.currentUsername();
  }

  private ensureCsrf(refresh = false): Observable<CsrfResponse> {
    if (refresh || this.csrfRequest === null) {
      this.csrfRequest = this.http.get<CsrfResponse>('/api/admin/auth/csrf').pipe(
        shareReplay({ bufferSize: 1, refCount: false })
      );
    }
    return this.csrfRequest;
  }
}
