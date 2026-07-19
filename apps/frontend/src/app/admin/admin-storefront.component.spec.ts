import { HttpErrorResponse, HttpEvent, HttpEventType, HttpResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';

import { Product } from '../product.service';
import { ProductService } from '../product.service';
import { AdminCatalogService, CategoryOption } from './admin-catalog.service';
import { AdminStorefrontComponent } from './admin-storefront.component';
import { AdminStorefrontService, NavigationGroup, StorefrontProductReference, StorefrontSettings } from './admin-storefront.service';

describe('AdminStorefrontComponent', () => {
  const settings: StorefrontSettings = {
    heroImageUrl: '/current-hero.jpg', heroImageAltText: 'Current hero', heroHeading: 'Current heading',
    heroSupportingText: 'Current support', updatedAt: '2026-07-18T12:00:00Z'
  };
  const categoryOptions: CategoryOption[] = [
    { id: 10, name: 'Office', path: 'Office', active: true, depth: 0 },
    { id: 11, name: 'Standing desks', path: 'Office / Desks / Standing desks', active: true, depth: 2 }
  ];
  const products: Product[] = [product(21, 'Oak desk', 11, true), product(22, 'Wall shelf', 10, true), product(23, 'Desk tray', 10, true), product(24, 'Archived lamp', 10, false)];

  afterEach(() => TestBed.resetTestingModule());

  it('shows loading and backend error states with a retry action', async () => {
    const pending = new Subject<StorefrontSettings>();
    const api = apiMock();
    api.settings.and.returnValue(pending);
    await configure(api);
    const fixture = TestBed.createComponent(AdminStorefrontComponent);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.storefront-loading')).not.toBeNull();

    pending.error(new Error('offline'));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.storefront-load-error')).not.toBeNull();
    expect(fixture.nativeElement.textContent).toContain('offline');
  });

  it('requires alt text, reports upload progress, and keeps the current hero after a failed replacement', async () => {
    const upload = new Subject<HttpEvent<StorefrontSettings>>();
    const api = apiMock();
    api.replaceHeroImage.and.returnValue(upload);
    await configure(api);
    const fixture = TestBed.createComponent(AdminStorefrontComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance as any;
    component.heroFile.set(new File(['replacement'], 'replacement.png', { type: 'image/png' }));
    component.heroAlt.set('');
    component.replaceHero();
    fixture.detectChanges();
    expect(api.replaceHeroImage).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('Alt text is required');

    component.heroAlt.set('Replacement image description');
    component.replaceHero();
    upload.next({ type: HttpEventType.UploadProgress, loaded: 6, total: 10 });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.upload-progress')?.textContent).toContain('60%');
    upload.error(new HttpErrorResponse({ status: 400, error: { message: 'Image was rejected.' } }));
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector<HTMLImageElement>('.hero-preview img')?.src).toContain('/current-hero.jpg');
    expect(fixture.nativeElement.textContent).toContain('Image was rejected.');
  });

  it('updates the hero only after a successful replacement response', async () => {
    const api = apiMock();
    api.replaceHeroImage.and.returnValue(of(new HttpResponse({ body: { ...settings, heroImageUrl: '/replacement.jpg', heroImageAltText: 'Replacement' } })));
    await configure(api);
    const fixture = TestBed.createComponent(AdminStorefrontComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance as any;
    component.heroFile.set(new File(['replacement'], 'replacement.jpg', { type: 'image/jpeg' }));
    component.heroAlt.set('Replacement');
    component.replaceHero();
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector<HTMLImageElement>('.hero-preview img')?.src).toContain('/replacement.jpg');
    expect(fixture.nativeElement.textContent).toContain('Homepage hero image replaced.');
  });

  it('supports group creation, inline rename, status changes, deletion confirmation, and ordering', async () => {
    const first = group(1, 'Office', 0);
    const second = group(2, 'Living', 1);
    const api = apiMock([first, second]);
    api.createGroup.and.returnValue(of(group(3, 'Lighting', 2)));
    api.updateGroup.and.callFake((value: NavigationGroup, label: string) => of({ ...value, label }));
    api.setGroupActive.and.callFake((id: number, active: boolean) => of({ ...(id === 1 ? first : second), active }));
    api.reorderGroups.and.returnValue(of([{ ...second, displayOrder: 0 }, { ...first, displayOrder: 1 }]));
    api.deleteGroup.and.returnValue(of(void 0));
    spyOn(globalThis, 'confirm').and.returnValue(true);
    await configure(api);
    const fixture = TestBed.createComponent(AdminStorefrontComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance as any;

    component.newGroupLabel.set('Lighting'); component.createGroup();
    expect(api.createGroup).toHaveBeenCalledWith('Lighting');
    component.setGroupLabel(1, 'Workspace'); component.renameGroup(first);
    expect(api.updateGroup).toHaveBeenCalledWith(first, 'Workspace');
    component.toggleGroup(first);
    expect(api.setGroupActive).toHaveBeenCalledWith(1, true);
    component.moveGroup(second, -1);
    expect(api.reorderGroups).toHaveBeenCalledWith([2, 1, 3]);
    component.deleteGroup(first);
    expect(globalThis.confirm).toHaveBeenCalled();
    expect(api.deleteGroup).toHaveBeenCalledWith(1);
  });

  it('shows full category paths, saves category ordering, prevents duplicates, and handles backend errors', async () => {
    const assigned = { id: 10, parentId: null, name: 'Office', slug: 'office', description: null, imageUrl: null, path: '/category/office', sortOrder: 0, active: true };
    const nested = { id: 11, parentId: 10, name: 'Standing desks', slug: 'standing-desks', description: null, imageUrl: null, path: '/category/office/desks/standing-desks', sortOrder: 0, active: true };
    const value = { ...group(1, 'Office', 0), categories: [assigned, nested] };
    const api = apiMock([value]);
    api.reorderCategories.and.returnValue(of({ ...value, categories: [nested, assigned] }));
    api.assignCategory.and.returnValue(throwError(() => new HttpErrorResponse({ status: 409, error: { message: 'Category is already assigned.' } })));
    await configure(api);
    const fixture = TestBed.createComponent(AdminStorefrontComponent);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Office / Desks / Standing desks');
    const component = fixture.componentInstance as any;
    component.moveCategory(value, 11, -1);
    expect(api.reorderCategories).toHaveBeenCalledWith(1, [11, 10]);

    component.setCategorySelection(value.id, '10');
    component.assignCategory(value);
    expect(api.assignCategory).not.toHaveBeenCalled();
    expect(component.message()).toContain('already assigned');

    component.setCategorySelection(value.id, '99');
    component.assignCategory(value);
    expect(component.message()).toBe('Category is already assigned.');
  });

  it('renders selected product details and enforces the three-product limit before calling the backend', async () => {
    const references: StorefrontProductReference[] = [21, 22, 23].map(id => ({ id, name: products.find(item => item.id === id)!.name, slug: `product-${id}`, active: true, status: 'ACTIVE' }));
    references[2] = { ...references[2], active: false, status: 'ARCHIVED' };
    const value = { ...group(1, 'Office', 0), featuredProducts: references };
    const api = apiMock([value]);
    await configure(api);
    const fixture = TestBed.createComponent(AdminStorefrontComponent);
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;
    expect(element.textContent).toContain('Office / Desks / Standing desks');
    expect(element.textContent).toContain('Inactive — retained here for admin review and hidden publicly');
    expect(element.textContent).toContain('Three-product limit reached');
    expect(element.querySelector<HTMLSelectElement>('.featured-assignment-panel select')?.disabled).toBeTrue();

    const component = fixture.componentInstance as any;
    component.setProductSelection(value.id, '24');
    component.assignProduct(value);
    expect(api.assignFeaturedProduct).not.toHaveBeenCalled();
    expect(component.message()).toContain('no more than three');
  });

  it('reactively enables category and product Add buttons after selecting an option', async () => {
    const value = group(1, 'Office', 0);
    const assignedCategory = {
      id: 10, parentId: null, name: 'Office', slug: 'office', description: null, imageUrl: null,
      path: '/category/office', sortOrder: 0, active: true
    };
    const assignedProduct: StorefrontProductReference = {
      id: 21, name: 'Oak desk', slug: 'product-21', active: true, status: 'ACTIVE'
    };
    const api = apiMock([value]);
    api.assignCategory.and.returnValue(of({ ...value, categories: [assignedCategory] }));
    api.assignFeaturedProduct.and.returnValue(of({ ...value, categories: [assignedCategory], featuredProducts: [assignedProduct] }));
    await configure(api);
    const fixture = TestBed.createComponent(AdminStorefrontComponent);
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;

    const categoryPanel = element.querySelector<HTMLElement>('.group-assignment-panel')!;
    const categorySelect = categoryPanel.querySelector<HTMLSelectElement>('select')!;
    const categoryAdd = categoryPanel.querySelector<HTMLButtonElement>('.assignment-picker button')!;
    expect(categoryAdd.disabled).toBeTrue();
    categorySelect.value = '10';
    categorySelect.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    expect(categoryAdd.disabled).toBeFalse();
    categoryAdd.click();
    fixture.detectChanges();
    expect(api.assignCategory).toHaveBeenCalledWith(1, 10);
    expect((element.querySelector<HTMLElement>('.group-assignment-panel')!.querySelector('select') as HTMLSelectElement).value).toBe('');

    const productPanel = element.querySelector<HTMLElement>('.featured-assignment-panel')!;
    const productSelect = productPanel.querySelector<HTMLSelectElement>('select')!;
    const productAdd = productPanel.querySelector<HTMLButtonElement>('.assignment-picker button')!;
    expect(productAdd.disabled).toBeTrue();
    productSelect.value = '21';
    productSelect.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    expect(productAdd.disabled).toBeFalse();
    productAdd.click();
    fixture.detectChanges();
    expect(api.assignFeaturedProduct).toHaveBeenCalledWith(1, 21);
    expect((element.querySelector<HTMLElement>('.featured-assignment-panel')!.querySelector('select') as HTMLSelectElement).value).toBe('');
  });

  function apiMock(groups: NavigationGroup[] = []): jasmine.SpyObj<AdminStorefrontService> {
    const api = jasmine.createSpyObj<AdminStorefrontService>('AdminStorefrontService', [
      'settings', 'updateSettings', 'replaceHeroImage', 'groups', 'createGroup', 'updateGroup', 'deleteGroup',
      'setGroupActive', 'reorderGroups', 'assignCategory', 'removeCategory', 'reorderCategories',
      'assignFeaturedProduct', 'removeFeaturedProduct', 'reorderFeaturedProducts'
    ]);
    api.settings.and.returnValue(of(settings));
    api.groups.and.returnValue(of(groups));
    api.updateSettings.and.returnValue(of(settings));
    api.removeCategory.and.returnValue(of(void 0));
    api.removeFeaturedProduct.and.returnValue(of(void 0));
    return api;
  }

  async function configure(api: jasmine.SpyObj<AdminStorefrontService>): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [AdminStorefrontComponent],
      providers: [
        { provide: AdminStorefrontService, useValue: api },
        { provide: AdminCatalogService, useValue: { categoryOptions: () => of(categoryOptions) } },
        { provide: ProductService, useValue: { getAdminProducts: () => of(products) } }
      ]
    }).compileComponents();
  }

  function group(id: number, label: string, displayOrder: number): NavigationGroup {
    return { id, label, active: false, displayOrder, categories: [], featuredProducts: [] };
  }

  function product(id: number, name: string, categoryId: number, active: boolean): Product {
    return {
      id, name, slug: `product-${id}`, description: null, priceCents: id * 100, currency: 'EUR',
      imageUrl: `/product-${id}.jpg`, imageUrls: [`/product-${id}.jpg`], inStock: true, sizes: [], active,
      categoryId, categoryName: 'Category', categorySlug: 'category',
      configuration: { benefits: [], specifications: {}, sections: [], faqs: [], options: [], variants: [] }
    };
  }
});
