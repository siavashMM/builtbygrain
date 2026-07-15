import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export interface ShopCategory {
  id: number;
  parentId: number | null;
  name: string;
  slug: string;
  description: string | null;
  imageUrl: string | null;
  sortOrder: number;
  active: boolean;
}

@Injectable({ providedIn: 'root' })
export class ShopCatalogService {
  private readonly http = inject(HttpClient);
  categories(): Observable<ShopCategory[]> { return this.http.get<ShopCategory[]>('/api/public/categories'); }
}
