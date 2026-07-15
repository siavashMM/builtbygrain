import { Component, EventEmitter, Input, OnChanges, Output, inject, signal } from '@angular/core';
import { FormArray, FormBuilder, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';
import { AdminCatalogService, CatalogImage, CatalogVariant } from './admin-catalog.service';
import { ProductService } from '../product.service';

@Component({
  selector: 'app-product-variant-matrix',
  imports: [ReactiveFormsModule],
  templateUrl: './product-variant-matrix.component.html'
})
export class ProductVariantMatrixComponent implements OnChanges {
  private readonly api = inject(AdminCatalogService);
  private readonly fb = inject(FormBuilder);
  private readonly productsApi = inject(ProductService);

  @Input({ required: true }) productId!: number;
  @Input() productSlug = '';
  @Input() focusVariantId: number | null = null;
  @Output() changed = new EventEmitter<void>();

  readonly variants = signal<CatalogVariant[]>([]);
  readonly images = signal<CatalogImage[]>([]);
  readonly message = signal('');
  readonly imageVariant = signal<CatalogVariant | null>(null);
  readonly uploadFiles = signal<File[]>([]);
  readonly rows = new FormArray<FormGroup>([]);

  ngOnChanges(): void { if (this.productId) this.load(); }

  load(): void {
    forkJoin({ variants: this.api.variants(this.productId), images: this.api.images(this.productId), options: this.api.options(this.productId) }).subscribe(({ variants, images, options }) => {
      const activeValueIds = new Set(options.flatMap(option => option.values.filter(value => value.active).map(value => value.id)));
      const visibleVariants = variants.filter(variant => variant.optionValues.every(value => activeValueIds.has(value.valueId)));
      this.variants.set(visibleVariants);
      this.images.set(images);
      this.rows.clear();
      visibleVariants.forEach(variant => this.rows.push(this.fb.group({
        id: [variant.id],
        label: [variant.label],
        produced: [variant.active],
        priceEuros: [variant.regularPriceCents / 100],
        stockQuantity: [variant.stockQuantity]
      })));
    });
  }

  saveRow(index: number): void {
    const row = this.row(index).getRawValue();
    const original = this.variants().find(variant => variant.id === row.id);
    if (!original) return;
    const produced = Boolean(row.produced);
    const stockQuantity = Math.max(0, Math.trunc(Number(row.stockQuantity) || 0));
    const update: CatalogVariant = {
      ...original,
      regularPriceCents: Math.max(0, Math.round(Number(row.priceEuros) * 100)),
      availabilityStatus: produced ? this.statusFor(stockQuantity) : 'DISCONTINUED',
      stockQuantity,
      active: produced,
      allowBackorder: false
    };
    this.api.updateVariant(this.productId, update).subscribe({
      next: () => {
        this.message.set(`${original.label} saved.`);
        this.load();
        this.changed.emit();
      },
      error: error => this.message.set(error.error?.detail || `${original.label} could not be saved.`)
    });
  }

  saveAll(): void {
    const dirty = this.rows.controls.map((row, index) => ({ row, index })).filter(item => item.row.dirty);
    if (!dirty.length) { this.message.set('Everything is already saved.'); return; }
    forkJoin(dirty.map(item => {
      const row = item.row.getRawValue();
      const original = this.variants().find(variant => variant.id === row.id)!;
      const produced = Boolean(row.produced);
      const stockQuantity = Math.max(0, Math.trunc(Number(row.stockQuantity) || 0));
      return this.api.updateVariant(this.productId, {
        ...original,
        regularPriceCents: Math.max(0, Math.round(Number(row.priceEuros) * 100)),
        availabilityStatus: produced ? this.statusFor(stockQuantity) : 'DISCONTINUED',
        stockQuantity,
        active: produced,
        allowBackorder: false
      });
    })).subscribe({
      next: () => { this.message.set('All variant changes saved.'); this.load(); this.changed.emit(); },
      error: error => this.message.set(error.error?.detail || 'Some variant changes could not be saved.')
    });
  }

  openImages(index: number): void {
    const id = this.row(index).controls['id'].value;
    this.imageVariant.set(this.variants().find(variant => variant.id === id) ?? null);
  }

  deleteVariant(index: number): void {
    const variant = this.variants()[index];
    if (!variant || !globalThis.confirm(`Remove ${variant.label}? This combination will stay removed when sizes and colors are saved again.`)) return;
    this.api.deleteVariant(this.productId, variant.id).subscribe({
      next: () => {
        this.message.set(`${variant.label} removed.`);
        this.load();
        this.changed.emit();
      },
      error: error => this.message.set(error.error?.detail || `${variant.label} could not be removed.`)
    });
  }

  usePhoto(imageId: number): void {
    const variant = this.imageVariant();
    if (!variant) return;
    this.api.assignImage(this.productId, variant.id, imageId, 0, true).subscribe({
      next: () => {
        this.imageVariant.set(null);
        this.message.set(`${variant.label} photo updated.`);
        this.load();
        this.changed.emit();
      },
      error: error => this.message.set(error.error?.detail || 'The photo could not be assigned.')
    });
  }

  selectUpload(event: Event): void { this.uploadFiles.set(Array.from((event.target as HTMLInputElement).files ?? [])); }
  upload(): void {
    const files = this.uploadFiles();
    if (!files.length) return;
    this.productsApi.uploadProductImages(this.productId, files).subscribe({
      next: () => {
        this.uploadFiles.set([]);
        this.api.images(this.productId).subscribe(images => this.images.set(images));
        this.message.set('Photo uploaded. Select it below to use it for this variant.');
      },
      error: error => this.message.set(error.error?.detail || 'The photo could not be uploaded.')
    });
  }

  row(index: number): FormGroup { return this.rows.at(index); }

  private statusFor(stockQuantity: number): string {
    if (stockQuantity === 0) return 'OUT_OF_STOCK';
    if (stockQuantity <= 5) return 'LOW_STOCK';
    return 'IN_STOCK';
  }
}
