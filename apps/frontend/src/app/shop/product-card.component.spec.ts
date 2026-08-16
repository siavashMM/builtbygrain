import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { ProductCard } from '../product.service';
import { ProductCardComponent } from './product-card.component';

describe('ProductCardComponent', () => {
  it('changes listing images when a color is selected and labels the lowest price', async () => {
    await TestBed.configureTestingModule({ imports: [ProductCardComponent], providers: [provideRouter([])] }).compileComponents();
    const fixture = TestBed.createComponent(ProductCardComponent);
    fixture.componentInstance.product = {
      id: 1, name: 'Oak shelf', slug: 'oak-shelf', currency: 'EUR', fromPriceCents: 6900,
      primaryImageUrl: '/default.jpg', hoverImageUrl: '/default-hover.jpg', categoryId: 2,
      categoryName: 'Shelves', categorySlug: 'shelves', colorSwatches: [
        { id: 4, label: 'Walnut', swatchHex: '#553322', primaryImageUrl: '/walnut.jpg', hoverImageUrl: '/walnut-hover.jpg' }
      ]
    } satisfies ProductCard;
    fixture.detectChanges();

    (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('.card-swatch')?.click();
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).querySelector<HTMLImageElement>('.listing-image-primary')?.src).toContain('/walnut.jpg');
    expect((fixture.nativeElement as HTMLElement).querySelector('.price')?.textContent).toContain('From');
    expect((fixture.nativeElement as HTMLElement).querySelector<HTMLAnchorElement>('.product-category-link')?.getAttribute('href')).toBe('/category/shelves');
    expect((fixture.nativeElement as HTMLElement).querySelector('.add-button')).toBeNull();
    expect((fixture.nativeElement as HTMLElement).querySelector<HTMLAnchorElement>('.listing-image-link')?.getAttribute('href')).toBe('/products/oak-shelf');
    expect((fixture.nativeElement as HTMLElement).textContent).not.toContain('Choose options');

    const image = (fixture.nativeElement as HTMLElement).querySelector<HTMLImageElement>('.listing-image-primary')!;
    image.dispatchEvent(new Event('error'));
    fixture.detectChanges();
    expect(image.src).toContain('/product-placeholder.svg');
  });

  it('links nested product categories to their complete category page path', async () => {
    await TestBed.configureTestingModule({ imports: [ProductCardComponent], providers: [provideRouter([])] }).compileComponents();
    const fixture = TestBed.createComponent(ProductCardComponent);
    fixture.componentInstance.product = {
      id: 2, name: 'Standing desk', slug: 'standing-desk', currency: 'EUR', fromPriceCents: 12900,
      primaryImageUrl: '/desk.jpg', hoverImageUrl: '/desk.jpg', categoryId: 3,
      categoryName: 'Standing desks', categorySlug: 'standing-desks', categoryPath: 'office/desks/standing-desks', colorSwatches: []
    } satisfies ProductCard;
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector<HTMLAnchorElement>('.product-category-link')?.getAttribute('href'))
      .toBe('/category/office/desks/standing-desks');
  });

  it('does not create the hover image until pointer or keyboard interaction', async () => {
    await TestBed.configureTestingModule({ imports: [ProductCardComponent], providers: [provideRouter([])] }).compileComponents();
    const fixture = TestBed.createComponent(ProductCardComponent);
    fixture.componentInstance.product = {
      id: 3, name: 'Bench', slug: 'bench', currency: 'EUR', fromPriceCents: 9900,
      primaryImageUrl: '/bench.jpg', hoverImageUrl: '/bench-hover.jpg', categoryId: 1,
      categoryName: 'Furniture', categorySlug: 'furniture', colorSwatches: []
    } satisfies ProductCard;
    fixture.detectChanges();

    const link = (fixture.nativeElement as HTMLElement).querySelector<HTMLElement>('.listing-image-link')!;
    expect(link.querySelector('.listing-image-hover')).toBeNull();
    link.dispatchEvent(new PointerEvent('pointerenter'));
    fixture.detectChanges();
    expect(link.querySelector<HTMLImageElement>('.listing-image-hover')?.src).toContain('/bench-hover.jpg');
  });
});
