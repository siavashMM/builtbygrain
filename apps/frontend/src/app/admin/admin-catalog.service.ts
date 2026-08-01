import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export type CatalogNodeType = 'CATEGORY' | 'PRODUCT' | 'VARIANT';
export interface CatalogTreeNode {
  id: string; nodeType: CatalogNodeType; parentId: string | null; label: string;
  secondaryLabel: string | null; status: string; hasChildren: boolean; iconType: string; sortOrder: number;
}
export interface CategoryDetails { id: number; parentId: number | null; name: string; slug: string; description: string | null; imageUrl: string | null; sortOrder: number; active: boolean; }
export interface CategoryRequest { name: string; slug: string; parentId: number | null; description?: string | null; imageUrl?: string | null; sortOrder?: number; active: boolean; }
export interface AdminCategoryNode { id:number; parentId:number|null; name:string; slug:string; active:boolean; position:number; directProductCount:number; children:AdminCategoryNode[]; }
export interface CategoryOption { id:number; name:string; path:string; active:boolean; depth:number; }
export interface CatalogOptionValue { id: number; label: string; code: string; swatchHex?: string; swatchImageUrl?: string; extraLabel?: string; sortOrder: number; active: boolean; }
export interface CatalogOption { id: number; productId: number; name: string; code: string; displayType: 'COLOR_SWATCH'|'BUTTON'|'DROPDOWN'; sortOrder: number; required: boolean; values: CatalogOptionValue[]; }
export interface CatalogVariant { id:number; publicId:string; productId:number; sku?:string; label:string; optionValues:{optionId:number;optionName:string;valueId:number;label:string}[]; regularPriceCents:number; salePriceCents?:number; stockQuantity:number; availabilityStatus:string; active:boolean; allowBackorder:boolean; deliveryEstimate?:string; primaryImageUrl?:string; imageUrls:string[]; }
export interface CatalogImage { id:number; url:string; altText?:string; filename:string; sortOrder:number; shared:boolean; active:boolean; usages:string[]; }
export interface ListingImages { primaryImageId:number|null; hoverImageId:number|null; }

@Injectable({ providedIn: 'root' })
export class AdminCatalogService {
  private readonly http = inject(HttpClient);
  roots(): Observable<CatalogTreeNode[]> { return this.http.get<CatalogTreeNode[]>('/api/admin/catalog/tree'); }
  categoryTree():Observable<AdminCategoryNode[]>{return this.http.get<AdminCategoryNode[]>('/api/admin/categories/tree');}
  categoryOptions():Observable<CategoryOption[]>{return this.http.get<CategoryOption[]>('/api/admin/categories/options');}
  children(nodeId: string): Observable<CatalogTreeNode[]> {
    return this.http.get<CatalogTreeNode[]>(`/api/admin/catalog/nodes/${encodeURIComponent(nodeId)}/children`);
  }
  createCategory(request: CategoryRequest):Observable<CategoryDetails> { return this.http.post<CategoryDetails>('/api/admin/categories', request); }
  createSimpleCategory(name:string,parentId:number|null):Observable<CategoryDetails>{return this.http.post<CategoryDetails>('/api/admin/categories?simple=true',{name,parentId});}
  renameCategory(id:number,name:string):Observable<CategoryDetails>{return this.http.patch<CategoryDetails>(`/api/admin/categories/${id}/name`,{name});}
  reparentCategory(id:number,parentId:number|null,position?:number):Observable<CategoryDetails>{return this.http.patch<CategoryDetails>(`/api/admin/categories/${id}/parent`,{parentId,position});}
  positionCategory(id:number,position:number):Observable<CategoryDetails>{return this.http.patch<CategoryDetails>(`/api/admin/categories/${id}/position`,{position});}
  categories(): Observable<CategoryDetails[]> { return this.http.get<CategoryDetails[]>('/api/admin/categories'); }
  category(id:number): Observable<CategoryDetails> { return this.http.get<CategoryDetails>(`/api/admin/categories/${id}`); }
  updateCategory(id:number,request:CategoryRequest): Observable<CategoryDetails> { return this.http.put<CategoryDetails>(`/api/admin/categories/${id}`,request); }
  setCategoryStatus(id:number,active:boolean): Observable<CategoryDetails> { return this.http.patch<CategoryDetails>(`/api/admin/categories/${id}/status`,{active}); }
  deleteCategory(id:number): Observable<void> { return this.http.delete<void>(`/api/admin/categories/${id}?confirmed=true`); }
  moveCategory(id: number, parentId: number | null, sortOrder: number) {
    return this.http.patch(`/api/admin/categories/${id}/move`, { parentId, sortOrder });
  }
  options(productId:number):Observable<CatalogOption[]>{return this.http.get<CatalogOption[]>(`/api/admin/products/${productId}/options`);}
  createOption(productId:number,r:Omit<CatalogOption,'id'|'productId'|'values'>):Observable<CatalogOption>{return this.http.post<CatalogOption>(`/api/admin/products/${productId}/options`,r);}
  updateOption(productId:number,o:CatalogOption):Observable<CatalogOption>{return this.http.put<CatalogOption>(`/api/admin/products/${productId}/options/${o.id}`,o);}
  deleteOption(productId:number,optionId:number):Observable<void>{return this.http.delete<void>(`/api/admin/products/${productId}/options/${optionId}`);}
  createValue(productId:number,optionId:number,r:Omit<CatalogOptionValue,'id'>):Observable<CatalogOptionValue>{return this.http.post<CatalogOptionValue>(`/api/admin/products/${productId}/options/${optionId}/values`,r);}
  updateValue(productId:number,optionId:number,v:CatalogOptionValue):Observable<CatalogOptionValue>{return this.http.put<CatalogOptionValue>(`/api/admin/products/${productId}/options/${optionId}/values/${v.id}`,v);}
  deleteValue(productId:number,optionId:number,valueId:number):Observable<void>{return this.http.delete<void>(`/api/admin/products/${productId}/options/${optionId}/values/${valueId}`);}
  generationPreview(productId:number):Observable<{combinationCount:number;existingCount:number;createCount:number}>{return this.http.get<{combinationCount:number;existingCount:number;createCount:number}>(`/api/admin/products/${productId}/variants/generate-preview`);}
  generateVariants(productId:number,defaultPriceCents:number):Observable<CatalogVariant[]>{return this.http.post<CatalogVariant[]>(`/api/admin/products/${productId}/variants/generate`,{defaultPriceCents,deactivateRemoved:true});}
  variants(productId:number):Observable<CatalogVariant[]>{return this.http.get<CatalogVariant[]>(`/api/admin/products/${productId}/variants`);}
  updateVariant(productId:number,v:CatalogVariant):Observable<CatalogVariant>{return this.http.put<CatalogVariant>(`/api/admin/products/${productId}/variants/${v.id}`,v);}
  deleteVariant(productId:number,variantId:number):Observable<void>{return this.http.delete<void>(`/api/admin/products/${productId}/variants/${variantId}`);}
  bulkVariants(productId:number,r:Record<string,unknown>):Observable<CatalogVariant[]>{return this.http.post<CatalogVariant[]>(`/api/admin/products/${productId}/variants/bulk-update`,r);}
  images(productId:number):Observable<CatalogImage[]>{return this.http.get<CatalogImage[]>(`/api/admin/products/${productId}/images`);}
  deleteImage(productId:number,imageId:number):Observable<void>{return this.http.delete<void>(`/api/admin/products/${productId}/catalog-images/${imageId}`);}
  listingImages(productId:number):Observable<ListingImages>{return this.http.get<ListingImages>(`/api/admin/products/${productId}/listing-images`);}
  assignListingImage(productId:number,role:'primary'|'hover',imageId:number|null):Observable<ListingImages>{return this.http.put<ListingImages>(`/api/admin/products/${productId}/listing-images/${role}`,{imageId});}
  assignImage(productId:number,variantId:number,imageId:number,sortOrder:number,primary:boolean):Observable<CatalogVariant>{return this.http.post<CatalogVariant>(`/api/admin/products/${productId}/variants/${variantId}/images`,{imageId,sortOrder,primary});}
  removeVariantImage(productId:number,variantId:number,imageId:number):Observable<CatalogVariant>{return this.http.delete<CatalogVariant>(`/api/admin/products/${productId}/variants/${variantId}/images/${imageId}`);}
}
