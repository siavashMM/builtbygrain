import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, ReplaySubject, share } from 'rxjs';
import { ProductCard } from '../product.service';

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
  private readonly categoryResponse = this.http.get<ShopCategory[]>('/api/public/categories').pipe(
    share({ connector: () => new ReplaySubject(1), resetOnError: true, resetOnComplete: true, resetOnRefCountZero: true })
  );
  private readonly storefrontResponse = this.http.get<PublicStorefrontConfiguration>('/api/public/storefront').pipe(
    share({ connector: () => new ReplaySubject(1), resetOnError: true, resetOnComplete: true, resetOnRefCountZero: true })
  );
  categories(): Observable<ShopCategory[]> { return this.categoryResponse; }
  storefront(): Observable<PublicStorefrontConfiguration> { return this.storefrontResponse; }
  categoryPage(path: string): Observable<ShopCategoryPage> {
    return this.http.get<ShopCategoryPage>('/api/public/category', { params: { path } });
  }
  navigation():Observable<NavigationCategory[]>{return this.http.get<NavigationCategory[]>('/api/navigation/categories');}
}
