import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export interface Product {
  id: number;
  name: string;
  slug: string;
  description: string | null;
  priceCents: number;
  currency: string;
  imageUrl: string | null;
  imageUrls: string[];
  inStock: boolean;
  sizes: string[];
  active: boolean;
  configuration: ProductConfiguration;
  categoryId: number;
  categoryName: string;
  categorySlug: string;
}

export interface ProductCardColorSwatch {
  id: number;
  label: string;
  swatchHex?: string | null;
  swatchImageUrl?: string | null;
  primaryImageUrl: string;
  hoverImageUrl: string;
}

export interface ProductCard {
  id: number;
  name: string;
  slug: string;
  currency: string;
  fromPriceCents: number;
  primaryImageUrl: string;
  hoverImageUrl: string;
  categoryId: number;
  categoryName: string;
  categorySlug: string;
  colorSwatches: ProductCardColorSwatch[];
}

export type ProductOptionDisplayType = 'BUTTON' | 'COLOR_SWATCH' | 'DROPDOWN';
export type StockStatus = 'IN_STOCK' | 'LOW_STOCK' | 'OUT_OF_STOCK' | 'BACKORDER' | 'PREORDER' | 'DISCONTINUED';
export interface ProductOptionValue { id: string; label: string; swatchColor?: string; imageUrl?: string; additionalInformation?: string; }
export interface ProductOption { id: string; name: string; displayType: ProductOptionDisplayType; values: ProductOptionValue[]; }
export interface ProductVariant { id: string; sku?: string; optionValueIds: string[]; priceCents: number; salePriceCents?: number; stockQuantity?: number; stockStatus: StockStatus; available: boolean; backorderAllowed: boolean; preorderAllowed: boolean; imageUrls: string[]; deliveryEstimate?: string; }
export interface ProductConfiguration {
  subtitle?: string; badge?: string; salePriceCents?: number; unitPriceLabel?: string; deliveryEstimate?: string;
  benefits: string[]; specifications: Record<string, string>; sections: { id: string; heading: string; content: string }[];
  faqs: { question: string; answer: string }[]; options: ProductOption[]; variants: ProductVariant[];
  rating?: { average: number; count: number };
  sizeAffectsImages?: boolean;
}

export interface ProductRequest {
  name: string;
  slug: string;
  description: string | null;
  priceCents: number;
  currency: string;
  inStock: boolean;
  sizes: string[];
  active: boolean;
  configuration?: ProductConfiguration;
  categoryId?: number | null;
}

@Injectable({
  providedIn: 'root'
})
export class ProductService {
  private readonly http = inject(HttpClient);

  getProducts(): Observable<ProductCard[]> {
    return this.http.get<ProductCard[]>('/api/public/products');
  }

  getProduct(slug: string, variantId?: string | null): Observable<Product> {
    const query = variantId ? `?variant=${encodeURIComponent(variantId)}` : '';
    return this.http.get<Product>(`/api/public/products/${encodeURIComponent(slug)}${query}`);
  }

  getAdminProducts(): Observable<Product[]> {
    return this.http.get<Product[]>('/api/admin/products');
  }

  createProduct(request: ProductRequest): Observable<Product> {
    if (request.categoryId != null) {
      return this.http.post<Product>(`/api/admin/categories/${request.categoryId}/products`, request);
    }
    return this.http.post<Product>('/api/admin/products', request);
  }

  createProductWithImages(request: ProductRequest, images: File[]): Observable<Product> {
    const formData = new FormData();
    formData.append('product', new Blob([JSON.stringify(request)], { type: 'application/json' }));
    images.forEach((image) => formData.append('images', image));
    return this.http.post<Product>('/api/admin/products/with-images', formData);
  }

  updateProduct(id: number, request: ProductRequest): Observable<Product> {
    return this.http.put<Product>(`/api/admin/products/${id}`, request);
  }
  moveProduct(id:number,categoryId:number):Observable<Product>{return this.http.patch<Product>(`/api/admin/products/${id}/move`,{categoryId});}
  duplicateProduct(id:number):Observable<Product>{return this.http.post<Product>(`/api/admin/products/${id}/duplicate`,{});}

  activateProduct(id: number): Observable<Product> {
    return this.http.patch<Product>(`/api/admin/products/${id}/activate`, null);
  }

  deactivateProduct(id: number): Observable<Product> {
    return this.http.patch<Product>(`/api/admin/products/${id}/deactivate`, null);
  }

  deleteProduct(id: number): Observable<void> {
    return this.http.delete<void>(`/api/admin/products/${id}`);
  }

  uploadProductImages(id: number, images: File[]): Observable<Product> {
    const formData = new FormData();
    images.forEach((image) => formData.append('images', image));
    return this.http.post<Product>(`/api/admin/products/${id}/images`, formData);
  }

  removeProductImage(id: number, imageIndex: number): Observable<Product> {
    return this.http.delete<Product>(`/api/admin/products/${id}/images/${imageIndex}`);
  }
}
