import { A11yModule } from '@angular/cdk/a11y';
import { DOCUMENT } from '@angular/common';
import { Component, ElementRef, HostListener, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { MatBadgeModule } from '@angular/material/badge';
import { MatDividerModule } from '@angular/material/divider';
import { MatMenuModule } from '@angular/material/menu';
import { NavigationEnd, Router, RouterLink } from '@angular/router';
import { Subscription, filter } from 'rxjs';

import { CartService } from '../cart/cart.service';
import { ProductCard } from '../product.service';
import { formatPrice } from './price.util';
import { PublicNavigationGroup, ShopCatalogService, ShopCategory } from './shop-catalog.service';

@Component({
  selector: 'app-shop-navigation',
  imports: [RouterLink, MatBadgeModule, MatDividerModule, MatMenuModule, A11yModule],
  template: `
    <header class="site-header" (mouseleave)="closeDesktopMenu(false)">
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
          [attr.aria-expanded]="menuOpen()" aria-controls="mobile-shop-menu" aria-label="Open shop menu">
          <span aria-hidden="true">{{ menuOpen() ? '×' : '☰' }}</span>
        </button>

        <a routerLink="/" class="shop-brand" aria-label="Built by Grain home" (click)="closeDestinations()">
          <span class="brand-mark" aria-hidden="true"><img src="/builtbygrain-mark.png" alt=""></span>
          <span>Built by Grain<small>Handcrafted wooden goods</small></span>
        </a>

        <nav class="category-nav navigation-group-nav" aria-label="Shop navigation groups" [attr.aria-busy]="navigationLoading()">
          @for (group of navigationGroups(); track group.id) {
            <button type="button" class="navigation-group-trigger" [class.active]="activeDesktopGroupId() === group.id"
              [attr.data-group-id]="group.id" aria-haspopup="true"
              [attr.aria-expanded]="activeDesktopGroupId() === group.id"
              [attr.aria-controls]="'mega-menu-' + group.id"
              (mouseenter)="openDesktopMenu(group)" (focus)="openDesktopMenuFromFocus(group)" (click)="toggleDesktopMenu(group)">
              {{ group.label }} <span aria-hidden="true">⌄</span>
            </button>
          }
        </nav>

        <div class="nav-actions">
          <a class="icon-button search-control" routerLink="/" fragment="featured-products" aria-label="Search products" title="Search products" (click)="closeDestinations()">
            <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="11" cy="11" r="6.5"/><path d="m16 16 4 4"/></svg>
          </a>
          <button type="button" class="icon-button bag-button" aria-label="Open shopping bag" [matMenuTriggerFor]="cartMenu"
            [matBadge]="cart.itemCount()" [matBadgeHidden]="cart.itemCount() === 0">
            <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M5 8.5h14l-1 12H6l-1-12Z"/><path d="M9 9V6a3 3 0 0 1 6 0v3"/></svg>
          </button>
        </div>
      </div>

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
      <nav id="mobile-shop-menu" class="mobile-shop-menu" aria-label="Mobile shop navigation" cdkTrapFocus [cdkTrapFocusAutoCapture]="true">
        <div class="mobile-menu-heading">
          @if (activeMobileGroup()) {
            <button type="button" class="mobile-menu-back" (click)="backToGroups()">← Back</button>
          } @else {
            <span class="brand-mark" aria-hidden="true"><img src="/builtbygrain-mark.png" alt=""></span>
          }
          <button type="button" class="icon-button" (click)="closeMenu()" aria-label="Close shop menu">×</button>
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
            <a href="mailto:hello@builtbygrain.example">Customer care</a>
            <a routerLink="/" fragment="craftsmanship" (click)="closeDestinations()">Our craft</a>
          </div>
          <a class="mobile-menu-cta" routerLink="/" fragment="featured-products" (click)="closeDestinations()">View all products</a>
        }
      </nav>
    }

    <mat-menu #cartMenu="matMenu" class="cart-menu">
      <div class="mini-cart" (click)="$event.stopPropagation()">
        <div class="mini-cart-heading"><strong>Your bag</strong><span>{{ cart.itemCount() }} {{ cart.itemCount() === 1 ? 'item' : 'items' }}</span></div>
        <mat-divider/>
        @if (cart.items().length === 0) { <p class="empty-cart">Your bag is waiting for something beautiful.</p> }
        @for (item of cart.items(); track item.productId + item.variantId) {
          <div class="mini-cart-item"><img [src]="item.imageUrl || '/product-placeholder.svg'" alt="" (error)="usePlaceholder($event)"><div><strong>{{ item.name }}</strong><span>{{ item.variantSummary || 'Standard' }} · Qty {{ item.quantity }}</span><b>{{ format(item.priceCents * item.quantity, item.currency) }}</b></div><button type="button" (click)="cart.remove(item.productId, item.variantId)" [attr.aria-label]="'Remove ' + item.name">×</button></div>
        }
        @if (cart.items().length) { <mat-divider/><div class="mini-cart-total"><span>Subtotal</span><strong>{{ format(cart.subtotalCents(), cart.items()[0].currency) }}</strong></div><a routerLink="/cart" class="mini-cart-primary">View bag</a><a routerLink="/cart" class="mini-cart-secondary">Continue to checkout</a> }
      </div>
    </mat-menu>
  `
})
export class ShopNavigationComponent implements OnInit, OnDestroy {
  protected readonly cart = inject(CartService);
  private readonly catalog = inject(ShopCatalogService);
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
  private routeSubscription?: Subscription;
  private focusBeforeMobileMenu: HTMLElement | null = null;
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

  ngOnInit(): void {
    this.catalog.storefront().subscribe({
      next: configuration => { this.configuredGroups.set(configuration.navigationGroups ?? []); this.configurationLoaded.set(true); },
      error: () => { this.configuredGroups.set([]); this.configurationLoaded.set(true); }
    });
    this.catalog.categories().subscribe({
      next: categories => { this.fallbackCategories.set(categories); this.categoriesLoaded.set(true); },
      error: () => { this.fallbackCategories.set([]); this.categoriesLoaded.set(true); }
    });
    this.routeSubscription = this.router.events.pipe(filter(event => event instanceof NavigationEnd)).subscribe(() => this.closeDestinations());
  }

  ngOnDestroy(): void {
    this.routeSubscription?.unsubscribe();
    this.unlockPage();
  }

  protected openDesktopMenu(group: PublicNavigationGroup): void {
    if (this.menuOpen()) return;
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
    this.closeDesktopMenu(false);
    this.focusBeforeMobileMenu = this.document.activeElement instanceof HTMLElement ? this.document.activeElement : null;
    this.menuOpen.set(true);
    this.document.body.classList.add('shop-menu-open');
  }

  protected closeMenu(restoreFocus = true): void {
    if (!this.menuOpen()) return;
    this.menuOpen.set(false);
    this.activeMobileGroupId.set(null);
    this.unlockPage();
    if (restoreFocus) {
      const target = this.focusBeforeMobileMenu;
      setTimeout(() => target?.isConnected && target.focus());
    }
  }

  protected openMobileGroup(group: PublicNavigationGroup): void { this.activeMobileGroupId.set(group.id); }
  protected backToGroups(): void { this.activeMobileGroupId.set(null); }
  protected closeDestinations(): void { this.closeDesktopMenu(false); this.closeMenu(true); }
  protected format(cents: number, currency: string): string { return formatPrice(cents, currency); }

  protected usePlaceholder(event: Event): void {
    const image = event.target as HTMLImageElement;
    if (!image.src.endsWith('/product-placeholder.svg')) image.src = '/product-placeholder.svg';
  }

  private unlockPage(): void { this.document.body.classList.remove('shop-menu-open'); }

  @HostListener('document:keydown.escape')
  protected onEscape(): void {
    if (this.menuOpen()) this.closeMenu(true);
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
}
