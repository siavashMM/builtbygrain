import { TestBed } from '@angular/core/testing';
import { Product } from '../product.service';
import { CartService } from './cart.service';

describe('CartService', () => {
  const product: Product = {
    categoryId: 1,
    categoryName: 'Uncategorized',
    categorySlug: 'uncategorized',
    id: 7,
    name: 'Oak Board',
    slug: 'oak-board',
    description: 'Handmade board',
    priceCents: 4900,
    currency: 'EUR',
    imageUrl: '/oak.jpg',
    imageUrls: ['/oak.jpg'],
    inStock: true,
    sizes: [],
    active: true,
    configuration: {
      benefits: [], specifications: {}, sections: [], faqs: [], options: [],
      variants: [{ id: 'default-7', optionValueIds: [], priceCents: 4900, stockStatus: 'IN_STOCK', available: true, backorderAllowed: false, preorderAllowed: false, imageUrls: [] }]
    }
  };

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({});
  });

  it('adds products and calculates item count and display subtotal', () => {
    const service = TestBed.inject(CartService);

    expect(service.addProduct(product, 2)).toBeTrue();
    expect(service.itemCount()).toBe(2);
    expect(service.subtotalCents()).toBe(9800);
    expect(service.items()[0].productId).toBe(product.id);
  });

  it('rejects inactive, unavailable, and invalid quantities', () => {
    const service = TestBed.inject(CartService);

    expect(service.addProduct({ ...product, active: false })).toBeFalse();
    expect(service.addProduct({ ...product, inStock: false })).toBeFalse();
    expect(service.addProduct(product, 0)).toBeFalse();
    expect(service.addProduct(product, 1.5)).toBeFalse();
    expect(service.items()).toEqual([]);
  });

  it('updates, removes, and clears items while enforcing quantity limits', () => {
    const service = TestBed.inject(CartService);
    service.addProduct(product);

    expect(service.increase(product.id)).toBeTrue();
    expect(service.items()[0].quantity).toBe(2);
    expect(service.decrease(product.id)).toBeTrue();
    expect(service.decrease(product.id)).toBeFalse();
    expect(service.setQuantity(product.id, 100)).toBeFalse();

    service.remove(product.id);
    expect(service.items()).toEqual([]);
    service.addProduct(product);
    service.clear();
    expect(service.items()).toEqual([]);
  });

  it('restores a valid guest cart after the service is recreated', () => {
    TestBed.inject(CartService).addProduct(product, 3);
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({});

    const restored = TestBed.inject(CartService);
    expect(restored.items()[0].quantity).toBe(3);
    expect(restored.subtotalCents()).toBe(14700);
  });

  it('ignores malformed persisted cart data', () => {
    localStorage.setItem('builtbygrain.guest-cart', JSON.stringify([{ productId: 1, quantity: -4 }]));

    expect(TestBed.inject(CartService).items()).toEqual([]);
  });
});
