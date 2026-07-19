import { Component, EventEmitter, HostListener, Input, OnChanges, Output, SimpleChanges, ViewChild, signal } from '@angular/core';
import { Product, ProductOption, ProductOptionValue, ProductRequest, ProductVariant } from '../product.service';
import { ProductOptionsEditorComponent } from './product-options-editor.component';
import { ProductVariantMatrixComponent } from './product-variant-matrix.component';
import { ProductCardImagesEditorComponent } from './product-card-images-editor.component';
import { RouterLink } from '@angular/router';
import { CategoryOption } from './admin-catalog.service';

interface ProductForm {
  name: string;
  slug: string;
  description: string;
  priceCents: number;
  currency: string;
  inStock: boolean;
  active: boolean;
  subtitle: string;
  badge: string;
  deliveryEstimate: string;
  benefits: string;
}

interface ColorRow { label: string; swatchColor: string; }
interface VariantImageRow { key: string; label: string; }

@Component({
  selector: 'app-admin-product-form',
  imports: [ProductOptionsEditorComponent,ProductVariantMatrixComponent,ProductCardImagesEditorComponent,RouterLink],
  templateUrl: './admin-product-form.component.html'
})
export class AdminProductFormComponent implements OnChanges {
  @ViewChild(ProductVariantMatrixComponent) private variantEditor?: ProductVariantMatrixComponent;
  @Input() product: Product | null = null;
  @Input() categoryId: number | null = null;
  @Input() categoryName = '';
  @Input() categoryOptions:CategoryOption[]=[];
  @Input() focusVariantId:number|null=null;
  @Input() saving = false;
  @Output() saveProduct = new EventEmitter<{ request: ProductRequest; images: File[] }>();
  @Output() newProduct = new EventEmitter<void>();
  @Output() catalogChanged=new EventEmitter<void>();
  @Output() duplicateProduct=new EventEmitter<void>();
  @Output() archiveProduct=new EventEmitter<void>();
  @Output() productChanged=new EventEmitter<Product>();
  protected readonly activeTab=signal<'general'|'variants'|'images'|'content'|'seo'|'preview'>('general');
  protected readonly dirty = signal(false);
  protected readonly selectedCategoryId=signal<number|null>(null);

  protected readonly form = signal<ProductForm>(this.emptyForm());
  protected readonly selectedImages = signal<File[]>([]);
  protected readonly imageError = signal('');
  protected readonly colors = signal<ColorRow[]>([]);
  protected readonly selectableSizes = signal<string[]>([]);
  protected readonly sizeAffectsImages = signal(true);
  protected readonly variantImages = signal<Record<string, string>>({});

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['product'] || changes['categoryId']) {
      this.resetFromProduct();
    }
  }

  protected updateField<K extends keyof ProductForm>(field: K, value: ProductForm[K]): void {
    this.form.update((form) => ({ ...form, [field]: value }));
    this.dirty.set(true);
  }

  protected updatePriceEuros(value: string | number): void {
    this.updateField('priceCents', Math.max(0, Math.round(Number(value) * 100)));
  }
  protected categoryPath():string{return this.categoryOptions.find(value=>value.id===this.selectedCategoryId())?.path??this.categoryName;}
  protected selectCategoryPath(value:string):void{const category=this.categoryOptions.find(option=>option.path===value.replace(/ \(inactive\)$/,''));if(!category)return;this.selectedCategoryId.set(category.id);this.dirty.set(true);}

  protected normalizeSlug(): void {
    const source = this.form().slug || this.form().name;
    this.updateField('slug', this.slugify(source));
  }

  protected refreshVariants(): void {
    this.variantEditor?.load();
    this.catalogChanged.emit();
  }

  protected addColor(): void { this.colors.update((rows) => [...rows, { label: '', swatchColor: '#b88f63' }]); }
  protected updateColor(index: number, field: keyof ColorRow, value: string): void {
    this.colors.update((rows) => rows.map((row, i) => i === index ? { ...row, [field]: value } : row));
  }
  protected removeColor(index: number): void { this.colors.update((rows) => rows.filter((_, i) => i !== index)); }
  protected addSize(): void { this.selectableSizes.update((rows) => [...rows, '']); }
  protected updateSize(index: number, value: string): void { this.selectableSizes.update((rows) => rows.map((row, i) => i === index ? value : row)); }
  protected removeSize(index: number): void { this.selectableSizes.update((rows) => rows.filter((_, i) => i !== index)); }
  protected setSizeAffectsImages(value: boolean): void { this.sizeAffectsImages.set(value); }
  protected setVariantImage(key: string, imageUrl: string): void { this.variantImages.update((images) => ({ ...images, [key]: imageUrl })); }
  protected variantImageRows(): VariantImageRow[] {
    const colors = this.colors().map((value) => value.label.trim()).filter(Boolean);
    const sizes = this.selectableSizes().map((value) => value.trim()).filter(Boolean);
    if (!colors.length && !sizes.length) return [];
    if (!this.sizeAffectsImages() && colors.length) return colors.map((color) => ({ key: `color-${this.slugify(color)}`, label: color }));
    if (colors.length && sizes.length) return colors.flatMap((color) => sizes.map((size) => ({ key: `color-${this.slugify(color)}|size-${this.slugify(size)}`, label: `${color} · ${size}` })));
    return (colors.length ? colors.map((color) => `color-${this.slugify(color)}`) : sizes.map((size) => `size-${this.slugify(size)}`))
      .map((key) => ({ key, label: key.replace(/^(color|size)-/, '') }));
  }

  protected submit(event: SubmitEvent): void {
    event.preventDefault();

    const form = this.form();
    const slug = this.slugify(form.slug || form.name);
    if (slug !== form.slug) this.updateField('slug', slug);
    const sizes = this.unique(this.selectableSizes().map((value) => value.trim()).filter(Boolean));
    const colors = this.colors().map((value) => ({ ...value, label: value.label.trim() })).filter((value) => value.label);
    const options = this.buildOptions(colors, sizes);
    const variants = this.buildVariants(options, form);
    const request: ProductRequest = {
      name: form.name.trim(),
      slug,
      description: this.optionalText(form.description),
      priceCents: Number(form.priceCents),
      currency: form.currency.trim().toUpperCase(),
      inStock: form.inStock,
      sizes,
      active: form.active,
      configuration: {
        ...(this.product?.configuration ?? { specifications: {}, sections: [], faqs: [], options: [], variants: [], benefits: [] }),
        subtitle: this.optionalText(form.subtitle) ?? undefined,
        badge: this.optionalText(form.badge) ?? undefined,
        deliveryEstimate: this.optionalText(form.deliveryEstimate) ?? undefined,
        benefits: form.benefits.split('\n').map((value) => value.trim()).filter(Boolean),
        options,
        variants,
        sizeAffectsImages: this.sizeAffectsImages()
      },
      categoryId: this.selectedCategoryId() ?? this.product?.categoryId ?? this.categoryId
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
    this.dirty.set(files.length > 0 || this.dirty());
  }

  protected startNewProduct(): void {
    if (this.dirty() && !globalThis.confirm('Discard your unsaved product changes?')) return;
    this.form.set(this.emptyForm());
    this.colors.set([]);
    this.selectableSizes.set([]);
    this.variantImages.set({});
    this.sizeAffectsImages.set(true);
    this.selectedImages.set([]);
    this.dirty.set(false);
    this.newProduct.emit();
  }

  protected discardChanges(): void {
    this.resetFromProduct();
  }

  public hasUnsavedChanges(): boolean {
    return this.dirty();
  }

  @HostListener('window:beforeunload', ['$event'])
  protected warnBeforeUnload(event: BeforeUnloadEvent): void {
    if (!this.dirty()) return;
    event.preventDefault();
    event.returnValue = '';
  }

  private resetFromProduct(): void {
    this.selectedCategoryId.set(this.product?.categoryId ?? this.categoryId);
    this.form.set(this.product === null ? this.emptyForm() : this.formFromProduct(this.product));
    this.loadOptions(this.product);
    this.selectedImages.set([]);
    this.imageError.set('');
    this.dirty.set(false);
  }

  private formFromProduct(product: Product): ProductForm {
    return {
      name: product.name,
      slug: product.slug,
      description: product.description ?? '',
      priceCents: product.priceCents,
      currency: product.currency,
      inStock: product.inStock,
      active: product.active,
      subtitle: product.configuration?.subtitle ?? '',
      badge: product.configuration?.badge ?? '',
      deliveryEstimate: product.configuration?.deliveryEstimate ?? '',
      benefits: product.configuration?.benefits?.join('\n') ?? ''
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
      active: true,
      subtitle: '', badge: '', deliveryEstimate: '', benefits: ''
    };
  }

  private optionalText(value: string): string | null {
    const trimmed = value.trim();
    return trimmed.length > 0 ? trimmed : null;
  }

  private loadOptions(product: Product | null): void {
    const color = product?.configuration?.options?.find((option) => option.id === 'color');
    const size = product?.configuration?.options?.find((option) => option.id === 'size');
    this.colors.set(color?.values.map((value) => ({ label: value.label, swatchColor: value.swatchColor || '#b88f63' })) ?? []);
    this.selectableSizes.set(size?.values.map((value) => value.label) ?? product?.sizes ?? []);
    this.sizeAffectsImages.set(product?.configuration?.sizeAffectsImages !== false);
    const assignments: Record<string, string> = {};
    product?.configuration?.variants?.forEach((variant) => {
      if (!variant.imageUrls?.[0]) return;
      const key = product.configuration.sizeAffectsImages === false
        ? variant.optionValueIds.find((id) => id.startsWith('color-')) ?? variant.optionValueIds.join('|')
        : variant.optionValueIds.join('|');
      if (key) assignments[key] = variant.imageUrls[0];
    });
    this.variantImages.set(assignments);
  }

  private buildOptions(colors: ColorRow[], sizes: string[]): ProductOption[] {
    const options: ProductOption[] = [];
    if (colors.length) options.push({ id: 'color', name: 'Color', displayType: 'COLOR_SWATCH', values: colors.map((color) => ({ id: `color-${this.slugify(color.label)}`, label: color.label, swatchColor: color.swatchColor })) });
    if (sizes.length) options.push({ id: 'size', name: 'Size', displayType: 'BUTTON', values: sizes.map((size) => ({ id: `size-${this.slugify(size)}`, label: size })) });
    return options;
  }

  private buildVariants(options: ProductOption[], form: ProductForm): ProductVariant[] {
    const combinations = options.length ? options.reduce<ProductOptionValue[][]>((rows, option) => rows.flatMap((row) => option.values.map((value) => [...row, value])), [[]]) : [[]];
    return combinations.map((values, index) => {
      const ids = values.map((value) => value.id);
      const existing = this.product?.configuration?.variants?.find((variant) => variant.optionValueIds.length === ids.length && ids.every((id) => variant.optionValueIds.includes(id)));
      const imageKey = this.sizeAffectsImages() ? ids.join('|') : ids.find((id) => id.startsWith('color-')) ?? ids.join('|');
      const assignedImage = this.variantImages()[imageKey];
      const base = existing ?? {
        id: ids.length ? `variant-${ids.map((id) => id.replace(/^(color|size)-/, '')).join('-')}` : `default-${this.product?.id ?? 'new'}`,
        sku: '', optionValueIds: ids, priceCents: Number(form.priceCents), stockQuantity: form.inStock ? 99 : 0,
        stockStatus: form.inStock ? 'IN_STOCK' : 'OUT_OF_STOCK', available: form.inStock,
        backorderAllowed: false, preorderAllowed: false, imageUrls: index === 0 ? (this.product?.imageUrls ?? []) : []
      };
      return { ...base, imageUrls: assignedImage ? [assignedImage] : (existing?.imageUrls ?? []) };
    });
  }

  private slugify(value: string): string { return value.toLowerCase().trim().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '') || 'value'; }
  private unique(values: string[]): string[] { return [...new Set(values)]; }
}
