import { TestBed } from '@angular/core/testing';
import { AdminProductFormComponent } from './admin-product-form.component';
import { ProductRequest } from '../product.service';

describe('AdminProductFormComponent', () => {
  it('normalizes an entered slug before creating a product', async () => {
    await TestBed.configureTestingModule({
      imports: [AdminProductFormComponent]
    }).compileComponents();

    const fixture = TestBed.createComponent(AdminProductFormComponent);
    const component = fixture.componentInstance;
    component.categoryId = 5;
    (component as any).form.update((form: object) => ({
      ...form,
      name: 'Sita floating shelves',
      slug: 'Sita_floating_shelves'
    }));

    let request: ProductRequest | undefined;
    component.saveProduct.subscribe(event => request = event.request);
    (component as any).submit({ preventDefault: () => undefined } as SubmitEvent);

    expect(request?.slug).toBe('sita-floating-shelves');
    expect(request?.categoryId).toBe(5);
  });
});
