import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { Router, RouterOutlet, provideRouter } from '@angular/router';
import { ProductCard } from '../product.service';
import { ShopCategoryPageComponent } from './shop-category-page.component';

@Component({ imports: [RouterOutlet], template: '<router-outlet />' })
class CategoryTestApp {}

describe('ShopCategoryPageComponent routing', () => {
  let fixture: ComponentFixture<CategoryTestApp>;
  let router: Router;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CategoryTestApp],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([
          { path: 'category', children: [{ path: '**', component: ShopCategoryPageComponent }] }
        ])
      ]
    }).compileComponents();
    router = TestBed.inject(Router);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads a deeply nested category from the direct browser URL', async () => {
    await router.navigateByUrl('/category/office/desks/standing-desks');
    fixture = TestBed.createComponent(CategoryTestApp);
    fixture.detectChanges();

    flushNavigation();
    expectCategoryRequest('office/desks/standing-desks').flush(categoryPage(
      category(3, 2, 'Standing desks', 'standing-desks', '/category/office/desks/standing-desks'),
      [
        category(1, null, 'Office', 'office', '/category/office'),
        category(2, 1, 'Desks', 'desks', '/category/office/desks'),
        category(3, 2, 'Standing desks', 'standing-desks', '/category/office/desks/standing-desks')
      ],
      [productCard(1, 'Oak standing desk', 3)]
    ));
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).querySelector('h1')?.textContent).toContain('Standing desks');
    expect(Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('.category-breadcrumbs a')).map(item => item.textContent?.trim()))
      .toEqual(['Home', 'Office', 'Desks']);
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Oak standing desk');
    expect((fixture.nativeElement as HTMLElement).querySelector('.category-product-grid')).not.toBeNull();
    expect((fixture.nativeElement as HTMLElement).querySelector('.category-products > header span')).toBeNull();
  });

  it('reuses the category component when switching routes without showing the homepage', async () => {
    fixture = TestBed.createComponent(CategoryTestApp);
    fixture.detectChanges();
    await router.navigateByUrl('/category/office');
    fixture.detectChanges();

    flushNavigation();
    expectCategoryRequest('office').flush(categoryPage(category(1, null, 'Office', 'office', '/category/office'), [], []));
    fixture.detectChanges();
    const firstInstance = fixture.debugElement.query(By.directive(ShopCategoryPageComponent)).componentInstance;
    expect((fixture.nativeElement as HTMLElement).querySelector('app-shop-home')).toBeNull();

    await router.navigateByUrl('/category/living-room/shelves');
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('app-shop-home')).toBeNull();
    expect(fixture.debugElement.query(By.directive(ShopCategoryPageComponent)).componentInstance).toBe(firstInstance);
    expectCategoryRequest('living-room/shelves').flush(categoryPage(
      category(5, 4, 'Shelves', 'shelves', '/category/living-room/shelves'),
      [category(4, null, 'Living room', 'living-room', '/category/living-room')],
      []
    ));
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).querySelector('h1')?.textContent).toContain('Shelves');
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('No pieces are available yet.');
  });

  function expectCategoryRequest(path: string) {
    return http.expectOne(request => request.url === '/api/public/category' && request.params.get('path') === path);
  }

  function flushNavigation() {
    http.expectOne('/api/account/auth/session').flush({}, { status: 401, statusText: 'Unauthorized' });
    http.expectOne('/api/public/storefront').flush({ settings: { heroImageUrl: null }, navigationGroups: [] });
    http.expectOne('/api/public/categories').flush([]);
  }
});

function category(id: number, parentId: number | null, name: string, slug: string, path: string) {
  return { id, parentId, name, slug, description: `${name} description`, imageUrl: null, path, sortOrder: 0, active: true };
}

function categoryPage(categoryValue: ReturnType<typeof category>, breadcrumbs: ReturnType<typeof category>[], products: ProductCard[]) {
  return { category: categoryValue, breadcrumbs, products };
}

function productCard(id: number, name: string, categoryId: number): ProductCard {
  return {
    id, name, slug: name.toLowerCase().replaceAll(' ', '-'), currency: 'EUR', fromPriceCents: 4400,
    primaryImageUrl: '/product-placeholder.svg', hoverImageUrl: '/product-placeholder.svg', categoryId,
    categoryName: 'Category', categorySlug: 'category', colorSwatches: []
  };
}
