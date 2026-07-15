import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AdminAuthService, adminAuthInterceptor } from './admin-auth.service';

describe('AdminAuthService', () => {
  let auth: AdminAuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([adminAuthInterceptor])),
        provideHttpClientTesting()
      ]
    });
    auth = TestBed.inject(AdminAuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    sessionStorage.clear();
  });

  it('stores a successful admin login and sends its credentials to admin APIs', () => {
    auth.login('admin', 'secret').subscribe();

    const login = http.expectOne('/api/admin/auth/login');
    expect(login.request.headers.get('Authorization')).toBe(`Basic ${btoa('admin:secret')}`);
    login.flush({ username: 'admin', admin: true });

    auth.validateSession().subscribe(valid => expect(valid).toBeTrue());
    const validation = http.expectOne('/api/admin/auth/login');
    expect(validation.request.headers.get('Authorization')).toBe(`Basic ${btoa('admin:secret')}`);
    validation.flush({ username: 'admin', admin: true });
  });

  it('does not reuse credentials from the obsolete unvalidated session format', () => {
    sessionStorage.setItem('builtbygrain.admin.basicToken', btoa('user:password'));
    sessionStorage.setItem('builtbygrain.admin.username', 'user');

    expect(auth.isLoggedIn()).toBeFalse();
    auth.validateSession().subscribe({ error: () => undefined });

    const validation = http.expectOne('/api/admin/auth/login');
    expect(validation.request.headers.has('Authorization')).toBeFalse();
    validation.flush({ error: 'Unauthorized' }, { status: 401, statusText: 'Unauthorized' });
  });
});
