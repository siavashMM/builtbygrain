import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { CartService } from '../cart/cart.service';
import { HealthService } from '../health.service';
import { ProductCard, ProductService } from '../product.service';
import { ShopNavigationComponent } from './shop-navigation.component';
import { ProductCardComponent } from './product-card.component';
import { ShopCatalogService, ShopCategory } from './shop-catalog.service';

type BackendState = 'checking' | 'online' | 'offline';
type ProductState = 'loading' | 'ready' | 'error';

@Component({
  selector: 'app-shop-home',
  imports: [ShopNavigationComponent, ProductCardComponent],
  templateUrl: './shop-home.component.html'
})
export class ShopHomeComponent implements OnInit {
  private readonly healthService = inject(HealthService);
  private readonly productService = inject(ProductService);
  private readonly cart = inject(CartService);
  private readonly route = inject(ActivatedRoute);
  private readonly catalog = inject(ShopCatalogService);

  protected readonly backendState = signal<BackendState>('checking');
  protected readonly backendStatus = signal('Checking backend');
  protected readonly backendTimestamp = signal<string | null>(null);
  protected readonly productState = signal<ProductState>('loading');
  protected readonly allProducts = signal<ProductCard[]>([]);
  protected readonly categories = signal<ShopCategory[]>([]);
  protected readonly selectedCategorySlug = signal<string | null>(null);
  protected readonly selectedCategory = computed(() => this.categories().find(category => category.slug === this.selectedCategorySlug()) ?? null);
  protected readonly products = computed(() => {
    const selected = this.selectedCategory();
    if (!selected) return this.allProducts();
    const ids = new Set<number>([selected.id]);
    let changed = true;
    while (changed) { changed = false; for (const category of this.categories()) if (category.parentId != null && ids.has(category.parentId) && !ids.has(category.id)) { ids.add(category.id); changed = true; } }
    return this.allProducts().filter(product => ids.has(product.categoryId));
  });
  protected readonly addedProductId = signal<number | null>(null);

  ngOnInit(): void {
    this.refreshBackendStatus();
    this.route.queryParamMap.subscribe(params => this.selectedCategorySlug.set(params.get('category')));
    this.catalog.categories().subscribe({ next: categories => this.categories.set(categories), error: () => this.categories.set([]) });
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
        this.allProducts.set(products);
        this.productState.set('ready');
      },
      error: () => {
        this.allProducts.set([]);
        this.productState.set('error');
      }
    });
  }

  protected addToBag(event: { product: ProductCard; colorId: number | null }): void {
    this.productService.getProduct(event.product.slug).subscribe(product => {
      const selected = event.colorId == null ? undefined : product.configuration.variants.find(variant => variant.optionValueIds.includes(`value-${event.colorId}`));
      if (this.cart.addProduct(product, 1, selected)) this.addedProductId.set(product.id);
    });
  }
}
