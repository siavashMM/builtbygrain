import { Component, EventEmitter, Input, Output, computed, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ProductCard, ProductCardColorSwatch } from '../product.service';
import { formatPrice } from './price.util';

@Component({
  selector: 'app-product-card',
  imports: [RouterLink],
  template: `
    <article class="product-card listing-product-card">
      <a class="listing-image-link" [routerLink]="['/products', product.slug]" [attr.aria-label]="'View ' + product.name">
        <img class="listing-image listing-image-primary" [src]="primaryImage()" [alt]="product.name">
        <img class="listing-image listing-image-hover" [src]="hoverImage()" alt="" aria-hidden="true">
      </a>
      <div class="listing-product-copy">
        <h3><a [routerLink]="['/products', product.slug]">{{ product.name }}</a></h3>
        <a class="product-category-link" routerLink="/" [queryParams]="{category: product.categorySlug}">{{ product.categoryName }}</a>
        @if (product.colorSwatches.length) {
          <div class="card-swatches" role="group" [attr.aria-label]="'Choose a color for ' + product.name">
            @for (swatch of product.colorSwatches; track swatch.id) {
              <button type="button" class="card-swatch" [class.selected]="selectedColorId() === swatch.id"
                [style.--swatch-color]="swatch.swatchHex || '#d8d3ca'"
                [style.--swatch-image]="swatch.swatchImageUrl ? 'url(' + swatch.swatchImageUrl + ')' : 'none'"
                [attr.aria-label]="swatch.label" [attr.title]="swatch.label"
                [attr.aria-pressed]="selectedColorId() === swatch.id" (click)="selectColor(swatch)">
                <span aria-hidden="true"></span>
              </button>
            }
          </div>
        }
        <div class="listing-product-footer">
          <p class="price"><span>From</span> {{ formatFromPrice() }}</p>
          <button type="button" class="add-button" (click)="add.emit({ product, colorId: selectedColorId() })">Add to bag</button>
        </div>
      </div>
    </article>
  `
})
export class ProductCardComponent {
  @Input({ required: true }) product!: ProductCard;
  @Output() add = new EventEmitter<{ product: ProductCard; colorId: number | null }>();

  protected readonly selectedColorId = signal<number | null>(null);
  private readonly selectedSwatch = computed(() => this.product.colorSwatches.find(swatch => swatch.id === this.selectedColorId()) ?? null);
  protected readonly primaryImage = computed(() => this.selectedSwatch()?.primaryImageUrl || this.product.primaryImageUrl || '/product-placeholder.svg');
  protected readonly hoverImage = computed(() => this.selectedSwatch()?.hoverImageUrl || this.product.hoverImageUrl || this.primaryImage());

  protected selectColor(swatch: ProductCardColorSwatch): void {
    this.selectedColorId.set(swatch.id);
  }

  protected formatFromPrice(): string {
    return formatPrice(this.product.fromPriceCents, this.product.currency);
  }
}
