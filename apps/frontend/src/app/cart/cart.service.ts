import { DOCUMENT } from '@angular/common';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Product } from '../product.service';

export interface CartItem {
  productId: number;
  slug: string;
  name: string;
  imageUrl: string | null;
  priceCents: number;
  currency: string;
  quantity: number;
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

  addProduct(product: Product, quantity = 1): boolean {
    if (!product.active || !product.inStock || !this.isValidQuantity(quantity)) {
      return false;
    }

    const existing = this.cartItems().find((item) => item.productId === product.id);
    const nextQuantity = (existing?.quantity ?? 0) + quantity;
    if (!this.isValidQuantity(nextQuantity)) {
      return false;
    }

    const item: CartItem = {
      productId: product.id,
      slug: product.slug,
      name: product.name,
      imageUrl: product.imageUrl ?? product.imageUrls[0] ?? null,
      priceCents: product.priceCents,
      currency: product.currency,
      quantity: nextQuantity
    };
    this.commit(existing
      ? this.cartItems().map((current) => current.productId === product.id ? item : current)
      : [...this.cartItems(), item]);
    return true;
  }

  setQuantity(productId: number, quantity: number): boolean {
    if (!this.isValidQuantity(quantity) || !this.cartItems().some((item) => item.productId === productId)) {
      return false;
    }
    this.commit(this.cartItems().map((item) => item.productId === productId ? { ...item, quantity } : item));
    return true;
  }

  increase(productId: number): boolean {
    const item = this.cartItems().find((candidate) => candidate.productId === productId);
    return item ? this.setQuantity(productId, item.quantity + 1) : false;
  }

  decrease(productId: number): boolean {
    const item = this.cartItems().find((candidate) => candidate.productId === productId);
    return item ? this.setQuantity(productId, item.quantity - 1) : false;
  }

  remove(productId: number): void {
    this.commit(this.cartItems().filter((item) => item.productId !== productId));
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
      && this.isValidQuantity(candidate.quantity);
  }
}
