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
}

@Injectable({
  providedIn: 'root'
})
export class ProductService {
  private readonly http = inject(HttpClient);

  getProducts(): Observable<Product[]> {
    return this.http.get<Product[]>('/api/public/products');
  }

  getProduct(slug: string): Observable<Product> {
    return this.http.get<Product>(`/api/public/products/${encodeURIComponent(slug)}`);
  }

  getAdminProducts(): Observable<Product[]> {
    return this.http.get<Product[]>('/api/admin/products');
  }

  createProduct(request: ProductRequest): Observable<Product> {
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
