import { Component, OnInit, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { CartService } from '../cart/cart.service';
import { HealthService } from '../health.service';
import { Product, ProductService } from '../product.service';
import { formatPrice } from './price.util';
import { ShopNavigationComponent } from './shop-navigation.component';

type BackendState = 'checking' | 'online' | 'offline';
type ProductState = 'loading' | 'ready' | 'error';

@Component({
  selector: 'app-shop-home',
  imports: [RouterLink, ShopNavigationComponent],
  templateUrl: './shop-home.component.html'
})
export class ShopHomeComponent implements OnInit {
  private readonly healthService = inject(HealthService);
  private readonly productService = inject(ProductService);
  private readonly cart = inject(CartService);
  private readonly router = inject(Router);

  protected readonly backendState = signal<BackendState>('checking');
  protected readonly backendStatus = signal('Checking backend');
  protected readonly backendTimestamp = signal<string | null>(null);
  protected readonly productState = signal<ProductState>('loading');
  protected readonly products = signal<Product[]>([]);
  protected readonly selectedImageIndexes = signal<Record<number, number>>({});
  protected readonly addedProductId = signal<number | null>(null);

  ngOnInit(): void {
    this.refreshBackendStatus();
    this.loadProducts();
  }

  protected refreshBackendStatus(): void {
    this.backendState.set('checking');
    this.backendStatus.set('Checking backend');
    this.backendTimestamp.set(null);

    this.healthService.getHealth().subscribe({
      next: (health) => {
        this.backendState.set('online');
        this.backendStatus.set(health.status);
        this.backendTimestamp.set(health.timestamp);
      },
      error: () => {
        this.backendState.set('offline');
        this.backendStatus.set('Unavailable');
        this.backendTimestamp.set(null);
      }
    });
  }

  protected loadProducts(): void {
    this.productState.set('loading');

    this.productService.getProducts().subscribe({
      next: (products) => {
        this.products.set(products);
        this.productState.set('ready');
      },
      error: () => {
        this.products.set([]);
        this.productState.set('error');
      }
    });
  }

  protected formatPrice(product: Product): string {
    return formatPrice(product.priceCents, product.currency);
  }

  protected shortDescription(product: Product): string {
    const description = product.description || 'Handcrafted wooden product from Built by Grain.';
    return description.length > 120 ? `${description.slice(0, 117).trimEnd()}...` : description;
  }

  protected openProduct(product: Product): void {
    void this.router.navigate(['/products', product.slug]);
  }

  protected handleCardKey(event: KeyboardEvent, product: Product): void {
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      this.openProduct(product);
    }
  }

  protected addToBag(event: Event, product: Product): void {
    event.stopPropagation();
    if (this.cart.addProduct(product)) {
      this.addedProductId.set(product.id);
    }
  }

  protected selectedImage(product: Product): string {
    if (product.imageUrls.length === 0) {
      return '/product-placeholder.svg';
    }
    return product.imageUrls[this.selectedImageIndex(product)] ?? product.imageUrls[0];
  }

  protected selectedImageIndex(product: Product): number {
    return this.selectedImageIndexes()[product.id] ?? 0;
  }

  protected selectImage(product: Product, index: number): void {
    this.selectedImageIndexes.update((indexes) => ({ ...indexes, [product.id]: index }));
  }

  protected previousImage(product: Product): void {
    const count = product.imageUrls.length;
    if (count < 2) return;
    this.selectImage(product, (this.selectedImageIndex(product) - 1 + count) % count);
  }

  protected nextImage(product: Product): void {
    const count = product.imageUrls.length;
    if (count < 2) return;
    this.selectImage(product, (this.selectedImageIndex(product) + 1) % count);
  }
}
