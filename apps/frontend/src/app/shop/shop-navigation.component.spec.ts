import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { AccountService } from '../account/account.service';
import { ProductCard } from '../product.service';
import { PublicNavigationGroup, ShopCategory } from './shop-catalog.service';
import { ShopNavigationComponent } from './shop-navigation.component';

describe('ShopNavigationComponent', () => {
  afterEach(() => {
    document.body.classList.remove('shop-menu-open', 'cart-drawer-open');
    window.localStorage.removeItem('builtbygrain.guest-cart');
    TestBed.inject(HttpTestingController).verify();
  });

  it('opens one desktop mega-menu by hover, focus, or click and caps featured products at three', async () => {
    const fixture = await createNavigation([
      group(1, 'Office', [category(10, null, 'Office', '/category/office'), category(11, 10, 'Standing desks', '/category/office/desks/standing-desks')], [card(1), card(1), card(2), card(3), card(4)]),
      group(2, 'Living', [category(20, null, 'Shelves', '/category/living/shelves')], [])
    ]);
    const element = fixture.nativeElement as HTMLElement;
    const triggers = element.querySelectorAll<HTMLButtonElement>('.navigation-group-trigger');

    triggers[0].dispatchEvent(new Event('mouseenter'));
    fixture.detectChanges();
    expect(triggers[0].getAttribute('aria-expanded')).toBe('true');
    expect(element.querySelector('.storefront-mega-menu h2')?.textContent).toContain('Office');
    expect(element.querySelectorAll('.mega-product-card').length).toBe(3);
    expect(Array.from(element.querySelectorAll('.mega-product-card strong')).map(node => node.textContent)).toEqual(['Featured 1', 'Featured 2', 'Featured 3']);
    expect(element.querySelector<HTMLAnchorElement>('a[href="/category/office/desks/standing-desks"]')).not.toBeNull();

    triggers[1].dispatchEvent(new FocusEvent('focus'));
    fixture.detectChanges();
    expect(triggers[0].getAttribute('aria-expanded')).toBe('false');
    expect(triggers[1].getAttribute('aria-expanded')).toBe('true');
    await new Promise(resolve => setTimeout(resolve));
    triggers[1].click();
    fixture.detectChanges();
    expect(element.querySelector('.storefront-mega-menu')).toBeNull();
  });

  it('links the account icon to the protected account entry point', async () => {
    const fixture = await createNavigation([]);
    const accountLink = (fixture.nativeElement as HTMLElement)
      .querySelector<HTMLAnchorElement>('.account-button');
    expect(accountLink?.getAttribute('href')).toBe('/account');
    expect(accountLink?.getAttribute('aria-label')).toBe('My account');
  });

  it('shows an accessible signed-in badge only while a real customer session is present', async () => {
    const fixture = await createNavigation([], [], true);
    const element = fixture.nativeElement as HTMLElement;
    const accountLink = element.querySelector<HTMLAnchorElement>('.account-button');
    expect(accountLink?.getAttribute('aria-label')).toBe('My account, signed in');
    expect(accountLink?.textContent).toContain('Signed in');
    expect(element.querySelector('.account-status-badge')?.textContent).toContain('✓');

    TestBed.inject(AccountService).clear();
    fixture.detectChanges();
    expect(element.querySelector('.account-status-badge')).toBeNull();
    expect(accountLink?.getAttribute('aria-label')).toBe('My account');
  });

  it('keeps the real cart count on the same uniform icon and opens an accessible desktop drawer', async () => {
    spyOn(window, 'matchMedia').and.returnValue(mediaQuery(false));
    const fixture = await createNavigation([], [], false, 3);
    const element = fixture.nativeElement as HTMLElement;
    const bag = element.querySelector<HTMLButtonElement>('.bag-button')!;
    expect(bag?.getAttribute('aria-label')).toBe('Open shopping bag, 3 items');
    expect(bag?.querySelector('.cart-count-badge')?.textContent?.trim()).toBe('3');
    expect(bag?.querySelector('.bag-label')).toBeNull();

    const restoredFocus = spyOn(bag, 'focus').and.callThrough();
    bag.focus();
    bag.click();
    fixture.detectChanges();
    await fixture.whenStable();

    const drawer = element.querySelector<HTMLElement>('.cart-drawer');
    expect(drawer?.getAttribute('role')).toBe('dialog');
    expect(drawer?.getAttribute('aria-modal')).toBe('true');
    expect(drawer?.hasAttribute('cdktrapfocus')).toBeTrue();
    expect(document.body.classList.contains('cart-drawer-open')).toBeTrue();
    expect(element.querySelector('.cart-drawer-item h3')?.textContent).toContain('Fictional oak tray');
    expect(element.querySelector('.cart-drawer-footer')?.textContent).toContain('126,00');
    expect(document.activeElement).toBe(element.querySelector('.cart-drawer-close'));

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    fixture.detectChanges();
    await Promise.resolve();
    expect(element.querySelector('.cart-drawer')).toBeNull();
    expect(document.body.classList.contains('cart-drawer-open')).toBeFalse();
    expect(restoredFocus).toHaveBeenCalled();
  });

  it('shows a simple honest empty-bag message at the top of the desktop drawer', async () => {
    spyOn(window, 'matchMedia').and.returnValue(mediaQuery(false));
    const fixture = await createNavigation([]);
    const element = fixture.nativeElement as HTMLElement;

    element.querySelector<HTMLButtonElement>('.bag-button')!.click();
    fixture.detectChanges();
    await fixture.whenStable();

    expect(element.querySelector('#cart-drawer-title')?.textContent).not.toContain('(0)');
    expect(element.querySelector('.cart-drawer-empty p')?.textContent).toContain('currently empty');
    expect(element.querySelector('.cart-drawer-empty a')?.textContent).toContain('Continue browsing');
    expect(element.querySelector('.cart-drawer-empty h3')).toBeNull();
    expect(element.querySelector('.cart-drawer-footer')).toBeNull();
  });

  it('keeps the dedicated shopping-bag page on mobile instead of opening a sidebar', async () => {
    spyOn(window, 'matchMedia').and.returnValue(mediaQuery(true));
    const fixture = await createNavigation([]);
    const element = fixture.nativeElement as HTMLElement;
    const navigate = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);

    element.querySelector<HTMLButtonElement>('.bag-button')!.click();
    fixture.detectChanges();

    expect(navigate).toHaveBeenCalledWith(['/cart']);
    expect(element.querySelector('.cart-drawer')).toBeNull();
  });

  it('closes the desktop panel with Escape, outside click, and destination selection', async () => {
    const fixture = await createNavigation([group(1, 'Office', [category(10, null, 'Office', '/category/office')], [card(1)])]);
    const element = fixture.nativeElement as HTMLElement;
    const trigger = element.querySelector<HTMLButtonElement>('.navigation-group-trigger')!;
    const focus = spyOn(trigger, 'focus');
    trigger.click(); fixture.detectChanges();
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' })); fixture.detectChanges();
    await new Promise(resolve => setTimeout(resolve));
    expect(element.querySelector('.storefront-mega-menu')).toBeNull();
    expect(focus).toHaveBeenCalled();

    trigger.click(); fixture.detectChanges();
    document.body.dispatchEvent(new MouseEvent('click', { bubbles: true })); fixture.detectChanges();
    expect(element.querySelector('.storefront-mega-menu')).toBeNull();

    trigger.click(); fixture.detectChanges();
    element.querySelector<HTMLAnchorElement>('.mega-category-column a')!.click(); fixture.detectChanges();
    expect(element.querySelector('.storefront-mega-menu')).toBeNull();
  });

  it('shows the same groups, categories, and featured products in a focus-trapped mobile drill-down with Back', async () => {
    const fixture = await createNavigation([group(1, 'Office', [category(11, 10, 'Standing desks', '/category/office/desks/standing-desks')], [card(1), card(2)])]);
    const element = fixture.nativeElement as HTMLElement;
    const toggle = element.querySelector<HTMLButtonElement>('.mobile-nav-toggle')!;
    const restoredFocus = spyOn(toggle, 'focus').and.callThrough();
    toggle.focus(); toggle.click(); fixture.detectChanges();
    restoredFocus.calls.reset();
    expect(toggle.getAttribute('aria-expanded')).toBe('true');
    expect(document.body.classList.contains('shop-menu-open')).toBeTrue();

    element.querySelector<HTMLButtonElement>('.mobile-category-drill')!.click(); fixture.detectChanges();
    expect(element.querySelector('.mobile-menu-back')).not.toBeNull();
    expect(element.querySelector<HTMLAnchorElement>('a[href="/category/office/desks/standing-desks"]')).not.toBeNull();
    expect(element.querySelectorAll('.mobile-featured-products a').length).toBe(2);
    expect(element.querySelector('#mobile-shop-menu')?.hasAttribute('cdktrapfocus')).toBeTrue();

    element.querySelector<HTMLButtonElement>('.mobile-menu-back')!.click(); fixture.detectChanges();
    expect(element.querySelector('.mobile-category-drill')).not.toBeNull();
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' })); fixture.detectChanges();
    await new Promise(resolve => setTimeout(resolve));
    expect(element.querySelector('#mobile-shop-menu')).toBeNull();
    expect(restoredFocus).toHaveBeenCalled();
  });

  it('falls back to ordered active root categories when configuration is empty', async () => {
    const fixture = await createNavigation([], [
      category(2, null, 'Second', '/category/second', true, 2),
      category(1, null, 'First', '/category/first', true, 1),
      category(3, null, 'Hidden', '/category/hidden', false, 0),
      category(4, 1, 'Child', '/category/first/child', true, 0)
    ]);
    const element = fixture.nativeElement as HTMLElement;
    const triggers = Array.from(element.querySelectorAll<HTMLButtonElement>('.navigation-group-trigger'));
    expect(triggers.map(trigger => trigger.textContent?.trim().split(' ')[0])).toEqual(['First', 'Second']);
    triggers[0].click(); fixture.detectChanges();
    expect(element.querySelector<HTMLAnchorElement>('.mega-category-column a')?.getAttribute('href')).toBe('/category/first');
  });

  it('does not flash fallback categories before storefront configuration finishes loading', async () => {
    await TestBed.configureTestingModule({
      imports: [ShopNavigationComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])]
    }).compileComponents();
    const fixture = TestBed.createComponent(ShopNavigationComponent);
    fixture.detectChanges();
    const http = TestBed.inject(HttpTestingController);

    http.expectOne('/api/account/auth/session').flush({}, { status: 401, statusText: 'Unauthorized' });
    http.expectOne('/api/public/categories').flush([category(10, null, 'Fallback', '/category/fallback')]);
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelectorAll('.navigation-group-trigger').length).toBe(0);
    expect((fixture.nativeElement as HTMLElement).querySelector('.navigation-group-nav')?.getAttribute('aria-busy')).toBe('true');

    http.expectOne('/api/public/storefront').flush({ settings: {}, navigationGroups: [group(1, 'Configured', [], [])] });
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('.navigation-group-trigger')?.textContent).toContain('Configured');
    expect((fixture.nativeElement as HTMLElement).querySelector('.navigation-group-nav')?.getAttribute('aria-busy')).toBe('false');
  });

  it('replaces broken mega-menu product images with the existing placeholder', async () => {
    const fixture = await createNavigation([group(1, 'Office', [], [{ ...card(1), primaryImageUrl: '/broken.jpg' }])]);
    const element = fixture.nativeElement as HTMLElement;
    element.querySelector<HTMLButtonElement>('.navigation-group-trigger')!.click(); fixture.detectChanges();
    const image = element.querySelector<HTMLImageElement>('.mega-product-card img')!;
    image.dispatchEvent(new Event('error')); fixture.detectChanges();
    expect(image.src).toContain('/product-placeholder.svg');
  });

  async function createNavigation(groups: PublicNavigationGroup[], categories: ShopCategory[] = [], signedIn = false, cartQuantity = 0) {
    window.localStorage.removeItem('builtbygrain.guest-cart');
    if (cartQuantity > 0) {
      window.localStorage.setItem('builtbygrain.guest-cart', JSON.stringify([{
        productId: 77,
        slug: 'fictional-oak-tray',
        name: 'Fictional oak tray',
        imageUrl: null,
        priceCents: 4200,
        currency: 'EUR',
        quantity: cartQuantity,
        variantId: 'standard'
      }]));
    }
    await TestBed.configureTestingModule({
      imports: [ShopNavigationComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])]
    }).compileComponents();
    const fixture = TestBed.createComponent(ShopNavigationComponent);
    fixture.detectChanges();
    const http = TestBed.inject(HttpTestingController);
    const session = http.expectOne('/api/account/auth/session');
    if (signedIn) {
      session.flush(customer());
    } else {
      session.flush({}, { status: 401, statusText: 'Unauthorized' });
    }
    http.expectOne('/api/public/storefront').flush({ settings: { heroImageUrl: null }, navigationGroups: groups });
    http.expectOne('/api/public/categories').flush(categories);
    fixture.detectChanges();
    return fixture;
  }
});

function group(id: number, label: string, categories: ShopCategory[], featuredProducts: ProductCard[]): PublicNavigationGroup {
  return { id, label, displayOrder: id - 1, categories, featuredProducts };
}

function customer() {
  return {
    id: 7,
    email: 'avery@example.test',
    firstName: 'Avery',
    lastName: 'Oak',
    phone: null,
    locale: 'en',
    emailVerified: true,
    accountStatus: 'ACTIVE',
    createdAt: '2026-07-26T10:00:00'
  };
}

function category(id: number, parentId: number | null, name: string, path: string, active = true, sortOrder = 0): ShopCategory {
  return { id, parentId, name, path, active, sortOrder, slug: path.split('/').at(-1)!, description: `${name} collection`, imageUrl: null };
}

function card(id: number): ProductCard {
  return {
    id, name: `Featured ${id}`, slug: `featured-${id}`, currency: 'EUR', fromPriceCents: id * 1000,
    primaryImageUrl: `/featured-${id}.jpg`, hoverImageUrl: `/featured-${id}.jpg`, categoryId: 10,
    categoryName: 'Office', categorySlug: 'office', colorSwatches: []
  };
}

function mediaQuery(matches: boolean): MediaQueryList {
  return {
    matches,
    media: '(max-width: 760px)',
    onchange: null,
    addListener: () => undefined,
    removeListener: () => undefined,
    addEventListener: () => undefined,
    removeEventListener: () => undefined,
    dispatchEvent: () => true
  };
}
