import { Component, EventEmitter, Input, Output } from '@angular/core';
import { Product } from '../product.service';

type AdminProductsState = 'loading' | 'ready' | 'error';

@Component({
  selector: 'app-admin-product-list',
  imports: [],
  templateUrl: './admin-product-list.component.html'
})
export class AdminProductListComponent {
  @Input({ required: true }) products: Product[] = [];
  @Input({ required: true }) state: AdminProductsState = 'loading';
  @Output() editProduct = new EventEmitter<Product>();
  @Output() deleteProduct = new EventEmitter<Product>();

  protected formatPrice(product: Product): string {
    return new Intl.NumberFormat('de-DE', {
      style: 'currency',
      currency: product.currency
    }).format(product.priceCents / 100);
  }
}
