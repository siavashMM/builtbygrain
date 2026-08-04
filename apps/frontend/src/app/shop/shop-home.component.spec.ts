import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { ProductCard } from '../product.service';
import { ShopHomeComponent } from './shop-home.component';

describe('ShopHomeComponent', () => {
  it('shows only active root categories in backend order with real paths and image fallbacks', async () => {
    await TestBed.configureTestingModule({
      imports: [ShopHomeComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([])
      ]
    }).compileComponents();

    const fixture = TestBed.createComponent(ShopHomeComponent);
    fixture.detectChanges();
    const http = TestBed.inject(HttpTestingController);
    http.expectOne('/api/account/auth/session').flush({}, { status: 401, statusText: 'Unauthorized' });
    http.expectOne('/api/public/storefront').flush({ settings: emptySettings(), navigationGroups: [] });
    http.expectOne('/api/public/categories').flush([
      { id: 1, parentId: null, name: 'Office', slug: 'office', description: 'Work well.', imageUrl: null, path: '/category/office', sortOrder: 0, active: true },
      { id: 2, parentId: 1, name: 'Desks', slug: 'desks', description: null, imageUrl: null, path: '/category/office/desks', sortOrder: 0, active: true },
      { id: 3, parentId: null, name: 'Shelves', slug: 'shelves', description: null, imageUrl: '/shelves.jpg', path: '/category/shelves', sortOrder: 1, active: true },
      { id: 4, parentId: null, name: 'Hidden', slug: 'hidden', description: null, imageUrl: null, path: '/category/hidden', sortOrder: 2, active: false }
    ]);
    http.expectOne('/api/public/products').flush([
      { ...productCard(1, 'Oak desk', 2, 'desks'), primaryImageUrl: '/desk.jpg' }
    ]);
    fixture.detectChanges();

    const cards = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll<HTMLAnchorElement>('#shop-categories .home-category-card'));
    expect(cards.map(card => card.querySelector('strong')?.textContent?.trim())).toEqual(['Office', 'Shelves']);
    expect(cards.map(card => card.getAttribute('href'))).toEqual(['/category/office', '/category/shelves']);
    expect(cards[0].querySelector('img')?.getAttribute('src')).toBe('/desk.jpg');
    expect(cards[1].querySelector('img')?.getAttribute('src')).toBe('/shelves.jpg');
    http.verify();
  });

  it('uses configured hero content and falls back to the current hero when the custom image fails', async () => {
    await TestBed.configureTestingModule({
      imports: [ShopHomeComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])]
    }).compileComponents();
    const fixture = TestBed.createComponent(ShopHomeComponent);
    fixture.detectChanges();
    const http = TestBed.inject(HttpTestingController);
    http.expectOne('/api/account/auth/session').flush({}, { status: 401, statusText: 'Unauthorized' });
    http.expectOne('/api/public/storefront').flush({
      settings: { ...emptySettings(), heroImageUrl: '/configured-hero.jpg', heroImageAltText: 'Configured oak workshop', heroHeading: 'A configured heading', heroSupportingText: 'Configured support.' },
      navigationGroups: []
    });
    http.expectOne('/api/public/categories').flush([]);
    http.expectOne('/api/public/products').flush([{ ...productCard(1, 'Fallback piece', 1, 'office'), primaryImageUrl: '/fallback-current.jpg' }]);
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    const image = element.querySelector<HTMLImageElement>('.home-hero > .hero-image-main')!;
    expect(image.getAttribute('src')).toBe('/configured-hero.jpg');
    expect(image.getAttribute('alt')).toBe('Configured oak workshop');
    expect(image.getAttribute('width')).toBe('1600');
    expect(image.classList).toContain('hero-image-custom');
    expect(getComputedStyle(image).objectFit).toBe('cover');
    expect(image.getBoundingClientRect().width).toBeCloseTo(element.querySelector<HTMLElement>('.home-hero')!.getBoundingClientRect().width, 0);
    expect(element.querySelector('.home-hero h1')?.textContent).toContain('A configured heading');

    image.dispatchEvent(new Event('load'));
    fixture.detectChanges();
    expect(image.classList).toContain('loaded');

    image.dispatchEvent(new Event('error'));
    fixture.detectChanges();
    const fallbackImage = element.querySelector<HTMLImageElement>('.home-hero > .hero-image-main')!;
    expect(fallbackImage.getAttribute('src')).toBe('/fallback-current.jpg');
    expect(fallbackImage.classList).not.toContain('hero-image-custom');
    http.verify();
  });
});

function emptySettings() {
  return { heroImageUrl: null, heroImageAltText: null, heroHeading: null, heroSupportingText: null, updatedAt: '2026-07-18T12:00:00Z' };
}

function productCard(id: number, name: string, categoryId: number, categorySlug: string): ProductCard {
  return {
    id, name, slug: name.toLowerCase().replaceAll(' ', '-'), currency: 'EUR', fromPriceCents: 4400,
    primaryImageUrl: '/product-placeholder.svg', hoverImageUrl: '/product-placeholder.svg', categoryId,
    categoryName: categorySlug, categorySlug, colorSwatches: []
  };
}
