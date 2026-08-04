import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';

import { AccountAuthComponent } from './account-auth.component';

describe('AccountAuthComponent', () => {
  afterEach(() => {
    document.body.classList.remove('shop-menu-open', 'cart-drawer-open');
    TestBed.inject(HttpTestingController).verify();
  });

  it('offers configured social sign-in methods and preserves the guarded account destination', async () => {
    await TestBed.configureTestingModule({
      imports: [AccountAuthComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              data: { mode: 'sign-in' },
              queryParamMap: convertToParamMap({ returnUrl: '/account/security' })
            }
          }
        }
      ]
    }).compileComponents();

    const fixture = TestBed.createComponent(AccountAuthComponent);
    fixture.detectChanges();
    const http = TestBed.inject(HttpTestingController);
    http.expectOne('/api/account/auth/session').flush({}, { status: 401, statusText: 'Unauthorized' });
    http.expectOne('/api/public/storefront').flush({ settings: {}, navigationGroups: [] });
    http.expectOne('/api/public/categories').flush([]);
    http.expectOne('/api/public/checkout/config').flush({
      socialProviders: { google: true, apple: true },
      addressAutocompleteEnabled: false,
      googleMapsBrowserKey: null
    });
    fixture.detectChanges();
    await fixture.whenStable();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector<HTMLAnchorElement>('.auth-social-button[href*="social/google"]')?.getAttribute('href'))
      .toBe('/api/account/auth/social/google?returnUrl=%2Faccount%2Fsecurity');
    expect(element.querySelector<HTMLAnchorElement>('.auth-social-button[href*="social/apple"]')?.textContent)
      .toContain('Sign in with Apple');
    expect(element.querySelector('.auth-divider')?.textContent).toContain('or use email');
  });
});
