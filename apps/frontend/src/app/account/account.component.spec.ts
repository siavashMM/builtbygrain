import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';

import { AccountComponent } from './account.component';

describe('AccountComponent security', () => {
  afterEach(() => {
    document.body.classList.remove('shop-menu-open', 'cart-drawer-open');
    TestBed.inject(HttpTestingController).verify();
  });

  it('offers a verified email setup flow when a social-only account has no password', async () => {
    await TestBed.configureTestingModule({
      imports: [AccountComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { data: { view: 'security' } } } }
      ]
    }).compileComponents();

    const fixture = TestBed.createComponent(AccountComponent);
    fixture.detectChanges();
    const http = TestBed.inject(HttpTestingController);
    http.expectOne('/api/account/auth/session').flush(profile(false));
    http.expectOne('/api/public/storefront').flush({ settings: {}, navigationGroups: [] });
    http.expectOne('/api/public/categories').flush([]);
    http.expectOne('/api/account/profile').flush(profile(false));
    http.expectOne('/api/account/addresses').flush([]);
    fixture.detectChanges();
    await fixture.whenStable();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('.editor-intro h1')?.textContent).toContain('Add a password');
    expect(element.querySelector('input[autocomplete="current-password"]')).toBeNull();

    element.querySelector<HTMLButtonElement>('.password-setup-card button')!.click();
    http.expectOne('/api/account/auth/csrf').flush({
      headerName: 'X-XSRF-TOKEN', parameterName: '_csrf', token: 'before'
    });
    const request = http.expectOne('/api/account/auth/forgot-password');
    expect(request.request.body).toEqual({ email: 'social@example.test' });
    request.flush({ message: 'If an account exists, a reset link has been sent.' });
    fixture.detectChanges();

    expect(element.querySelector('.account-message.success')?.textContent)
      .toContain('A secure password setup link was sent');
    expect(element.querySelector<HTMLButtonElement>('.password-setup-card button')?.disabled).toBeTrue();
  });
});

function profile(passwordSet: boolean) {
  return {
    id: 8,
    email: 'social@example.test',
    firstName: 'Social',
    lastName: 'Customer',
    phone: null,
    locale: 'en',
    emailVerified: true,
    passwordSet,
    accountStatus: 'ACTIVE',
    createdAt: '2026-08-04T10:00:00'
  };
}
