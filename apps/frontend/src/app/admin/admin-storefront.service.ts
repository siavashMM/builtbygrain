import { HttpClient, HttpEvent } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export interface StorefrontSettings {
  heroImageUrl: string | null;
  heroImageAltText: string | null;
  heroHeading: string | null;
  heroSupportingText: string | null;
  updatedAt: string;
}

export interface StorefrontCategory {
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

export interface StorefrontProductReference {
  id: number;
  name: string;
  slug: string;
  active: boolean;
  status: 'DRAFT' | 'ACTIVE' | 'ARCHIVED';
}

export interface NavigationGroup {
  id: number;
  label: string;
  active: boolean;
  displayOrder: number;
  categories: StorefrontCategory[];
  featuredProducts: StorefrontProductReference[];
}

export interface StorefrontSettingsUpdate {
  heroImageAltText: string | null;
  heroHeading: string | null;
  heroSupportingText: string | null;
}

@Injectable({ providedIn: 'root' })
export class AdminStorefrontService {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/admin/storefront';

  settings(): Observable<StorefrontSettings> {
    return this.http.get<StorefrontSettings>(`${this.base}/settings`);
  }

  updateSettings(update: StorefrontSettingsUpdate): Observable<StorefrontSettings> {
    return this.http.put<StorefrontSettings>(`${this.base}/settings`, update);
  }

  replaceHeroImage(image: File, altText: string): Observable<HttpEvent<StorefrontSettings>> {
    const form = new FormData();
    form.append('image', image);
    form.append('altText', altText);
    return this.http.post<StorefrontSettings>(`${this.base}/settings/hero-image`, form, {
      observe: 'events', reportProgress: true
    });
  }

  groups(): Observable<NavigationGroup[]> {
    return this.http.get<NavigationGroup[]>(`${this.base}/navigation-groups`);
  }

  createGroup(label: string): Observable<NavigationGroup> {
    return this.http.post<NavigationGroup>(`${this.base}/navigation-groups`, { label, active: false });
  }

  updateGroup(group: NavigationGroup, label: string): Observable<NavigationGroup> {
    return this.http.put<NavigationGroup>(`${this.base}/navigation-groups/${group.id}`, { label, active: group.active });
  }

  deleteGroup(groupId: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/navigation-groups/${groupId}`);
  }

  setGroupActive(groupId: number, active: boolean): Observable<NavigationGroup> {
    return this.http.patch<NavigationGroup>(`${this.base}/navigation-groups/${groupId}/${active ? 'activate' : 'deactivate'}`, null);
  }

  reorderGroups(ids: number[]): Observable<NavigationGroup[]> {
    return this.http.put<NavigationGroup[]>(`${this.base}/navigation-groups/reorder`, { ids });
  }

  assignCategory(groupId: number, categoryId: number): Observable<NavigationGroup> {
    return this.http.post<NavigationGroup>(`${this.base}/navigation-groups/${groupId}/categories`, { id: categoryId });
  }

  removeCategory(groupId: number, categoryId: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/navigation-groups/${groupId}/categories/${categoryId}`);
  }

  reorderCategories(groupId: number, ids: number[]): Observable<NavigationGroup> {
    return this.http.put<NavigationGroup>(`${this.base}/navigation-groups/${groupId}/categories/reorder`, { ids });
  }

  assignFeaturedProduct(groupId: number, productId: number): Observable<NavigationGroup> {
    return this.http.post<NavigationGroup>(`${this.base}/navigation-groups/${groupId}/featured-products`, { id: productId });
  }

  removeFeaturedProduct(groupId: number, productId: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/navigation-groups/${groupId}/featured-products/${productId}`);
  }

  reorderFeaturedProducts(groupId: number, ids: number[]): Observable<NavigationGroup> {
    return this.http.put<NavigationGroup>(`${this.base}/navigation-groups/${groupId}/featured-products/reorder`, { ids });
  }
}
