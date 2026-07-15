import { DOCUMENT } from '@angular/common';
import { Injectable, inject, signal } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class WishlistService {
  private readonly storage = inject(DOCUMENT).defaultView?.localStorage;
  private readonly ids = signal<number[]>(this.restore());
  readonly productIds = this.ids.asReadonly();

  has(productId: number): boolean { return this.ids().includes(productId); }
  toggle(productId: number): boolean {
    const active = !this.has(productId);
    this.ids.set(active ? [...this.ids(), productId] : this.ids().filter((id) => id !== productId));
    try { this.storage?.setItem('builtbygrain.guest-wishlist', JSON.stringify(this.ids())); } catch { /* memory fallback */ }
    return active;
  }
  private restore(): number[] {
    try { const value: unknown = JSON.parse(this.storage?.getItem('builtbygrain.guest-wishlist') ?? '[]'); return Array.isArray(value) ? value.filter(Number.isInteger) : []; }
    catch { return []; }
  }
}
