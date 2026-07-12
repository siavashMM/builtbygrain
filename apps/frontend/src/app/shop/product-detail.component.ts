import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { CartService } from '../cart/cart.service';
import { Product, ProductService } from '../product.service';
import { formatPrice } from './price.util';
import { ShopNavigationComponent } from './shop-navigation.component';

type DetailState = 'loading' | 'ready' | 'not-found' | 'error';

@Component({
  selector: 'app-product-detail',
  imports: [RouterLink, ShopNavigationComponent],
  templateUrl: './product-detail.component.html'
})
export class ProductDetailComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly productService = inject(ProductService);
  private readonly cart = inject(CartService);

  protected readonly state = signal<DetailState>('loading');
  protected readonly product = signal<Product | null>(null);
  protected readonly quantity = signal(1);
  protected readonly selectedImageIndex = signal(0);
  protected readonly addedMessage = signal('');

  ngOnInit(): void {
    this.loadProduct();
  }

  protected loadProduct(): void {
    const slug = this.route.snapshot.paramMap.get('slug');
    if (!slug) {
      this.state.set('not-found');
      return;
    }
    this.state.set('loading');
    this.productService.getProduct(slug).subscribe({
      next: (product) => {
        this.product.set(product);
        this.state.set('ready');
      },
      error: (error: HttpErrorResponse) => {
        this.product.set(null);
        this.state.set(error.status === 404 ? 'not-found' : 'error');
      }
    });
  }

  protected images(product: Product): string[] {
    return product.imageUrls.length > 0 ? product.imageUrls : ['/product-placeholder.svg'];
  }

  protected selectImage(index: number): void {
    this.selectedImageIndex.set(index);
  }

  protected changeQuantity(delta: number): void {
    this.quantity.update((current) => Math.min(99, Math.max(1, current + delta)));
    this.addedMessage.set('');
  }

  protected addToBag(product: Product): void {
    this.addedMessage.set(this.cart.addProduct(product, this.quantity()) ? 'Added to your bag.' : 'Unable to add this quantity.');
  }

  protected formatPrice(product: Product): string {
    return formatPrice(product.priceCents, product.currency);
  }
}
