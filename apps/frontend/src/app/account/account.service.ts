import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, catchError, finalize, map, of, shareReplay, switchMap, tap, throwError } from 'rxjs';

export interface CustomerProfile {
  id: number;
  email: string;
  firstName: string;
  lastName: string;
  phone: string | null;
  locale: string;
  emailVerified: boolean;
  passwordSet: boolean;
  accountStatus: string;
  createdAt: string;
}

export interface CustomerAddress {
  id: number;
  recipientName: string;
  company: string | null;
  street: string;
  houseNumber: string;
  addressLine2: string | null;
  postalCode: string;
  city: string;
  region: string | null;
  countryCode: string;
  phone: string | null;
  defaultShipping: boolean;
  defaultBilling: boolean;
  createdAt: string;
  updatedAt: string;
}

export type AddressInput = Omit<CustomerAddress, 'id' | 'createdAt' | 'updatedAt'>;

export interface SocialProviders {
  google: boolean;
  apple: boolean;
}

interface AuthResponse {
  customer: CustomerProfile;
  returnUrl: string;
}

interface CsrfResponse {
  headerName: string;
  parameterName: string;
  token: string;
}

@Injectable({ providedIn: 'root' })
export class AccountService {
  private readonly http = inject(HttpClient);
  private readonly currentCustomer = signal<CustomerProfile | null>(null);
  private csrfRequest: Observable<CsrfResponse> | null = null;
  private sessionRequest: Observable<boolean> | null = null;
  private socialProvidersRequest: Observable<SocialProviders> | null = null;

  readonly customer = this.currentCustomer.asReadonly();

  register(input: {
    email: string;
    password: string;
    firstName: string;
    lastName: string;
    locale: string;
    returnUrl: string | null;
  }): Observable<AuthResponse> {
    return this.ensureCsrf().pipe(
      switchMap(() => this.http.post<AuthResponse>('/api/account/auth/register', input)),
      tap(response => this.setCustomer(response.customer)),
      switchMap(response => this.ensureCsrf(true).pipe(map(() => response)))
    );
  }

  login(email: string, password: string, returnUrl: string | null): Observable<AuthResponse> {
    return this.ensureCsrf().pipe(
      switchMap(() => this.http.post<AuthResponse>('/api/account/auth/login', { email, password, returnUrl })),
      tap(response => this.setCustomer(response.customer)),
      switchMap(response => this.ensureCsrf(true).pipe(map(() => response)))
    );
  }

  availableSocialProviders(): Observable<SocialProviders> {
    if (this.socialProvidersRequest === null) {
      this.socialProvidersRequest = this.http.get<{ socialProviders: SocialProviders }>(
        '/api/public/checkout/config'
      ).pipe(
        map(config => config.socialProviders),
        shareReplay({ bufferSize: 1, refCount: false })
      );
    }
    return this.socialProvidersRequest;
  }

  validateSession(): Observable<boolean> {
    return this.restoreSessionRequest(true);
  }

  restoreSession(): Observable<boolean> {
    if (this.currentCustomer() !== null) return of(true);
    return this.restoreSessionRequest();
  }

  logout(): Observable<void> {
    return this.http.post<void>('/api/account/auth/logout', null).pipe(
      switchMap(() => this.ensureCsrf(true)),
      map(() => undefined),
      finalize(() => this.clear())
    );
  }

  forgotPassword(email: string): Observable<{ message: string }> {
    return this.ensureCsrf().pipe(
      switchMap(() => this.http.post<{ message: string }>('/api/account/auth/forgot-password', { email }))
    );
  }

  resetPassword(token: string, newPassword: string): Observable<{ message: string }> {
    return this.ensureCsrf().pipe(
      switchMap(() => this.http.post<{ message: string }>('/api/account/auth/reset-password', { token, newPassword }))
    );
  }

  changePassword(currentPassword: string, newPassword: string): Observable<void> {
    return this.withSessionFailure(this.http.post<void>('/api/account/auth/password', { currentPassword, newPassword })).pipe(
      tap(() => this.clear())
    );
  }

  profile(): Observable<CustomerProfile> {
    return this.withSessionFailure(this.http.get<CustomerProfile>('/api/account/profile')).pipe(
      tap(customer => this.setCustomer(customer))
    );
  }

  updateProfile(input: Pick<CustomerProfile, 'email' | 'firstName' | 'lastName' | 'phone' | 'locale'>):
    Observable<CustomerProfile> {
    return this.withSessionFailure(this.http.put<CustomerProfile>('/api/account/profile', input)).pipe(
      tap(customer => this.setCustomer(customer)),
      switchMap(customer => this.ensureCsrf(true).pipe(map(() => customer)))
    );
  }

  addresses(): Observable<CustomerAddress[]> {
    return this.withSessionFailure(this.http.get<CustomerAddress[]>('/api/account/addresses'));
  }

  addAddress(input: AddressInput): Observable<CustomerAddress> {
    return this.withSessionFailure(this.http.post<CustomerAddress>('/api/account/addresses', input));
  }

  updateAddress(id: number, input: AddressInput): Observable<CustomerAddress> {
    return this.withSessionFailure(this.http.put<CustomerAddress>(`/api/account/addresses/${id}`, input));
  }

  deleteAddress(id: number): Observable<void> {
    return this.withSessionFailure(this.http.delete<void>(`/api/account/addresses/${id}`));
  }

  chooseDefault(id: number, type: 'shipping' | 'billing'): Observable<CustomerAddress> {
    return this.withSessionFailure(this.http.post<CustomerAddress>(`/api/account/addresses/${id}/default-${type}`, null));
  }

  clear(): void {
    this.currentCustomer.set(null);
    this.sessionRequest = null;
  }

  private restoreSessionRequest(force = false): Observable<boolean> {
    if (force || this.sessionRequest === null) {
      this.sessionRequest = this.http.get<CustomerProfile>('/api/account/auth/session').pipe(
        tap(customer => this.setCustomer(customer)),
        map(() => true),
        catchError(() => {
          this.clear();
          return of(false);
        }),
        shareReplay({ bufferSize: 1, refCount: false })
      );
    }
    return this.sessionRequest;
  }

  private setCustomer(customer: CustomerProfile): void {
    this.currentCustomer.set(customer);
  }

  private withSessionFailure<T>(request: Observable<T>): Observable<T> {
    return request.pipe(
      catchError(error => {
        if (error?.status === 401) this.clear();
        return throwError(() => error);
      })
    );
  }

  private ensureCsrf(refresh = false): Observable<CsrfResponse> {
    if (refresh || this.csrfRequest === null) {
      this.csrfRequest = this.http.get<CsrfResponse>('/api/account/auth/csrf').pipe(
        shareReplay({ bufferSize: 1, refCount: false })
      );
    }
    return this.csrfRequest;
  }
}
