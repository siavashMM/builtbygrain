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
});
