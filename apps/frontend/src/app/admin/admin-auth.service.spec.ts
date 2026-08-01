import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AdminAuthService } from './admin-auth.service';

describe('AdminAuthService', () => {
  let auth: AdminAuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    auth = TestBed.inject(AdminAuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    sessionStorage.clear();
  });

  it('logs in through a CSRF-protected server session without storing credentials', () => {
    auth.login('admin', 'secret').subscribe(response => expect(response.admin).toBeTrue());

    http.expectOne('/api/admin/auth/csrf').flush({
      headerName: 'X-XSRF-TOKEN', parameterName: '_csrf', token: 'first-token'
    });

    const login = http.expectOne('/api/admin/auth/login');
    expect(login.request.method).toBe('POST');
    expect(login.request.body).toEqual({ username: 'admin', password: 'secret' });
    expect(login.request.headers.has('Authorization')).toBeFalse();
    login.flush({ username: 'admin', admin: true });

    http.expectOne('/api/admin/auth/csrf').flush({
      headerName: 'X-XSRF-TOKEN', parameterName: '_csrf', token: 'rotated-token'
    });

    expect(auth.getUsername()).toBe('admin');
    expect(sessionStorage.length).toBe(0);
  });

  it('validates and clears the server session without browser storage', () => {
    auth.validateSession().subscribe(valid => expect(valid).toBeTrue());

    const validation = http.expectOne('/api/admin/auth/session');
    expect(validation.request.method).toBe('GET');
    expect(validation.request.headers.has('Authorization')).toBeFalse();
    validation.flush({ username: 'admin', admin: true });

    expect(auth.getUsername()).toBe('admin');
    auth.clear();
    expect(auth.getUsername()).toBeNull();
  });

  it('invalidates the server session and refreshes the CSRF cookie on logout', () => {
    auth.logout().subscribe();

    const logout = http.expectOne('/api/admin/auth/logout');
    expect(logout.request.method).toBe('POST');
    logout.flush(null);

    http.expectOne('/api/admin/auth/csrf').flush({
      headerName: 'X-XSRF-TOKEN', parameterName: '_csrf', token: 'signed-out-token'
    });
    expect(auth.getUsername()).toBeNull();
  });

  it('changes the password, refreshes CSRF, and clears the local session state', () => {
    auth.validateSession().subscribe();
    http.expectOne('/api/admin/auth/session').flush({ username: 'admin', admin: true });

    auth.changePassword('current-secret', 'a-new-secure-password').subscribe();

    const change = http.expectOne('/api/admin/auth/password');
    expect(change.request.method).toBe('POST');
    expect(change.request.body).toEqual({
      currentPassword: 'current-secret',
      newPassword: 'a-new-secure-password'
    });
    change.flush(null);

    http.expectOne('/api/admin/auth/csrf').flush({
      headerName: 'X-XSRF-TOKEN', parameterName: '_csrf', token: 'signed-out-token'
    });
    expect(auth.getUsername()).toBeNull();
  });
});
