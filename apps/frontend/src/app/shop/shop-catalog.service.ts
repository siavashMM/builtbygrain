import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ProductCard } from '../product.service';
import { CatalogRequestCache } from '../catalog-request-cache.service';

export interface ShopCategory {
  id: number;
  parentId: number | null;
  name: string;
  slug: string;
  description: string | null;
  imageUrl: string | null;
  path: string;
  sortOrder: number;
  active: boolean;
}
export interface NavigationCategory { id:number; name:string; slug:string; path:string; children:NavigationCategory[]; }
export interface ShopCategoryPage { category: ShopCategory; breadcrumbs: ShopCategory[]; products: ProductCard[]; }
export interface PublicStorefrontSettings {
  heroImageUrl: string | null;
  heroImageAltText: string | null;
  heroHeading: string | null;
  heroSupportingText: string | null;
  updatedAt: string;
}
export interface PublicNavigationGroup {
  id: number;
  label: string;
  displayOrder: number;
  categories: PublicNavigationCategory[];
  featuredProducts: ProductCard[];
}
export interface PublicNavigationCategory { id: number; name: string; slug: string; description: string | null; path: string; }
export interface PublicStorefrontConfiguration {
  settings: PublicStorefrontSettings;
  navigationGroups: PublicNavigationGroup[];
}

@Injectable({ providedIn: 'root' })
export class ShopCatalogService {
  private readonly http = inject(HttpClient);
  private readonly cache = inject(CatalogRequestCache);
  categories(): Observable<ShopCategory[]> {
    return this.cache.get('categories', () => this.http.get<ShopCategory[]>('/api/public/categories'));
  }
  storefront(): Observable<PublicStorefrontConfiguration> {
    return this.cache.get('storefront', () => this.http.get<PublicStorefrontConfiguration>('/api/public/storefront'));
  }
  categoryPage(path: string): Observable<ShopCategoryPage> {
    return this.cache.get(`category:${path}`, () =>
      this.http.get<ShopCategoryPage>('/api/public/category', { params: { path } }));
  }
  navigation():Observable<NavigationCategory[]> {
    return this.cache.get('navigation', () => this.http.get<NavigationCategory[]>('/api/navigation/categories'));
  }
}
