import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { adminCatalogInvalidationInterceptor } from './admin-catalog-invalidation.interceptor';
import { CatalogRequestCache } from './catalog-request-cache.service';

describe('adminCatalogInvalidationInterceptor', () => {
  beforeEach(() => TestBed.configureTestingModule({ providers: [
    provideHttpClient(withInterceptors([adminCatalogInvalidationInterceptor])),
    provideHttpClientTesting()
  ] }));

  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('invalidates public catalog entries only after a successful admin mutation', () => {
    const cache = TestBed.inject(CatalogRequestCache);
    const invalidate = spyOn(cache, 'invalidate');
    TestBed.inject(HttpClient).put('/api/admin/products/1', {}).subscribe();
    expect(invalidate).not.toHaveBeenCalled();
    TestBed.inject(HttpTestingController).expectOne('/api/admin/products/1').flush({});
    expect(invalidate).toHaveBeenCalledOnceWith();
  });

  it('does not invalidate on reads or failed mutations', () => {
    const cache = TestBed.inject(CatalogRequestCache);
    const invalidate = spyOn(cache, 'invalidate');
    TestBed.inject(HttpClient).get('/api/admin/products').subscribe();
    TestBed.inject(HttpTestingController).expectOne('/api/admin/products').flush([]);
    TestBed.inject(HttpClient).delete('/api/admin/products/1').subscribe({ error: () => undefined });
    TestBed.inject(HttpTestingController).expectOne('/api/admin/products/1').flush({}, { status: 409, statusText: 'Conflict' });
    expect(invalidate).not.toHaveBeenCalled();
  });
});
