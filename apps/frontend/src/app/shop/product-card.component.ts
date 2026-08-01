import { Component, EventEmitter, Input, Output, computed, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ProductCard, ProductCardColorSwatch } from '../product.service';
import { formatPrice } from './price.util';

@Component({
  selector: 'app-product-card',
  imports: [RouterLink],
  template: `
    <article class="product-card listing-product-card">
      <a class="listing-image-link" [routerLink]="['/products', product.slug]" [attr.aria-label]="'View ' + product.name"
        (pointerenter)="enableHover()" (focusin)="enableHover()">
        <span class="product-card-index" aria-hidden="true">Built to last</span>
        <img class="listing-image listing-image-primary" [src]="primaryImage()" [alt]="product.name" loading="lazy" (error)="usePlaceholder($event)">
        @if (hoverEnabled()) {
          <img class="listing-image listing-image-hover" [src]="hoverImage()" alt="" aria-hidden="true" loading="lazy" (error)="usePlaceholder($event)">
        }
      </a>
      <div class="listing-product-copy">
        <h3><a [routerLink]="['/products', product.slug]">{{ product.name }}</a></h3>
        <a class="product-category-link" [routerLink]="categoryLink()">{{ product.categoryName }}</a>
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
          <a class="add-button" [routerLink]="['/products', product.slug]">Choose options</a>
        </div>
      </div>
    </article>
  `
})
export class ProductCardComponent {
  @Input({ required: true }) product!: ProductCard;
  // Kept as a public output for compatibility with existing recommendation hosts.
  @Output() add = new EventEmitter<{ product: ProductCard; colorId: number | null }>();
  protected readonly selectedColorId = signal<number | null>(null);
  protected readonly hoverEnabled = signal(false);
  private readonly selectedSwatch = computed(() => this.product.colorSwatches.find(swatch => swatch.id === this.selectedColorId()) ?? null);
  protected readonly primaryImage = computed(() => this.selectedSwatch()?.primaryImageUrl || this.product.primaryImageUrl || '/product-placeholder.svg');
  protected readonly hoverImage = computed(() => this.selectedSwatch()?.hoverImageUrl || this.product.hoverImageUrl || this.primaryImage());

  protected selectColor(swatch: ProductCardColorSwatch): void {
    this.selectedColorId.set(swatch.id);
    this.enableHover();
  }

  protected enableHover(): void { this.hoverEnabled.set(true); }

  protected formatFromPrice(): string {
    return formatPrice(this.product.fromPriceCents, this.product.currency);
  }

  protected categoryLink(): string { return `/category/${this.product.categoryPath || this.product.categorySlug}`; }

  protected usePlaceholder(event: Event): void {
    const image = event.target as HTMLImageElement;
    if (!image.src.endsWith('/product-placeholder.svg')) image.src = '/product-placeholder.svg';
  }
}
