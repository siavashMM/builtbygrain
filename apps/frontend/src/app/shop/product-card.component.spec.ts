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
  });
});
