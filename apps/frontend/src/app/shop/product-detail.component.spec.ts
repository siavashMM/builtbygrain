import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { By } from '@angular/platform-browser';
import { Router, RouterOutlet, provideRouter } from '@angular/router';
import { Product } from '../product.service';
import { ProductDetailComponent } from './product-detail.component';

@Component({ imports: [RouterOutlet], template: '<router-outlet />' })
class ProductDetailTestApp {}

describe('ProductDetailComponent routing', () => {
  let fixture: ComponentFixture<ProductDetailTestApp>;
  let router: Router;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ProductDetailTestApp],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideNoopAnimations(),
        provideRouter([{ path: 'products/:slug', component: ProductDetailComponent }])
      ]
    }).compileComponents();
    router = TestBed.inject(Router);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads the new product when Angular reuses the detail component for another slug', async () => {
    await router.navigateByUrl('/products/oak-desk');
    fixture = TestBed.createComponent(ProductDetailTestApp);
    fixture.detectChanges();

    http.expectOne('/api/public/storefront').flush({ settings: {}, navigationGroups: [] });
    http.expectOne('/api/public/categories').flush([]);
    http.expectOne('/api/public/products/oak-desk').flush(product(1, 'Oak desk', 'oak-desk'));
    http.expectOne('/api/public/products').flush([]);
    fixture.detectChanges();

    const firstInstance = fixture.debugElement.query(By.directive(ProductDetailComponent)).componentInstance;
    expect((fixture.nativeElement as HTMLElement).querySelector('.purchase-heading h1')?.textContent).toContain('Oak desk');

    await router.navigateByUrl('/products/walnut-shelf');
    fixture.detectChanges();

    expect(fixture.debugElement.query(By.directive(ProductDetailComponent)).componentInstance).toBe(firstInstance);
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Preparing the product');
    http.expectOne('/api/public/products/walnut-shelf').flush(product(2, 'Walnut shelf', 'walnut-shelf'));
    http.expectOne('/api/public/products').flush([]);
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).querySelector('.purchase-heading h1')?.textContent).toContain('Walnut shelf');
    expect((fixture.nativeElement as HTMLElement).textContent).not.toContain('Oak desk');
  });
});

function product(id: number, name: string, slug: string): Product {
  return {
    id, name, slug, description: `${name} description`, priceCents: 10000, currency: 'EUR',
    imageUrl: null, imageUrls: [], inStock: true, sizes: [], active: true,
    categoryId: 1, categoryName: 'Furniture', categorySlug: 'furniture',
    configuration: {
      benefits: [], specifications: {}, sections: [], faqs: [], options: [],
      variants: [{
        id: `variant-${id}`, optionValueIds: [], priceCents: 10000, stockStatus: 'IN_STOCK',
        available: true, backorderAllowed: false, preorderAllowed: false, imageUrls: []
      }]
    }
  };
}
