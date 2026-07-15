import { Component, EventEmitter, Input, OnChanges, Output, inject, signal } from '@angular/core';
import { forkJoin } from 'rxjs';
import { Product, ProductService } from '../product.service';
import { AdminCatalogService, CatalogImage, ListingImages } from './admin-catalog.service';

type ListingRole = 'primary' | 'hover';

@Component({
  selector: 'app-product-card-images-editor',
  imports: [],
  template: `
    <section class="card-image-editor" aria-labelledby="card-images-title">
      <header class="card-heading"><div><h3 id="card-images-title">Product card images</h3><p>Choose reusable images for product lists. This does not change the product-details gallery.</p></div></header>
      @if (error()) { <p class="admin-message error" role="alert">{{ error() }}</p> }
      @if (sameImageWarning()) { <p class="admin-message warning">Default and hover use the same image, so hovering will not create a visible change.</p> }
      <div class="listing-role-grid">
        @for (role of roleOptions; track role.key) {
          <article class="listing-role-card">
            <div><h4>{{ role.label }}</h4><p>{{ role.help }}</p></div>
            <div class="listing-role-preview">
              <img [src]="selectedImage(role.key)?.url || '/product-placeholder.svg'" [alt]="selectedImage(role.key)?.altText || role.label">
              <div><strong>{{ selectedImage(role.key)?.altText || selectedImage(role.key)?.filename || 'Automatic fallback' }}</strong><small>{{ usageText(selectedImage(role.key)) }}</small></div>
            </div>
            <div class="listing-role-actions">
              <button type="button" class="admin-secondary-button" (click)="selectingRole.set(role.key)">{{ selectedImage(role.key) ? 'Replace from media' : 'Select from media' }}</button>
              <label class="admin-secondary-button upload-button">Upload new<input type="file" accept="image/jpeg,image/png,image/webp,image/gif" (change)="uploadForRole(role.key,$event)"></label>
              @if (selectedImage(role.key)) { <button type="button" class="text-danger-button" (click)="assign(role.key,null)">Clear selection</button> }
            </div>
          </article>
        }
      </div>
    </section>

    <section class="media-library" aria-labelledby="media-library-title">
      <header class="card-heading"><div><h3 id="media-library-title">Product media</h3><p>Images can be shared by the gallery, variants, and product cards without duplicate files.</p></div></header>
      @if (images().length) {
        <div class="media-library-grid">
          @for (image of images(); track image.id; let index = $index) {
            <article><img [src]="image.url" [alt]="image.altText || image.filename"><strong>{{ image.altText || image.filename }}</strong><small>{{ usageText(image) }}</small><button type="button" class="text-danger-button" (click)="remove(index)">Delete image</button></article>
          }
        </div>
      } @else { <p class="state-message">No product images have been uploaded yet.</p> }
      <label class="admin-secondary-button upload-button">Upload product image<input type="file" multiple accept="image/jpeg,image/png,image/webp,image/gif" (change)="uploadMedia($event)"></label>
    </section>

    @if (selectingRole(); as role) {
      <div class="image-assignment-dialog" role="dialog" aria-modal="true" aria-labelledby="listing-image-dialog-title" (click)="selectingRole.set(null)">
        <section class="dialog-card" (click)="$event.stopPropagation()">
          <header><div><h3 id="listing-image-dialog-title">Select {{ role === 'primary' ? 'default' : 'hover' }} card image</h3><p>Choose any existing product or variant image.</p></div><button type="button" class="admin-secondary-button" (click)="selectingRole.set(null)">Close</button></header>
          <div class="assignable-images">
            @for (image of images(); track image.id) {
              <article><button type="button" class="media-choice" (click)="assign(role,image.id)"><img [src]="image.url" [alt]="image.altText || image.filename"></button><strong>{{ image.altText || image.filename }}</strong><small>{{ usageText(image) }}</small></article>
            }
          </div>
        </section>
      </div>
    }
  `
})
export class ProductCardImagesEditorComponent implements OnChanges {
  private readonly catalog = inject(AdminCatalogService);
  private readonly products = inject(ProductService);
  @Input({ required: true }) product!: Product;
  @Output() productChanged = new EventEmitter<Product>();

  protected readonly images = signal<CatalogImage[]>([]);
  protected readonly listing = signal<ListingImages>({ primaryImageId: null, hoverImageId: null });
  protected readonly selectingRole = signal<ListingRole | null>(null);
  protected readonly error = signal('');
  protected readonly roleOptions: {key:ListingRole;label:string;help:string}[] = [
    { key: 'primary', label: 'Default card image', help: 'Shown normally in product lists.' },
    { key: 'hover', label: 'Hover card image', help: 'Shown on desktop hover and keyboard focus.' }
  ];

  ngOnChanges(): void { if (this.product?.id) this.load(); }
  protected load(): void {
    forkJoin({ images: this.catalog.images(this.product.id), listing: this.catalog.listingImages(this.product.id) }).subscribe({
      next: result => { this.images.set(result.images); this.listing.set(result.listing); this.error.set(''); },
      error: () => this.error.set('Product media could not be loaded.')
    });
  }
  protected selectedImage(role:ListingRole):CatalogImage|undefined { const id=role==='primary'?this.listing().primaryImageId:this.listing().hoverImageId;return this.images().find(image=>image.id===id); }
  protected sameImageWarning():boolean { const roles=this.listing();return roles.primaryImageId!=null&&roles.primaryImageId===roles.hoverImageId; }
  protected usageText(image:CatalogImage|undefined):string { return image?.usages?.length ? image.usages.join(' · ') : 'Not currently assigned'; }
  protected assign(role:ListingRole,imageId:number|null):void { this.catalog.assignListingImage(this.product.id,role,imageId).subscribe({next:listing=>{this.listing.set(listing);this.selectingRole.set(null);this.load();},error:()=>this.error.set('The product card image could not be updated.')}); }
  protected uploadForRole(role:ListingRole,event:Event):void { const input=event.target as HTMLInputElement;const file=input.files?.[0];if(!file)return;this.products.uploadProductImages(this.product.id,[file]).subscribe({next:product=>{this.productChanged.emit(product);this.catalog.images(this.product.id).subscribe(images=>{this.images.set(images);const newest=images.at(-1);if(newest)this.assign(role,newest.id);});input.value='';},error:()=>this.error.set('Image upload failed. Use JPEG, PNG, WebP, or GIF up to 5 MB.')}); }
  protected uploadMedia(event:Event):void { const input=event.target as HTMLInputElement;const files=Array.from(input.files??[]);if(!files.length)return;this.products.uploadProductImages(this.product.id,files).subscribe({next:product=>{this.productChanged.emit(product);this.load();input.value='';},error:()=>this.error.set('Image upload failed. A product can have up to eight images, 5 MB each.')}); }
  protected remove(index:number):void { if(!globalThis.confirm('Delete this image? Images used by a product card must be cleared or reassigned first.'))return;this.products.removeProductImage(this.product.id,index).subscribe({next:product=>{this.productChanged.emit(product);this.load();},error:error=>this.error.set(error?.error?.detail||error?.error?.message||'This image is in use. Clear or reassign it before deleting.')}); }
}
