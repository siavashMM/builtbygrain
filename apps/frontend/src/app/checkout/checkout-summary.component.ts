import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CartService } from '../cart/cart.service';
import { formatPrice } from '../shop/price.util';

@Component({
  selector: 'app-checkout-summary',
  imports: [RouterLink],
  template: `
    <aside class="checkout-order-summary" aria-labelledby="checkout-summary-title">
      <header>
        <div><p class="checkout-eyebrow">Your order</p><h2 id="checkout-summary-title">Summary</h2></div>
        <a routerLink="/cart">Edit bag</a>
      </header>
      <div class="checkout-summary-items">
        @for (item of cart.items(); track item.productId + '-' + item.variantId) {
          <article>
            <span class="summary-image"><img [src]="item.imageUrl || '/product-placeholder.svg'" alt=""></span>
            <div><strong>{{ item.name }}</strong>
              @if (item.variantSummary) { <small>{{ item.variantSummary }}</small> }
              <small>Quantity {{ item.quantity }}</small>
            </div>
            <b>{{ format(item.priceCents * item.quantity, item.currency) }}</b>
          </article>
        }
      </div>
      <dl>
        <div><dt>Subtotal</dt><dd>{{ subtotal() }}</dd></div>
        <div><dt>Delivery</dt><dd>Calculated next</dd></div>
        <div class="checkout-summary-total"><dt>Estimated total</dt><dd>{{ subtotal() }}</dd></div>
      </dl>
      <p class="summary-delivery-note">Made-to-order pieces usually leave our workshop in 4–6 weeks.</p>
    </aside>
  `
})
export class CheckoutSummaryComponent {
  protected readonly cart = inject(CartService);
  protected format = formatPrice;
  protected subtotal(): string {
    return formatPrice(this.cart.subtotalCents(), this.cart.items()[0]?.currency ?? 'EUR');
  }
}
