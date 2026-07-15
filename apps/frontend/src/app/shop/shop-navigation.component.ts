import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { MatBadgeModule } from '@angular/material/badge';
import { MatButtonModule } from '@angular/material/button';
import { MatDividerModule } from '@angular/material/divider';
import { MatMenuModule } from '@angular/material/menu';
import { RouterLink } from '@angular/router';
import { CartService } from '../cart/cart.service';
import { formatPrice } from './price.util';
import { ShopCatalogService, ShopCategory } from './shop-catalog.service';

@Component({
  selector: 'app-shop-navigation',
  imports: [RouterLink, MatBadgeModule, MatButtonModule, MatDividerModule, MatMenuModule],
  template: `
    <header class="site-header">
      <div class="utility-bar">
        <p>Complimentary delivery on orders over €75</p>
        <nav aria-label="Utility navigation">
          <a href="mailto:hello@builtbygrain.example">Customer care</a><a href="#reviews">Reviews</a>
          <button type="button" [matMenuTriggerFor]="languageMenu">EN <span aria-hidden="true">⌄</span></button>
        </nav>
      </div>
      <div class="main-nav">
        <a routerLink="/" class="shop-brand" aria-label="Built by Grain home"><span class="brand-mark">BG</span><span>Built by Grain<small>Objects for considered homes</small></span></a>
        <nav class="category-nav" aria-label="Product categories">
          <a routerLink="/">All products</a>
          @for (category of rootCategories(); track category.id) {
            <a routerLink="/" [queryParams]="{category: category.slug}">{{ category.name }}</a>
          }
        </nav>
        <div class="nav-actions">
          <button type="button" class="icon-button search-control" aria-label="Search" title="Search">⌕</button>
          <button type="button" class="icon-button" aria-label="Open shopping bag" [matMenuTriggerFor]="cartMenu" [matBadge]="cart.itemCount()" [matBadgeHidden]="cart.itemCount() === 0">▢</button>
          <button type="button" class="icon-button" aria-label="Open account menu" [matMenuTriggerFor]="accountMenu">○</button>
        </div>
      </div>
    </header>

    <mat-menu #languageMenu="matMenu"><button mat-menu-item>English</button><button mat-menu-item>Deutsch</button></mat-menu>
    <mat-menu #accountMenu="matMenu" class="account-menu"><div class="menu-heading" (click)="$event.stopPropagation()"><strong>Your account</strong><span>Sign in to view orders and saved pieces.</span></div><mat-divider/><a mat-menu-item routerLink="/">Log in</a><a mat-menu-item routerLink="/">Create account</a></mat-menu>
    <mat-menu #cartMenu="matMenu" class="cart-menu">
      <div class="mini-cart" (click)="$event.stopPropagation()">
        <div class="mini-cart-heading"><strong>Your bag</strong><span>{{ cart.itemCount() }} {{ cart.itemCount() === 1 ? 'item' : 'items' }}</span></div>
        <mat-divider/>
        @if (cart.items().length === 0) { <p class="empty-cart">Your bag is waiting for something beautiful.</p> }
        @for (item of cart.items(); track item.productId + item.variantId) {
          <div class="mini-cart-item"><img [src]="item.imageUrl || '/product-placeholder.svg'" alt=""><div><strong>{{ item.name }}</strong><span>{{ item.variantSummary || 'Standard' }} · Qty {{ item.quantity }}</span><b>{{ format(item.priceCents * item.quantity, item.currency) }}</b></div><button type="button" (click)="cart.remove(item.productId, item.variantId)" [attr.aria-label]="'Remove ' + item.name">×</button></div>
        }
        @if (cart.items().length) { <mat-divider/><div class="mini-cart-total"><span>Subtotal</span><strong>{{ format(cart.subtotalCents(), cart.items()[0].currency) }}</strong></div><a routerLink="/cart" class="mini-cart-primary">View bag</a><a routerLink="/cart" class="mini-cart-secondary">Continue to checkout</a> }
      </div>
    </mat-menu>
  `
})
export class ShopNavigationComponent implements OnInit {
  protected readonly cart = inject(CartService);
  private readonly catalog = inject(ShopCatalogService);
  protected readonly categories = signal<ShopCategory[]>([]);
  protected readonly rootCategories = computed(() => this.categories().filter(category => category.parentId === null));
  ngOnInit(): void { this.catalog.categories().subscribe({ next: categories => this.categories.set(categories), error: () => this.categories.set([]) }); }
  protected format(cents: number, currency: string): string { return formatPrice(cents, currency); }
}
