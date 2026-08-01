import { Component, ViewEncapsulation, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CartItem, CartService } from './cart.service';
import { formatPrice } from '../shop/price.util';
import { ShopNavigationComponent } from '../shop/shop-navigation.component';

@Component({
  selector: 'app-cart',
  imports: [RouterLink, ShopNavigationComponent],
  templateUrl: './cart.component.html',
  styleUrl: './cart.component.css',
  encapsulation: ViewEncapsulation.None
})
export class CartComponent {
  protected readonly cart = inject(CartService);

  protected formatItemPrice(item: CartItem, total = false): string {
    return formatPrice(item.priceCents * (total ? item.quantity : 1), item.currency);
  }

  protected subtotal(): string {
    return formatPrice(this.cart.subtotalCents(), this.cart.items()[0]?.currency ?? 'EUR');
  }

  protected usePlaceholder(event: Event): void {
    const image = event.target as HTMLImageElement;
    if (!image.src.endsWith('/product-placeholder.svg')) image.src = '/product-placeholder.svg';
  }
}
