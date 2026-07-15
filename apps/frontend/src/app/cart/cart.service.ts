import { DOCUMENT } from '@angular/common';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Product } from '../product.service';
import { ProductVariant } from '../product.service';

export interface CartItem {
  productId: number;
  slug: string;
  name: string;
  imageUrl: string | null;
  priceCents: number;
  currency: string;
  quantity: number;
  variantId: string;
  variantSummary?: string;
}

const STORAGE_KEY = 'builtbygrain.guest-cart';
const MAX_QUANTITY = 99;

@Injectable({ providedIn: 'root' })
export class CartService {
  private readonly document = inject(DOCUMENT);
  private readonly storage = this.document.defaultView?.localStorage;
  private readonly cartItems = signal<CartItem[]>(this.restore());

  readonly items = this.cartItems.asReadonly();
  readonly itemCount = computed(() => this.cartItems().reduce((count, item) => count + item.quantity, 0));
  readonly subtotalCents = computed(() =>
    this.cartItems().reduce((subtotal, item) => subtotal + item.priceCents * item.quantity, 0)
  );

  addProduct(product: Product, quantity = 1, variant?: ProductVariant, variantSummary?: string): boolean {
    const selected = variant ?? product.configuration?.variants?.[0];
    if (!product.active || !product.inStock || !selected?.available || !this.isValidQuantity(quantity) ||
        (selected.stockQuantity != null && !selected.backorderAllowed && !selected.preorderAllowed && quantity > selected.stockQuantity)) {
      return false;
    }

    const existing = this.cartItems().find((item) => item.productId === product.id && item.variantId === selected.id);
    const nextQuantity = (existing?.quantity ?? 0) + quantity;
    if (!this.isValidQuantity(nextQuantity)) {
      return false;
    }

    const item: CartItem = {
      productId: product.id,
      slug: product.slug,
      name: product.name,
      imageUrl: selected.imageUrls?.[0] ?? product.imageUrl ?? product.imageUrls[0] ?? null,
      priceCents: selected.salePriceCents ?? selected.priceCents,
      currency: product.currency,
      quantity: nextQuantity,
      variantId: selected.id,
      variantSummary
    };
    this.commit(existing
      ? this.cartItems().map((current) => current.productId === product.id && current.variantId === selected.id ? item : current)
      : [...this.cartItems(), item]);
    return true;
  }

  setQuantity(productId: number, quantity: number, variantId?: string): boolean {
    if (!this.isValidQuantity(quantity) || !this.cartItems().some((item) => item.productId === productId && (!variantId || item.variantId === variantId))) {
      return false;
    }
    this.commit(this.cartItems().map((item) => item.productId === productId && (!variantId || item.variantId === variantId) ? { ...item, quantity } : item));
    return true;
  }

  increase(productId: number, variantId?: string): boolean {
    const item = this.cartItems().find((candidate) => candidate.productId === productId && (!variantId || candidate.variantId === variantId));
    return item ? this.setQuantity(productId, item.quantity + 1, variantId) : false;
  }

  decrease(productId: number, variantId?: string): boolean {
    const item = this.cartItems().find((candidate) => candidate.productId === productId && (!variantId || candidate.variantId === variantId));
    return item ? this.setQuantity(productId, item.quantity - 1, variantId) : false;
  }

  remove(productId: number, variantId?: string): void {
    this.commit(this.cartItems().filter((item) => item.productId !== productId || (!!variantId && item.variantId !== variantId)));
  }

  clear(): void {
    this.commit([]);
  }

  private isValidQuantity(quantity: number): boolean {
    return Number.isInteger(quantity) && quantity >= 1 && quantity <= MAX_QUANTITY;
  }

  private commit(items: CartItem[]): void {
    this.cartItems.set(items);
    try {
      this.storage?.setItem(STORAGE_KEY, JSON.stringify(items));
    } catch {
      // The in-memory cart remains usable when storage is unavailable.
    }
  }

  private restore(): CartItem[] {
    try {
      const parsed: unknown = JSON.parse(this.storage?.getItem(STORAGE_KEY) ?? '[]');
      if (!Array.isArray(parsed)) return [];
      return parsed.filter((item): item is CartItem => this.isStoredItem(item));
    } catch {
      return [];
    }
  }

  private isStoredItem(item: unknown): item is CartItem {
    if (!item || typeof item !== 'object') return false;
    const candidate = item as Partial<CartItem>;
    return typeof candidate.productId === 'number'
      && typeof candidate.slug === 'string'
      && typeof candidate.name === 'string'
      && (typeof candidate.imageUrl === 'string' || candidate.imageUrl === null)
      && typeof candidate.priceCents === 'number'
      && candidate.priceCents >= 0
      && typeof candidate.currency === 'string'
      && typeof candidate.quantity === 'number'
      && typeof candidate.variantId === 'string'
      && this.isValidQuantity(candidate.quantity);
  }
}
