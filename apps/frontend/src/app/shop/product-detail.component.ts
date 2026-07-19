import { DOCUMENT, KeyValuePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, HostListener, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatDividerModule } from '@angular/material/divider';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Meta, Title } from '@angular/platform-browser';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Subscription, distinctUntilChanged, map } from 'rxjs';
import { CartService } from '../cart/cart.service';
import { Product, ProductCard, ProductOption, ProductVariant, ProductService } from '../product.service';
import { WishlistService } from '../wishlist.service';
import { formatPrice } from './price.util';
import { ShopNavigationComponent } from './shop-navigation.component';
import { ProductGalleryComponent } from './product-gallery.component';
import { ProductCardComponent } from './product-card.component';

type DetailState = 'loading' | 'ready' | 'not-found' | 'error';

@Component({ selector: 'app-product-detail', imports: [RouterLink, ShopNavigationComponent, ProductGalleryComponent, ProductCardComponent, KeyValuePipe, MatButtonModule, MatCardModule, MatChipsModule, MatDividerModule, MatExpansionModule, MatProgressSpinnerModule, MatSelectModule, MatSnackBarModule, MatTooltipModule], templateUrl: './product-detail.component.html' })
export class ProductDetailComponent implements OnInit, OnDestroy {
  private readonly route = inject(ActivatedRoute); private readonly router = inject(Router);
  private readonly productService = inject(ProductService); private readonly cart = inject(CartService);
  private readonly wishlist = inject(WishlistService); private readonly title = inject(Title); private readonly meta = inject(Meta);
  private readonly document = inject(DOCUMENT);
  private readonly snackBar = inject(MatSnackBar);
  private routeSubscription?: Subscription;
  private productSubscription?: Subscription;
  private recommendationsSubscription?: Subscription;
  protected readonly state = signal<DetailState>('loading'); protected readonly product = signal<Product | null>(null);
  protected readonly quantity = signal(1); protected readonly selectedImageIndex = signal(0); protected readonly message = signal('');
  protected readonly selectedValues = signal<Record<string, string>>({}); protected readonly modalOpen = signal(false);
  protected readonly previewColorName = signal<string | null>(null);
  protected readonly recommendations = signal<ProductCard[]>([]); protected readonly adding = signal(false);
  protected readonly selectedVariant = computed(() => this.resolveVariant(this.selectedValues()));
  protected readonly displayImages = computed(() => {
    const p = this.product();
    const selectedIds = Object.entries(this.selectedValues()).filter(([optionId]) => p?.configuration.sizeAffectsImages !== false || optionId !== 'size').map(([, valueId]) => valueId);
    const imageVariant = this.selectedVariant() ?? p?.configuration.variants.find((variant) => variant.imageUrls?.length && selectedIds.every((id) => variant.optionValueIds.includes(id)));
    // Variant-specific photography leads the gallery, while the general product
    // photography remains available as supporting context.
    const images = [...(imageVariant?.imageUrls ?? []), ...(p?.imageUrls ?? [])]
      .filter((image, index, all) => !!image && all.indexOf(image) === index);
    return images.length ? images : ['/product-placeholder.svg'];
  });

  ngOnInit(): void {
    this.routeSubscription = this.route.paramMap.pipe(
      map(params => params.get('slug')),
      distinctUntilChanged()
    ).subscribe(() => this.loadProduct());
  }
  ngOnDestroy(): void {
    this.routeSubscription?.unsubscribe();
    this.productSubscription?.unsubscribe();
    this.recommendationsSubscription?.unsubscribe();
    this.document.getElementById('product-jsonld')?.remove();
  }
  protected loadProduct(): void {
    const slug = this.route.snapshot.paramMap.get('slug');
    this.productSubscription?.unsubscribe();
    this.recommendationsSubscription?.unsubscribe();
    this.product.set(null); this.recommendations.set([]); this.selectedValues.set({}); this.selectedImageIndex.set(0);
    this.quantity.set(1); this.message.set(''); this.previewColorName.set(null); this.modalOpen.set(false);
    if (!slug) { this.state.set('not-found'); return; }
    const variantId = this.route.snapshot.queryParamMap.get('variant'); this.state.set('loading');
    this.productSubscription = this.productService.getProduct(slug, variantId).subscribe({ next: (product) => {
      this.product.set(product); this.restoreVariant(variantId); this.state.set('ready'); this.updateSeo(product); this.loadRecommendations(product.id);
    }, error: (error: HttpErrorResponse) => { this.product.set(null); this.state.set(error.status === 404 ? 'not-found' : 'error'); } });
  }
  protected selectOption(option: ProductOption, valueId: string): void {
    if (!this.isValuePossible(option.id, valueId)) return;
    this.selectedValues.update((values) => ({ ...values, [option.id]: valueId })); this.selectedImageIndex.set(0); this.message.set('');
    const variant = this.selectedVariant();
    void this.router.navigate([], { relativeTo: this.route, queryParams: { variant: variant?.id ?? null }, queryParamsHandling: 'merge', replaceUrl: true });
  }
  protected isValuePossible(optionId: string, valueId: string): boolean {
    const p = this.product(); if (!p) return false;
    const candidate = { ...this.selectedValues(), [optionId]: valueId };
    return p.configuration.variants.some((variant) => variant.stockStatus !== 'DISCONTINUED' && Object.entries(candidate).every(([id, selected]) => {
      const option = p.configuration.options.find((item) => item.id === id); const value = option?.values.find((item) => item.id === selected);
      return !!value && variant.optionValueIds.includes(value.id);
    }));
  }
  protected selectImage(index: number): void { this.selectedImageIndex.set(index); }
  protected moveImage(delta: number): void { this.selectedImageIndex.update((i) => (i + delta + this.displayImages().length) % this.displayImages().length); }
  protected changeQuantity(delta: number): void { this.setQuantity(this.quantity() + delta); }
  protected setQuantity(value: number): void { const max = this.maxQuantity(); this.quantity.set(Math.min(max, Math.max(1, Math.trunc(value || 1)))); this.message.set(''); }
  protected maxQuantity(): number { return 99; }
  protected addToBag(product: Product): void {
    const variant = this.selectedVariant();
    if (!variant) { this.message.set('Please select all product options.'); return; }
    this.adding.set(true); const success = this.cart.addProduct(product, this.quantity(), variant, this.variantSummary()); this.adding.set(false);
    this.message.set(success ? 'Added to your bag.' : 'This selection or quantity is no longer available.');
    this.snackBar.open(this.message(), success ? 'View bag' : 'Dismiss', { duration: 3500, horizontalPosition: 'center' }).onAction().subscribe(() => { if (success) void this.router.navigate(['/cart']); });
  }
  protected toggleWishlist(productId: number): void { this.wishlist.toggle(productId); this.message.set(this.wishlist.has(productId) ? 'Saved to your wishlist.' : 'Removed from your wishlist.'); this.snackBar.open(this.message(), 'Dismiss', { duration: 2500 }); }
  protected addRecommended(event: {product:ProductCard;colorId:number|null}): void { this.productService.getProduct(event.product.slug).subscribe(product => { const variant = event.colorId == null ? product.configuration.variants.find(item => item.available) : product.configuration.variants.find(item => item.available && item.optionValueIds.includes(`value-${event.colorId}`)); const success = !!variant && this.cart.addProduct(product, 1, variant); this.snackBar.open(success ? `${product.name} added to your bag.` : `${product.name} is unavailable.`, 'Dismiss', { duration: 2500 }); }); }
  protected isWishlisted(id: number): boolean { return this.wishlist.has(id); }
  protected format(cents: number, currency: string): string { return formatPrice(cents, currency); }
  protected discount(variant: ProductVariant): number { return variant.salePriceCents ? Math.round((1 - variant.salePriceCents / variant.priceCents) * 100) : 0; }
  protected variantSummary(): string { const p = this.product(); if (!p) return ''; return p.configuration.options.map((o) => o.values.find((v) => v.id === this.selectedValues()[o.id])?.label).filter(Boolean).join(' · '); }
  protected selectedOptionLabel(option: ProductOption): string { return option.values.find((value) => value.id === this.selectedValues()[option.id])?.label ?? ''; }
  protected displayedColorName(option: ProductOption): string { return this.previewColorName() ?? this.selectedOptionLabel(option); }
  protected previewColor(name: string | null): void { this.previewColorName.set(name); }
  protected stockMessage(variant: ProductVariant | null): string {
    if (!variant) return 'Select your options to check availability.';
    return ({ IN_STOCK: 'In stock and ready to ship', LOW_STOCK: 'Low stock', OUT_OF_STOCK: 'Currently unavailable', BACKORDER: variant.deliveryEstimate || 'Available on backorder', PREORDER: variant.deliveryEstimate || 'Available for preorder', DISCONTINUED: 'No longer available' })[variant.stockStatus];
  }
  protected openModal(): void { this.modalOpen.set(true); setTimeout(() => this.document.querySelector<HTMLButtonElement>('.modal-close')?.focus()); }
  protected closeModal(): void { this.modalOpen.set(false); }
  @HostListener('document:keydown.escape') onEscape(): void { if (this.modalOpen()) this.closeModal(); }
  private loadRecommendations(productId: number): void { this.recommendationsSubscription = this.productService.getProducts().subscribe({ next: (items) => this.recommendations.set(items.filter(item => item.id !== productId).slice(0, 3)), error: () => this.recommendations.set([]) }); }
  private resolveVariant(values: Record<string, string>): ProductVariant | null {
    const p = this.product(); if (!p) return null; const selected = Object.values(values);
    if (p.configuration.options.length !== selected.length) return p.configuration.options.length === 0 ? p.configuration.variants[0] ?? null : null;
    return p.configuration.variants.find((v) => selected.every((id) => v.optionValueIds.includes(id))) ?? null;
  }
  private restoreVariant(id: string | null): void {
    const p = this.product()!; const variant = p.configuration.variants.find((v) => v.id === id) ?? (p.configuration.variants.length === 1 ? p.configuration.variants[0] : null);
    if (!variant) return; const values: Record<string, string> = {}; p.configuration.options.forEach((o) => { const value = o.values.find((v) => variant.optionValueIds.includes(v.id)); if (value) values[o.id] = value.id; }); this.selectedValues.set(values);
  }
  private updateSeo(p: Product): void {
    const variant = this.selectedVariant() ?? p.configuration.variants[0]; const price = variant?.salePriceCents ?? variant?.priceCents ?? p.priceCents;
    this.title.setTitle(`${p.name} | Built by Grain`); this.meta.updateTag({ name: 'description', content: p.configuration.subtitle || p.description || p.name });
    this.meta.updateTag({ property: 'og:title', content: p.name }); this.meta.updateTag({ property: 'og:image', content: this.displayImages()[0] });
    let script = this.document.getElementById('product-jsonld') as HTMLScriptElement | null; if (!script) { script = this.document.createElement('script'); script.id = 'product-jsonld'; script.type = 'application/ld+json'; this.document.head.appendChild(script); }
    script.text = JSON.stringify({ '@context': 'https://schema.org', '@type': 'Product', name: p.name, image: this.displayImages(), description: p.description, sku: variant?.sku, offers: { '@type': 'Offer', priceCurrency: p.currency, price: (price / 100).toFixed(2), availability: variant?.available ? 'https://schema.org/InStock' : 'https://schema.org/OutOfStock', url: `${this.document.location.origin}/products/${p.slug}` }, ...(p.configuration.rating ? { aggregateRating: { '@type': 'AggregateRating', ratingValue: p.configuration.rating.average, reviewCount: p.configuration.rating.count } } : {}) });
  }
}
