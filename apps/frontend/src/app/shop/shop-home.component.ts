import { Component, ElementRef, OnInit, ViewChild, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ProductCard, ProductService } from '../product.service';
import { ProductCardComponent } from './product-card.component';
import { PublicStorefrontConfiguration, ShopCatalogService, ShopCategory } from './shop-catalog.service';
import { ShopNavigationComponent } from './shop-navigation.component';

type ProductState = 'loading' | 'ready' | 'error';
type NewsletterState = 'idle' | 'invalid' | 'unavailable';

@Component({
  selector: 'app-shop-home',
  imports: [RouterLink, ShopNavigationComponent, ProductCardComponent],
  templateUrl: './shop-home.component.html'
})
export class ShopHomeComponent implements OnInit {
  private static readonly DEFAULT_HERO_HEADING = 'Handcrafted wooden goods, shaped for everyday use.';
  private static readonly DEFAULT_HERO_SUPPORT = 'Thoughtfully made pieces for calmer desks, organised homes and durable everyday rituals.';
  private static readonly DEFAULT_HERO_ALT = 'Handcrafted wooden shelf in natural oak';
  private readonly productService = inject(ProductService);
  private readonly catalog = inject(ShopCatalogService);
  @ViewChild('featuredProductRail') private featuredProductRail?: ElementRef<HTMLElement>;

  protected readonly productState = signal<ProductState>('loading');
  protected readonly allProducts = signal<ProductCard[]>([]);
  protected readonly categories = signal<ShopCategory[]>([]);
  protected readonly storefrontConfiguration = signal<PublicStorefrontConfiguration | null>(null);
  protected readonly heroImageFailed = signal(false);
  protected readonly heroImageLoaded = signal(false);
  protected readonly newsletterState = signal<NewsletterState>('idle');
  protected readonly homeCategories = computed(() => this.categories().filter(category => category.active && category.parentId === null));
  protected readonly editorialCategories = computed(() => this.homeCategories().slice(0, 3));
  protected readonly featuredProducts = computed(() => this.allProducts().slice(0, 8));
  protected readonly fallbackHeroImage = computed(() => this.allProducts()[0]?.primaryImageUrl || '/product-placeholder.svg');
  protected readonly usesCustomHero = computed(() => !!this.storefrontConfiguration()?.settings.heroImageUrl && !this.heroImageFailed());
  protected readonly heroImage = computed(() => this.usesCustomHero()
    ? this.storefrontConfiguration()!.settings.heroImageUrl!
    : this.fallbackHeroImage());
  protected readonly heroAlt = computed(() => this.usesCustomHero()
    ? this.storefrontConfiguration()!.settings.heroImageAltText || ShopHomeComponent.DEFAULT_HERO_ALT
    : ShopHomeComponent.DEFAULT_HERO_ALT);
  protected readonly heroHeading = computed(() => this.storefrontConfiguration()?.settings.heroHeading || ShopHomeComponent.DEFAULT_HERO_HEADING);
  protected readonly heroSupportingText = computed(() => this.storefrontConfiguration()?.settings.heroSupportingText || ShopHomeComponent.DEFAULT_HERO_SUPPORT);

  ngOnInit(): void {
    this.catalog.categories().subscribe({ next: categories => this.categories.set(categories), error: () => this.categories.set([]) });
    this.catalog.storefront().subscribe({
      next: configuration => {
        this.storefrontConfiguration.set(configuration);
        this.heroImageFailed.set(false);
        this.heroImageLoaded.set(false);
      },
      error: () => this.storefrontConfiguration.set(null)
    });
    this.loadProducts();
  }

  protected handleHeroError(): void {
    if (this.usesCustomHero()) {
      this.heroImageFailed.set(true);
      this.heroImageLoaded.set(false);
    }
  }

  protected handleHeroLoad(): void {
    this.heroImageLoaded.set(true);
  }

  protected loadProducts(): void {
    this.productState.set('loading');
    this.productService.getProducts().subscribe({
      next: products => { this.allProducts.set(products); this.productState.set('ready'); },
      error: () => { this.allProducts.set([]); this.productState.set('error'); }
    });
  }

  protected scrollFeaturedProducts(direction: 'previous' | 'next'): void {
    const rail = this.featuredProductRail?.nativeElement;
    if (!rail) return;

    const firstItem = rail.querySelector<HTMLElement>('app-product-card, .product-skeleton');
    const itemWidth = firstItem?.getBoundingClientRect().width ?? rail.clientWidth * 0.8;
    const styles = window.getComputedStyle(rail);
    const gap = Number.parseFloat(styles.columnGap || styles.gap || '0') || 0;
    rail.scrollBy({ left: (itemWidth + gap) * (direction === 'next' ? 1 : -1), behavior: 'smooth' });
  }

  protected imageForCategory(category: ShopCategory, index: number): string {
    const categoryIds = new Set<number>([category.id]);
    for (let found = true; found;) {
      found = false;
      for (const item of this.categories()) {
        if (item.parentId != null && categoryIds.has(item.parentId) && !categoryIds.has(item.id)) {
          categoryIds.add(item.id);
          found = true;
        }
      }
    }
    const product = this.allProducts().find(item => categoryIds.has(item.categoryId));
    const fallback = this.allProducts()[index % Math.max(this.allProducts().length, 1)] ?? this.allProducts()[0];
    return category?.imageUrl || product?.primaryImageUrl || fallback?.primaryImageUrl || '/product-placeholder.svg';
  }

  protected submitNewsletter(event: Event, email: string): void {
    event.preventDefault();
    if (!/^\S+@\S+\.\S+$/.test(email.trim())) { this.newsletterState.set('invalid'); return; }
    // TODO: Connect this form when a newsletter subscription endpoint is available.
    this.newsletterState.set('unavailable');
  }
}
