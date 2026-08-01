import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ShopCatalogService } from './shop-catalog.service';

describe('ShopCatalogService', () => {
  beforeEach(() => TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] }));
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('loads public catalog categories', () => {
    TestBed.inject(ShopCatalogService).categories().subscribe();
    const request = TestBed.inject(HttpTestingController).expectOne('/api/public/categories');
    expect(request.request.method).toBe('GET');
    request.flush([]);
  });

  it('loads recursive public navigation',()=>{
    TestBed.inject(ShopCatalogService).navigation().subscribe();
    const request=TestBed.inject(HttpTestingController).expectOne('/api/navigation/categories');
    expect(request.request.method).toBe('GET');
    request.flush([]);
  });

  it('shares storefront requests for the 60-second cache window', () => {
    const service = TestBed.inject(ShopCatalogService);
    service.storefront().subscribe();
    service.storefront().subscribe();
    const request = TestBed.inject(HttpTestingController).expectOne('/api/public/storefront');
    expect(request.request.method).toBe('GET');
    request.flush({ settings: {}, navigationGroups: [] });

    service.storefront().subscribe();
    TestBed.inject(HttpTestingController).expectNone('/api/public/storefront');
  });

  it('loads a category page by its complete nested path', () => {
    TestBed.inject(ShopCatalogService).categoryPage('office/desks/standing-desks').subscribe();
    const request = TestBed.inject(HttpTestingController).expectOne(candidate =>
      candidate.url === '/api/public/category' && candidate.params.get('path') === 'office/desks/standing-desks'
    );
    expect(request.request.method).toBe('GET');
    request.flush({ category: null, breadcrumbs: [], products: [] });
  });
});
