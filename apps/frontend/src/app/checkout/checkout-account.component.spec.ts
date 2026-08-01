import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { CheckoutAccountComponent } from './checkout-account.component';

describe('CheckoutAccountComponent', () => {
  beforeEach(() => {
    localStorage.setItem('builtbygrain.guest-cart', JSON.stringify([{
      productId: 7,
      slug: 'oak-bench',
      name: 'Oak bench',
      imageUrl: null,
      priceCents: 12900,
      currency: 'EUR',
      quantity: 1,
      variantId: 'natural'
    }]));
  });

  afterEach(() => {
    localStorage.removeItem('builtbygrain.guest-cart');
    document.body.classList.remove('shop-menu-open', 'cart-drawer-open');
    TestBed.inject(HttpTestingController).verify();
  });

  it('keeps classic registration and guest checkout available while unconfigured social options stay disabled', async () => {
    const fixture = await createComponent({
      socialProviders: { google: false, apple: false },
      addressAutocompleteEnabled: false,
      googleMapsBrowserKey: null
    });
    const element = fixture.nativeElement as HTMLElement;

    expect(element.querySelectorAll<HTMLButtonElement>('.social-button:disabled').length).toBe(2);
    expect(element.querySelector('.checkout-form h2')?.textContent).toContain('Create your account');
    expect(element.querySelector('.guest-checkout button')?.textContent).toContain('Continue as guest');
  });

  it('uses the existing secured registration API and continues to Delivery', async () => {
    const fixture = await createComponent({
      socialProviders: { google: true, apple: true },
      addressAutocompleteEnabled: true,
      googleMapsBrowserKey: 'browser-key'
    });
    const component = fixture.componentInstance as any;
    const router = TestBed.inject(Router);
    const navigate = spyOn(router, 'navigateByUrl').and.resolveTo(true);

    component.registerForm.setValue({
      firstName: 'Avery',
      lastName: 'Oak',
      email: 'avery@example.test',
      password: 'correct horse grain',
      confirmPassword: 'correct horse grain'
    });
    component.register();

    const http = TestBed.inject(HttpTestingController);
    http.expectOne('/api/account/auth/csrf').flush({
      headerName: 'X-XSRF-TOKEN',
      parameterName: '_csrf',
      token: 'before'
    });
    const registration = http.expectOne('/api/account/auth/register');
    expect(registration.request.body.returnUrl).toBe('/checkout/delivery');
    registration.flush({
      customer: customer(),
      returnUrl: '/checkout/delivery'
    });
    http.expectOne('/api/account/auth/csrf').flush({
      headerName: 'X-XSRF-TOKEN',
      parameterName: '_csrf',
      token: 'after'
    });

    expect(navigate).toHaveBeenCalledWith('/checkout/delivery');
  });

  async function createComponent(config: object) {
    await TestBed.configureTestingModule({
      imports: [CheckoutAccountComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])]
    }).compileComponents();
    const fixture = TestBed.createComponent(CheckoutAccountComponent);
    fixture.detectChanges();
    const http = TestBed.inject(HttpTestingController);
    http.expectOne('/api/account/auth/session').flush({}, { status: 401, statusText: 'Unauthorized' });
    http.expectOne('/api/public/storefront').flush({ settings: {}, navigationGroups: [] });
    http.expectOne('/api/public/categories').flush([]);
    http.expectOne('/api/public/checkout/config').flush(config);
    fixture.detectChanges();
    await fixture.whenStable();
    return fixture;
  }
});

function customer() {
  return {
    id: 4,
    email: 'avery@example.test',
    firstName: 'Avery',
    lastName: 'Oak',
    phone: null,
    locale: 'en',
    emailVerified: false,
    accountStatus: 'ACTIVE',
    createdAt: '2026-07-30T10:00:00'
  };
}
