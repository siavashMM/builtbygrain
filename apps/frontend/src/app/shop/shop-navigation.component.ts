import { A11yModule } from '@angular/cdk/a11y';
import { DOCUMENT } from '@angular/common';
import { Component, ElementRef, HostListener, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink } from '@angular/router';
import { Subscription, filter } from 'rxjs';

import { AccountService } from '../account/account.service';
import { CartService } from '../cart/cart.service';
import { ProductCard, ProductService } from '../product.service';
import { formatPrice } from './price.util';
import { PublicNavigationGroup, ShopCatalogService, ShopCategory } from './shop-catalog.service';

type HeaderSearchCategory = Pick<ShopCategory, 'id' | 'name' | 'path'>;

@Component({
  selector: 'app-shop-navigation',
  imports: [RouterLink, A11yModule],
  template: `
    <header class="site-header"
      [class.homepage-header]="isHomepage()"
      [class.header-at-top]="isHomepage() && !headerScrolled()"
      [class.header-scrolled]="isHomepage() && headerScrolled()"
      [class.header-interacting]="activeDesktopGroupId() !== null || searchPanelOpen()"
      (mouseleave)="closeDesktopMenu(false)">
      <div class="utility-bar">
        <p>Complimentary delivery on orders over €75 <span aria-hidden="true">•</span> 30-day returns</p>
        <nav aria-label="Utility navigation">
          <a href="mailto:hello@builtbygrain.example">Customer care</a>
          <a routerLink="/" fragment="craftsmanship">Our craft</a>
          <span>EN / EUR</span>
        </nav>
      </div>

      <div class="main-nav">
        <button class="mobile-nav-toggle icon-button" type="button" (click)="toggleMenu()"
          [attr.aria-expanded]="menuOpen()" aria-controls="mobile-shop-menu"
          [attr.aria-label]="menuOpen() ? 'Close shop menu' : 'Open shop menu'">
          @if (menuOpen()) {
            <svg viewBox="0 0 24 24" aria-hidden="true"><path d="m6 6 12 12M18 6 6 18"/></svg>
          } @else {
            <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 7h16M4 12h16M4 17h16"/></svg>
          }
        </button>

        <a routerLink="/" class="shop-brand" aria-label="Built by Grain home" (click)="closeDestinations()">
          <span class="brand-mark" aria-hidden="true"><img src="/builtbygrain-mark.png" alt=""></span>
          <span>Built by Grain<small>Handcrafted wooden goods</small></span>
        </a>

        <form class="header-search" role="search" (submit)="submitSearch($event)" (focusout)="onSearchFocusOut($event)">
          <label class="sr-only" for="storefront-product-search">Search products and collections</label>
          <button class="header-search-submit" type="submit" aria-label="Search">
            <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="10.8" cy="10.8" r="6.8"/><path d="m16 16 4.2 4.2"/></svg>
          </button>
          <input #searchInput id="storefront-product-search" type="search" inputmode="search" autocomplete="off"
            placeholder="Search handcrafted pieces..." [value]="searchQuery()"
            [attr.aria-expanded]="searchPanelOpen()" aria-controls="storefront-search-results"
            (input)="updateSearch($any($event.target).value)" (focus)="openSearch()">
          @if (searchQuery()) {
            <button class="header-search-clear" type="button" aria-label="Clear search" (click)="clearSearch(searchInput)">
              <svg viewBox="0 0 24 24" aria-hidden="true"><path d="m7 7 10 10M17 7 7 17"/></svg>
            </button>
          }

          @if (searchPanelOpen()) {
            <section id="storefront-search-results" class="header-search-panel" aria-label="Search suggestions">
              <div class="search-panel-heading">
                <p>{{ searchQuery().trim() ? 'Matching pieces' : 'Popular pieces' }}</p>
                @if (!searchLoading()) {
                  <span>{{ searchProductResults().length + searchCategoryResults().length }} suggestions</span>
                }
              </div>
              @if (searchLoading()) {
                <p class="search-panel-state" aria-live="polite">Loading suggestions…</p>
              } @else if (searchProductResults().length || searchCategoryResults().length) {
                @if (searchProductResults().length) {
                  <div class="search-product-results">
                    @for (product of searchProductResults(); track product.id) {
                      <a [routerLink]="['/products', product.slug]" (click)="selectSearchResult()">
                        <img [src]="product.primaryImageUrl || '/product-placeholder.svg'" alt="" width="64" height="64" loading="lazy" (error)="usePlaceholder($event)">
                        <span><strong>{{ product.name }}</strong><small>{{ product.categoryName }} · {{ format(product.fromPriceCents, product.currency) }}</small></span>
                        <b aria-hidden="true">→</b>
                      </a>
                    }
                  </div>
                }
                @if (searchCategoryResults().length) {
                  <div class="search-category-results">
                    <p>Collections</p>
                    @for (category of searchCategoryResults(); track category.id) {
                      <a [routerLink]="category.path" (click)="selectSearchResult()">{{ category.name }} <span aria-hidden="true">→</span></a>
                    }
                  </div>
                }
              } @else {
                <div class="search-panel-state" aria-live="polite">
                  <strong>No matching pieces yet</strong>
                  <span>Try a product name or one of our collections.</span>
                  <a routerLink="/" fragment="featured-products" (click)="selectSearchResult()">Browse all products <span aria-hidden="true">→</span></a>
                </div>
              }
            </section>
          }
        </form>

        <div class="nav-actions">
          <a class="icon-button account-button" routerLink="/account"
            [attr.aria-label]="accounts.customer() ? 'My account, signed in' : 'My account'"
            [title]="accounts.customer() ? 'My account — Signed in' : 'My account'" (click)="closeDestinations()">
            <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="8" r="3.5"/><path d="M5.5 20a6.5 6.5 0 0 1 13 0"/></svg>
            @if (accounts.customer()) {
              <span class="account-status-badge" title="Signed in" aria-hidden="true">✓</span>
              <span class="sr-only">Signed in</span>
            }
          </a>
          <button type="button" class="icon-button bag-button" (click)="openCart()"
            aria-haspopup="dialog" aria-controls="cart-drawer"
            [attr.aria-expanded]="cartDrawerOpen()"
            [attr.aria-label]="'Open shopping bag, ' + cart.itemCount() + (cart.itemCount() === 1 ? ' item' : ' items')">
            <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M5 8.5h14l-1 12H6l-1-12Z"/><path d="M9 9V6a3 3 0 0 1 6 0v3"/></svg>
            @if (cart.itemCount() > 0) {
              <span class="cart-count-badge" aria-hidden="true">{{ cart.itemCount() > 99 ? '99+' : cart.itemCount() }}</span>
            }
          </button>
        </div>
      </div>

      <nav class="category-nav navigation-group-nav" aria-label="Shop navigation groups" [attr.aria-busy]="navigationLoading()">
        @for (group of navigationGroups(); track group.id) {
          <button type="button" class="navigation-group-trigger" [class.active]="activeDesktopGroupId() === group.id"
            [attr.data-group-id]="group.id" aria-haspopup="true"
            [attr.aria-expanded]="activeDesktopGroupId() === group.id"
            [attr.aria-controls]="'mega-menu-' + group.id"
            (mouseenter)="openDesktopMenu(group)" (focus)="openDesktopMenuFromFocus(group)" (click)="toggleDesktopMenu(group)">
            <span>{{ group.label }}</span>
          </button>
        }
      </nav>

      @if (activeDesktopGroup(); as group) {
        <section class="storefront-mega-menu" [id]="'mega-menu-' + group.id" [attr.aria-label]="group.label + ' navigation'">
          <div class="mega-menu-inner">
            <div class="mega-category-column">
              <p class="mega-menu-eyebrow">Explore {{ group.label }}</p>
              <h2>{{ group.label }}</h2>
              @if (group.categories.length) {
                <nav [attr.aria-label]="group.label + ' categories'">
                  @for (category of group.categories; track category.id) {
                    <a [routerLink]="category.path" (click)="closeDestinations()">
                      <span>{{ category.name }}</span><small>{{ category.description || 'View this collection' }}</small><b aria-hidden="true">→</b>
                    </a>
                  }
                </nav>
              } @else {
                <p class="mega-menu-empty">Collections for this group are being prepared.</p>
              }
            </div>

            @if (group.featuredProducts.length) {
              <div class="mega-products-column">
                <div class="mega-products-heading"><p>Featured pieces</p><span>{{ group.featuredProducts.length }} selected</span></div>
                <div class="mega-product-grid">
                  @for (product of group.featuredProducts.slice(0, 3); track product.id) {
                    <a class="mega-product-card" [routerLink]="['/products', product.slug]" (click)="closeDestinations()">
                      <img [src]="product.primaryImageUrl || '/product-placeholder.svg'" [alt]="product.name" loading="lazy" (error)="usePlaceholder($event)">
                      <span><strong>{{ product.name }}</strong><small>{{ format(product.fromPriceCents, product.currency) }}</small></span>
                    </a>
                  }
                </div>
              </div>
            }
          </div>
        </section>
      }
    </header>

    @if (menuOpen()) {
      <div class="mobile-menu-scrim" (click)="closeMenu()" aria-hidden="true"></div>
      <nav id="mobile-shop-menu" class="mobile-shop-menu" aria-label="Mobile shop navigation" cdkTrapFocus>
        <div class="mobile-menu-heading">
          @if (activeMobileGroup()) {
            <button type="button" class="mobile-menu-back" (click)="backToGroups()">← Back</button>
          } @else {
            <span class="brand-mark" aria-hidden="true"><img src="/builtbygrain-mark.png" alt=""></span>
          }
          <button type="button" class="icon-button" (click)="closeMenu()" aria-label="Close shop menu">
            <svg viewBox="0 0 24 24" aria-hidden="true"><path d="m6 6 12 12M18 6 6 18"/></svg>
          </button>
        </div>

        @if (activeMobileGroup(); as group) {
          <p>{{ group.label }}</p>
          <div class="mobile-group-content">
            <div class="mobile-group-categories">
              @for (category of group.categories; track category.id) {
                <a [routerLink]="category.path" (click)="closeDestinations()"><span>{{ category.name }}</span><span aria-hidden="true">↗</span></a>
              }
              @if (!group.categories.length) { <p class="mobile-navigation-empty">No collections are currently assigned.</p> }
            </div>
            @if (group.featuredProducts.length) {
              <section class="mobile-featured-products" [attr.aria-label]="group.label + ' featured products'">
                <h2>Featured pieces</h2>
                @for (product of group.featuredProducts.slice(0, 3); track product.id) {
                  <a [routerLink]="['/products', product.slug]" (click)="closeDestinations()">
                    <img [src]="product.primaryImageUrl || '/product-placeholder.svg'" alt="" loading="lazy" (error)="usePlaceholder($event)">
                    <span><strong>{{ product.name }}</strong><small>{{ format(product.fromPriceCents, product.currency) }}</small></span>
                  </a>
                }
              </section>
            }
          </div>
        } @else {
          <p>Shop our collections</p>
          @if (navigationLoading()) {
            <p class="mobile-navigation-empty" aria-live="polite">Loading collections…</p>
          }
          @for (group of navigationGroups(); track group.id) {
            <button type="button" class="mobile-category-drill" (click)="openMobileGroup(group)">
              <span>{{ group.label }}</span><span aria-hidden="true">›</span>
            </button>
          }
          <a routerLink="/" fragment="featured-products" (click)="closeDestinations()"><span>All products</span><span aria-hidden="true">↗</span></a>
          <div class="mobile-menu-meta">
            <a routerLink="/account" (click)="closeDestinations()">My account</a>
            <a href="mailto:hello@builtbygrain.example">Customer care</a>
            <a routerLink="/" fragment="craftsmanship" (click)="closeDestinations()">Our craft</a>
          </div>
          <a class="mobile-menu-cta" routerLink="/" fragment="featured-products" (click)="closeDestinations()">View all products</a>
        }
      </nav>
    }

    @if (cartDrawerOpen()) {
      <div class="cart-drawer-layer">
        <div class="cart-drawer-scrim" aria-hidden="true" (click)="closeCart()"></div>
        <aside id="cart-drawer" class="cart-drawer" role="dialog" aria-modal="true" aria-labelledby="cart-drawer-title"
          cdkTrapFocus [cdkTrapFocusAutoCapture]="true">
          <header class="cart-drawer-header">
            <h2 id="cart-drawer-title">Shopping bag
              @if (cart.itemCount() > 0) { <span>({{ cart.itemCount() }})</span> }
            </h2>
            <button type="button" class="cart-drawer-close" (click)="closeCart()" aria-label="Close shopping bag">
              <svg viewBox="0 0 24 24" aria-hidden="true"><path d="m6 6 12 12M18 6 6 18"/></svg>
            </button>
          </header>

          <div class="cart-drawer-body">
            @if (cart.items().length === 0) {
              <div class="cart-drawer-empty">
                <p>Your shopping bag is currently empty. <a routerLink="/" fragment="featured-products" (click)="closeCart(false)">Continue browsing</a></p>
              </div>
            } @else {
              <div class="cart-drawer-items">
                @for (item of cart.items(); track item.productId + '-' + item.variantId) {
                  <article class="cart-drawer-item">
                    <a class="cart-drawer-image" [routerLink]="['/products', item.slug]" [queryParams]="{ variant: item.variantId }" (click)="closeCart(false)">
                      <img [src]="item.imageUrl || '/product-placeholder.svg'" [alt]="item.name" (error)="usePlaceholder($event)">
                    </a>
                    <div class="cart-drawer-item-copy">
                      <h3><a [routerLink]="['/products', item.slug]" [queryParams]="{ variant: item.variantId }" (click)="closeCart(false)">{{ item.name }}</a></h3>
                      @if (item.variantSummary) { <p>{{ item.variantSummary }}</p> }
                      <strong>{{ format(item.priceCents * item.quantity, item.currency) }}</strong>
                      <div class="cart-drawer-item-actions">
                        <div class="drawer-quantity" [attr.aria-label]="'Quantity for ' + item.name">
                          <button type="button" (click)="cart.decrease(item.productId, item.variantId)"
                            [disabled]="item.quantity === 1" [attr.aria-label]="'Decrease ' + item.name + ' quantity'">−</button>
                          <span>{{ item.quantity }}</span>
                          <button type="button" (click)="cart.increase(item.productId, item.variantId)"
                            [disabled]="item.quantity === 99" [attr.aria-label]="'Increase ' + item.name + ' quantity'">+</button>
                        </div>
                        <button type="button" class="drawer-remove" (click)="cart.remove(item.productId, item.variantId)">Remove</button>
                      </div>
                    </div>
                  </article>
                }
              </div>
            }
          </div>

          @if (cart.items().length > 0) {
            <footer class="cart-drawer-footer">
              <div><span>Estimated subtotal</span><strong>{{ format(cart.subtotalCents(), cart.items()[0].currency) }}</strong></div>
              <p>Final prices and availability are verified securely at checkout.</p>
              <a routerLink="/cart" class="cart-drawer-primary" (click)="closeCart(false)">Review shopping bag</a>
            </footer>
          }
        </aside>
      </div>
    }
  `
})
export class ShopNavigationComponent implements OnInit, OnDestroy {
  protected readonly cart = inject(CartService);
  protected readonly accounts = inject(AccountService);
  private readonly catalog = inject(ShopCatalogService);
  private readonly products = inject(ProductService);
  private readonly router = inject(Router);
  private readonly document = inject(DOCUMENT);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly configuredGroups = signal<PublicNavigationGroup[]>([]);
  private readonly fallbackCategories = signal<ShopCategory[]>([]);
  private readonly configurationLoaded = signal(false);
  private readonly categoriesLoaded = signal(false);
  protected readonly activeDesktopGroupId = signal<number | null>(null);
  protected readonly activeMobileGroupId = signal<number | null>(null);
  protected readonly menuOpen = signal(false);
  protected readonly cartDrawerOpen = signal(false);
  protected readonly searchQuery = signal('');
  protected readonly searchFocused = signal(false);
  protected readonly searchLoading = signal(false);
  protected readonly isHomepage = signal(false);
  protected readonly headerScrolled = signal(false);
  private readonly searchProductsLoaded = signal(false);
  private readonly searchableProducts = signal<ProductCard[]>([]);
  private routeSubscription?: Subscription;
  private focusBeforeMobileMenu: HTMLElement | null = null;
  private focusBeforeCart: HTMLElement | null = null;
  private groupOpenedByFocus: number | null = null;

  protected readonly navigationGroups = computed(() => {
    if (!this.configurationLoaded()) return [];
    const configured = this.configuredGroups().map(group => ({
      ...group,
      categories: group.categories
        .filter((category, index, categories) => categories.findIndex(candidate => candidate.id === category.id) === index),
      featuredProducts: group.featuredProducts
        .filter((product, index, products) => products.findIndex(candidate => candidate.id === product.id) === index)
        .slice(0, 3)
    })).sort((left, right) => left.displayOrder - right.displayOrder);
    if (configured.length) return configured;
    if (!this.categoriesLoaded()) return [];
    return this.fallbackCategories().filter(category => category.active && category.parentId === null)
      .sort((left, right) => left.sortOrder - right.sortOrder)
      .map((category, index): PublicNavigationGroup => ({
        id: -category.id,
        label: category.name,
        displayOrder: index,
        categories: [category],
        featuredProducts: []
      }));
  });
  protected readonly navigationLoading = computed(() =>
    !this.configurationLoaded() || (this.configuredGroups().length === 0 && !this.categoriesLoaded())
  );
  protected readonly activeDesktopGroup = computed(() => this.navigationGroups().find(group => group.id === this.activeDesktopGroupId()) ?? null);
  protected readonly activeMobileGroup = computed(() => this.navigationGroups().find(group => group.id === this.activeMobileGroupId()) ?? null);
  protected readonly searchPanelOpen = computed(() => this.searchFocused());
  private readonly searchableCategories = computed<HeaderSearchCategory[]>(() => {
    const categories = new Map<number, HeaderSearchCategory>();
    for (const group of this.navigationGroups()) {
      for (const category of group.categories) categories.set(category.id, category);
    }
    for (const category of this.fallbackCategories()) {
      if (category.active) categories.set(category.id, category);
    }
    return [...categories.values()];
  });
  protected readonly searchProductResults = computed(() => {
    const query = this.normalizedSearchQuery();
    const products = this.searchableProducts();
    if (!query) return products.slice(0, 4);
    return products
      .filter(product => `${product.name} ${product.categoryName}`.toLocaleLowerCase().includes(query))
      .sort((left, right) => Number(right.name.toLocaleLowerCase().startsWith(query)) - Number(left.name.toLocaleLowerCase().startsWith(query)))
      .slice(0, 4);
  });
  protected readonly searchCategoryResults = computed(() => {
    const query = this.normalizedSearchQuery();
    const categories = this.searchableCategories();
    if (!query) return categories.slice(0, 4);
    return categories
      .filter(category => category.name.toLocaleLowerCase().includes(query))
      .sort((left, right) => Number(right.name.toLocaleLowerCase().startsWith(query)) - Number(left.name.toLocaleLowerCase().startsWith(query)))
      .slice(0, 4);
  });

  ngOnInit(): void {
    this.updateRouteState(this.router.url);
    this.updateHeaderScrollState();
    this.accounts.restoreSession().subscribe();
    this.catalog.storefront().subscribe({
      next: configuration => { this.configuredGroups.set(configuration.navigationGroups ?? []); this.configurationLoaded.set(true); },
      error: () => { this.configuredGroups.set([]); this.configurationLoaded.set(true); }
    });
    this.catalog.categories().subscribe({
      next: categories => { this.fallbackCategories.set(categories); this.categoriesLoaded.set(true); },
      error: () => { this.fallbackCategories.set([]); this.categoriesLoaded.set(true); }
    });
    this.routeSubscription = this.router.events.pipe(filter(event => event instanceof NavigationEnd)).subscribe(event => {
      this.closeDestinations();
      this.updateRouteState(event.urlAfterRedirects);
      this.updateHeaderScrollState();
    });
  }

  ngOnDestroy(): void {
    this.routeSubscription?.unsubscribe();
    this.document.body.classList.remove('shop-menu-open', 'cart-drawer-open');
  }

  protected openDesktopMenu(group: PublicNavigationGroup): void {
    if (this.menuOpen() || this.cartDrawerOpen()) return;
    this.closeSearch();
    this.activeDesktopGroupId.set(group.id);
  }

  protected openDesktopMenuFromFocus(group: PublicNavigationGroup): void {
    this.openDesktopMenu(group);
    this.groupOpenedByFocus = group.id;
    setTimeout(() => {
      if (this.groupOpenedByFocus === group.id) this.groupOpenedByFocus = null;
    });
  }

  protected toggleDesktopMenu(group: PublicNavigationGroup): void {
    if (this.groupOpenedByFocus === group.id) {
      this.groupOpenedByFocus = null;
      return;
    }
    this.activeDesktopGroupId.set(this.activeDesktopGroupId() === group.id ? null : group.id);
  }

  protected closeDesktopMenu(restoreFocus: boolean): void {
    const groupId = this.activeDesktopGroupId();
    if (groupId == null) return;
    this.activeDesktopGroupId.set(null);
    if (restoreFocus) {
      setTimeout(() => this.host.nativeElement.querySelector<HTMLElement>(`[data-group-id="${groupId}"]`)?.focus());
    }
  }

  protected toggleMenu(): void { this.menuOpen() ? this.closeMenu() : this.openMenu(); }

  protected openMenu(): void {
    this.closeCart(false);
    this.closeDesktopMenu(false);
    this.closeSearch();
    this.focusBeforeMobileMenu = this.host.nativeElement.querySelector<HTMLElement>('.mobile-nav-toggle')
      ?? (this.document.activeElement instanceof HTMLElement ? this.document.activeElement : null);
    this.menuOpen.set(true);
    this.document.body.classList.add('shop-menu-open');
    queueMicrotask(() => this.host.nativeElement
      .querySelector<HTMLElement>('#mobile-shop-menu button, #mobile-shop-menu a')?.focus());
  }

  protected closeMenu(restoreFocus = true): void {
    if (!this.menuOpen()) return;
    this.menuOpen.set(false);
    this.activeMobileGroupId.set(null);
    this.unlockPage();
    if (restoreFocus) {
      const target = this.focusBeforeMobileMenu;
      queueMicrotask(() => target?.isConnected && target.focus());
    }
  }

  protected openMobileGroup(group: PublicNavigationGroup): void { this.activeMobileGroupId.set(group.id); }
  protected backToGroups(): void { this.activeMobileGroupId.set(null); }
  protected closeDestinations(): void { this.closeDesktopMenu(false); this.closeMenu(true); this.closeCart(false); this.closeSearch(); }
  protected format(cents: number, currency: string): string { return formatPrice(cents, currency); }

  protected openSearch(): void {
    this.closeDesktopMenu(false);
    this.searchFocused.set(true);
    this.ensureSearchProducts();
  }

  protected updateSearch(value: string): void {
    this.searchQuery.set(value);
    this.searchFocused.set(true);
    this.ensureSearchProducts();
  }

  protected clearSearch(input: HTMLInputElement): void {
    this.searchQuery.set('');
    this.searchFocused.set(true);
    queueMicrotask(() => input.focus());
  }

  protected closeSearch(): void {
    this.searchFocused.set(false);
  }

  protected selectSearchResult(): void {
    this.closeSearch();
  }

  protected submitSearch(event: Event): void {
    event.preventDefault();
    if (!this.searchQuery().trim()) return;
    const product = this.searchProductResults()[0];
    if (product) {
      this.selectSearchResult();
      void this.router.navigate(['/products', product.slug]);
      return;
    }
    const category = this.searchCategoryResults()[0];
    if (category) {
      this.selectSearchResult();
      void this.router.navigateByUrl(category.path);
    }
  }

  protected onSearchFocusOut(event: FocusEvent): void {
    if (event.relatedTarget instanceof Node && (event.currentTarget as HTMLElement).contains(event.relatedTarget)) return;
    this.closeSearch();
  }

  protected openCart(): void {
    if (this.document.defaultView?.matchMedia('(max-width: 760px)').matches) {
      void this.router.navigate(['/cart']);
      return;
    }
    this.closeDesktopMenu(false);
    this.closeMenu(false);
    this.closeSearch();
    this.focusBeforeCart = this.document.activeElement instanceof HTMLElement
      ? this.document.activeElement
      : this.host.nativeElement.querySelector<HTMLElement>('.bag-button');
    this.cartDrawerOpen.set(true);
    this.document.body.classList.add('cart-drawer-open');
    queueMicrotask(() => this.host.nativeElement.querySelector<HTMLElement>('.cart-drawer-close')?.focus());
  }

  protected closeCart(restoreFocus = true): void {
    if (!this.cartDrawerOpen()) return;
    this.cartDrawerOpen.set(false);
    this.document.body.classList.remove('cart-drawer-open');
    if (restoreFocus) {
      const target = this.focusBeforeCart;
      queueMicrotask(() => target?.isConnected && target.focus());
    }
    this.focusBeforeCart = null;
  }

  protected usePlaceholder(event: Event): void {
    const image = event.target as HTMLImageElement;
    if (!image.src.endsWith('/product-placeholder.svg')) image.src = '/product-placeholder.svg';
  }

  private unlockPage(): void { this.document.body.classList.remove('shop-menu-open'); }

  private normalizedSearchQuery(): string { return this.searchQuery().trim().toLocaleLowerCase(); }

  private updateRouteState(url: string): void {
    const path = url.split(/[?#]/, 1)[0].replace(/\/+$/, '') || '/';
    this.isHomepage.set(path === '/');
  }

  private updateHeaderScrollState(): void {
    const scrolled = this.isHomepage() && (this.document.defaultView?.scrollY ?? 0) > 56;
    if (this.headerScrolled() === scrolled) return;
    this.headerScrolled.set(scrolled);
    if (scrolled) {
      this.closeDesktopMenu(false);
      this.closeSearch();
    }
  }

  private ensureSearchProducts(): void {
    if (this.searchProductsLoaded() || this.searchLoading()) return;
    this.searchLoading.set(true);
    this.products.getProducts().subscribe({
      next: products => {
        this.searchableProducts.set(products);
        this.searchProductsLoaded.set(true);
        this.searchLoading.set(false);
      },
      error: () => {
        this.searchableProducts.set([]);
        this.searchProductsLoaded.set(true);
        this.searchLoading.set(false);
      }
    });
  }

  @HostListener('document:keydown.escape')
  protected onEscape(): void {
    if (this.cartDrawerOpen()) this.closeCart(true);
    else if (this.menuOpen()) this.closeMenu(true);
    else if (this.searchFocused()) this.closeSearch();
    else this.closeDesktopMenu(true);
  }

  @HostListener('document:click', ['$event'])
  protected onDocumentClick(event: MouseEvent): void {
    if (this.activeDesktopGroupId() != null && !this.host.nativeElement.contains(event.target as Node)) this.closeDesktopMenu(false);
  }

  @HostListener('focusout', ['$event'])
  protected onFocusOut(event: FocusEvent): void {
    if (this.activeDesktopGroupId() != null && event.relatedTarget instanceof Node && !this.host.nativeElement.contains(event.relatedTarget)) {
      this.closeDesktopMenu(false);
    }
  }

  @HostListener('window:scroll')
  protected onWindowScroll(): void {
    this.updateHeaderScrollState();
  }
}
