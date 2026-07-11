import { Component, EventEmitter, Input, OnChanges, Output, signal } from '@angular/core';
import { Product, ProductRequest } from '../product.service';

interface ProductForm {
  name: string;
  slug: string;
  description: string;
  priceCents: number;
  currency: string;
  inStock: boolean;
  sizes: string;
  active: boolean;
}

@Component({
  selector: 'app-admin-product-form',
  imports: [],
  templateUrl: './admin-product-form.component.html'
})
export class AdminProductFormComponent implements OnChanges {
  @Input() product: Product | null = null;
  @Input() saving = false;
  @Output() saveProduct = new EventEmitter<{ request: ProductRequest; images: File[] }>();
  @Output() newProduct = new EventEmitter<void>();

  protected readonly form = signal<ProductForm>(this.emptyForm());
  protected readonly selectedImages = signal<File[]>([]);
  protected readonly imageError = signal('');

  ngOnChanges(): void {
    this.form.set(this.product === null ? this.emptyForm() : this.formFromProduct(this.product));
  }

  protected updateField<K extends keyof ProductForm>(field: K, value: ProductForm[K]): void {
    this.form.update((form) => ({ ...form, [field]: value }));
  }

  protected updatePriceCents(value: string | number): void {
    this.updateField('priceCents', Number(value));
  }

  protected submit(event: SubmitEvent): void {
    event.preventDefault();

    const form = this.form();
    const request: ProductRequest = {
      name: form.name.trim(),
      slug: form.slug.trim(),
      description: this.optionalText(form.description),
      priceCents: Number(form.priceCents),
      currency: form.currency.trim().toUpperCase(),
      inStock: form.inStock,
      sizes: form.sizes
        .split(',')
        .map((size) => size.trim())
        .filter((size) => size.length > 0),
      active: form.active
    };
    this.saveProduct.emit({ request, images: this.product === null ? this.selectedImages() : [] });
  }

  protected selectImages(event: Event): void {
    const input = event.target as HTMLInputElement;
    const files = Array.from(input.files ?? []);
    this.imageError.set('');
    if (files.length > 8) {
      this.selectedImages.set([]);
      this.imageError.set('Choose at most 8 images.');
      input.value = '';
      return;
    }
    if (files.some((file) => file.size > 5 * 1024 * 1024)) {
      this.selectedImages.set([]);
      this.imageError.set('Each image must be 5 MB or smaller.');
      input.value = '';
      return;
    }
    this.selectedImages.set(files);
  }

  protected startNewProduct(): void {
    this.form.set(this.emptyForm());
    this.selectedImages.set([]);
    this.newProduct.emit();
  }

  private formFromProduct(product: Product): ProductForm {
    return {
      name: product.name,
      slug: product.slug,
      description: product.description ?? '',
      priceCents: product.priceCents,
      currency: product.currency,
      inStock: product.inStock,
      sizes: product.sizes.join(', '),
      active: product.active
    };
  }

  private emptyForm(): ProductForm {
    return {
      name: '',
      slug: '',
      description: '',
      priceCents: 4900,
      currency: 'EUR',
      inStock: true,
      sizes: '',
      active: true
    };
  }

  private optionalText(value: string): string | null {
    const trimmed = value.trim();
    return trimmed.length > 0 ? trimmed : null;
  }
}
