import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AccountService, CustomerProfile } from './account.service';

describe('AccountService', () => {
  let service: AccountService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(AccountService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('registers through the CSRF endpoint and preserves a server-sanitized return path', () => {
    let destination = '';
    service.register({
      email: 'avery@example.test',
      password: 'correct horse grain',
      firstName: 'Avery',
      lastName: 'Oak',
      locale: 'en',
      returnUrl: '/cart'
    }).subscribe(response => destination = response.returnUrl);

    http.expectOne('/api/account/auth/csrf').flush(csrf());
    const registration = http.expectOne('/api/account/auth/register');
    expect(registration.request.body.returnUrl).toBe('/cart');
    registration.flush({ customer: profile(), returnUrl: '/cart' });
    http.expectOne('/api/account/auth/csrf').flush(csrf());

    expect(destination).toBe('/cart');
    expect(service.customer()?.email).toBe('avery@example.test');
  });

  it('restores and clears a JDBC-backed session without persisting credentials in the browser', () => {
    let valid = false;
    service.validateSession().subscribe(result => valid = result);
    http.expectOne('/api/account/auth/session').flush(profile());
    expect(valid).toBeTrue();
    expect(service.customer()?.firstName).toBe('Avery');

    service.logout().subscribe();
    http.expectOne('/api/account/auth/logout').flush(null);
    http.expectOne('/api/account/auth/csrf').flush(csrf());
    expect(service.customer()).toBeNull();
  });

  it('shares passive session restoration and clears the signed-in state after authentication failure', () => {
    const restored: boolean[] = [];
    service.restoreSession().subscribe(value => restored.push(value));
    service.restoreSession().subscribe(value => restored.push(value));
    http.expectOne('/api/account/auth/session').flush(profile());

    expect(restored).toEqual([true, true]);
    expect(service.customer()?.email).toBe('avery@example.test');

    service.profile().subscribe({ error: () => undefined });
    http.expectOne('/api/account/profile').flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(service.customer()).toBeNull();
  });

  it('treats an expired passive session as signed out without surfacing a header error', () => {
    let valid = true;
    service.restoreSession().subscribe(value => valid = value);
    http.expectOne('/api/account/auth/session').flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(valid).toBeFalse();
    expect(service.customer()).toBeNull();
  });

  it('uses ownership-scoped address URLs and explicit default actions', () => {
    service.chooseDefault(42, 'shipping').subscribe();
    const defaultRequest = http.expectOne('/api/account/addresses/42/default-shipping');
    expect(defaultRequest.request.method).toBe('POST');
    defaultRequest.flush(address());

    service.deleteAddress(42).subscribe();
    const deleteRequest = http.expectOne('/api/account/addresses/42');
    expect(deleteRequest.request.method).toBe('DELETE');
    deleteRequest.flush(null);
  });
});

function csrf() {
  return { headerName: 'X-XSRF-TOKEN', parameterName: '_csrf', token: 'test-token' };
}

function profile(): CustomerProfile {
  return {
    id: 1,
    email: 'avery@example.test',
    firstName: 'Avery',
    lastName: 'Oak',
    phone: null,
    locale: 'en',
    emailVerified: false,
    accountStatus: 'ACTIVE',
    createdAt: '2026-07-26T10:00:00'
  };
}

function address() {
  return {
    id: 42,
    recipientName: 'Avery Oak',
    company: null,
    street: 'Oak Street',
    houseNumber: '12',
    addressLine2: null,
    postalCode: '10115',
    city: 'Berlin',
    region: null,
    countryCode: 'DE',
    phone: null,
    defaultShipping: true,
    defaultBilling: false,
    createdAt: '2026-07-26T10:00:00',
    updatedAt: '2026-07-26T10:00:00'
  };
}
