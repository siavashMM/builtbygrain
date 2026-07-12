import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CartService } from '../cart/cart.service';

@Component({
  selector: 'app-shop-navigation',
  imports: [RouterLink],
  template: `
    <nav class="shop-navigation" aria-label="Shop navigation">
      <a routerLink="/" class="shop-brand">Built by Grain</a>
      <a routerLink="/cart" class="bag-link" aria-label="Shopping bag with {{ cart.itemCount() }} items">
        Bag <span class="bag-count">{{ cart.itemCount() }}</span>
      </a>
    </nav>
  `
})
export class ShopNavigationComponent {
  protected readonly cart = inject(CartService);
}
