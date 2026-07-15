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
});
