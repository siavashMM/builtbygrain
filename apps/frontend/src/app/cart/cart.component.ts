import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CartItem, CartService } from './cart.service';
import { formatPrice } from '../shop/price.util';
import { ShopNavigationComponent } from '../shop/shop-navigation.component';

@Component({
  selector: 'app-cart',
  imports: [RouterLink, ShopNavigationComponent],
  templateUrl: './cart.component.html'
})
export class CartComponent {
  protected readonly cart = inject(CartService);

  protected formatItemPrice(item: CartItem, total = false): string {
    return formatPrice(item.priceCents * (total ? item.quantity : 1), item.currency);
  }

  protected subtotal(): string {
    return formatPrice(this.cart.subtotalCents(), this.cart.items()[0]?.currency ?? 'EUR');
  }
}
