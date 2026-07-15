import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ProductService } from './product.service';

describe('ProductService', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
  });

  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('loads a public product by its encoded slug', () => {
    const service = TestBed.inject(ProductService);
    service.getProduct('oak board').subscribe();

    const request = TestBed.inject(HttpTestingController).expectOne('/api/public/products/oak%20board');
    expect(request.request.method).toBe('GET');
    request.flush({});
  });

  it('creates a product through its selected category endpoint', () => {
    const service = TestBed.inject(ProductService);
    const product = {
      name: 'Oak shelf', slug: 'oak-shelf', description: null,
      priceCents: 4900, currency: 'EUR', inStock: true,
      sizes: [], active: true, categoryId: 12
    };

    service.createProduct(product).subscribe();

    const request = TestBed.inject(HttpTestingController)
      .expectOne('/api/admin/categories/12/products');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(product);
    request.flush({});
  });
});
