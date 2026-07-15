import { Component, EventEmitter, Input, OnChanges, Output, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { AdminCatalogService, CatalogOption, CatalogOptionValue } from './admin-catalog.service';

type VariationType = 'size' | 'color';
interface DraftChoice { id?: number; label: string; swatchHex?: string; }

@Component({
  selector: 'app-product-options-editor',
  imports: [],
  templateUrl: './product-options-editor.component.html'
})
export class ProductOptionsEditorComponent implements OnChanges {
  private readonly api = inject(AdminCatalogService);

  @Input({ required: true }) productId!: number;
  @Input() defaultPriceCents = 0;
  @Output() changed = new EventEmitter<void>();

  readonly options = signal<CatalogOption[]>([]);
  readonly sizes = signal<DraftChoice[]>([]);
  readonly colors = signal<DraftChoice[]>([]);
  readonly sizeInput = signal(60);
  readonly selectedColor = signal({ name: 'Brown', hex: '#795548' });
  readonly message = signal('');
  readonly working = signal(false);
  private readonly savedSignature = signal('');
  readonly dirty = computed(() => this.signature() !== this.savedSignature());
  readonly colorPalette = [
    { name: 'Natural', hex: '#d2b48c' }, { name: 'Brown', hex: '#795548' },
    { name: 'Dark brown', hex: '#4e342e' }, { name: 'Black', hex: '#212121' },
    { name: 'White', hex: '#f5f5f5' }, { name: 'Gray', hex: '#9e9e9e' },
    { name: 'Red', hex: '#b83232' }, { name: 'Green', hex: '#557a46' },
    { name: 'Blue', hex: '#3f6592' }
  ];

  ngOnChanges(): void { if (this.productId) this.load(); }

  load(): void {
    this.api.options(this.productId).subscribe({
      next: options => this.setSavedOptions(options),
      error: () => this.message.set('Sizes and colors could not be loaded.')
    });
  }

  changeSize(delta: number): void { this.sizeInput.update(value => Math.min(1000, Math.max(1, Math.trunc(value || 1) + delta))); }
  setSize(value: string | number): void { this.sizeInput.set(Math.min(1000, Math.max(1, Math.trunc(Number(value) || 1)))); }
  chooseColor(name: string, hex: string): void { this.selectedColor.set({ name, hex }); }
  chooseCustomColor(hex: string): void { this.selectedColor.set({ name: `Custom ${hex.toUpperCase()}`, hex }); }

  addSize(): void {
    const label = `${this.sizeInput()} cm`;
    if (this.sizes().some(value => value.label.toLowerCase() === label.toLowerCase())) return;
    this.sizes.update(values => [...values, this.previouslySaved('size', label) ?? { label }]);
    this.markDraftChanged();
  }

  addColor(): void {
    const color = this.selectedColor();
    if (this.colors().some(value => value.label.toLowerCase() === color.name.toLowerCase())) return;
    this.colors.update(values => [...values, this.previouslySaved('color', color.name) ?? { label: color.name, swatchHex: color.hex }]);
    this.markDraftChanged();
  }

  remove(type: VariationType, choice: DraftChoice): void {
    const target = type === 'size' ? this.sizes : this.colors;
    target.update(values => values.filter(value => value !== choice));
    this.markDraftChanged();
  }

  async saveVariations(): Promise<void> {
    if (!this.sizes().length || !this.colors().length) {
      this.message.set('Keep at least one size and one color before saving.');
      return;
    }
    this.working.set(true);
    this.message.set('Saving sizes and colors…');
    try {
      const sizeOption = await this.ensureOption('size');
      await this.syncChoices(sizeOption, this.sizes());
      const colorOption = await this.ensureOption('color');
      await this.syncChoices(colorOption, this.colors());
      await firstValueFrom(this.api.generateVariants(this.productId, this.defaultPriceCents));
      const options = await firstValueFrom(this.api.options(this.productId));
      this.setSavedOptions(options);
      this.message.set('Sizes, colors, and combinations saved.');
      this.changed.emit();
    } catch (error: any) {
      this.message.set(error?.error?.detail || 'Sizes and colors could not be saved.');
    } finally {
      this.working.set(false);
    }
  }

  discardDraft(): void {
    this.setSavedOptions(this.options());
    this.message.set('Unsaved size and color changes discarded.');
  }

  private async ensureOption(type: VariationType): Promise<CatalogOption> {
    const existing = this.option(type);
    if (!existing) return firstValueFrom(this.api.createOption(this.productId, {
      name: this.title(type), code: type, displayType: type === 'color' ? 'COLOR_SWATCH' : 'BUTTON',
      sortOrder: type === 'size' ? 0 : 1, required: true
    }));
    if (type === 'color' && existing.displayType !== 'COLOR_SWATCH') {
      return firstValueFrom(this.api.updateOption(this.productId, { ...existing, displayType: 'COLOR_SWATCH' }));
    }
    return existing;
  }

  private async syncChoices(option: CatalogOption, drafts: DraftChoice[]): Promise<void> {
    const draftIds = new Set(drafts.flatMap(choice => choice.id == null ? [] : [choice.id]));
    for (const existing of option.values.filter(value => value.active && !draftIds.has(value.id))) {
      await firstValueFrom(this.api.deleteValue(this.productId, option.id, existing.id));
    }
    for (const [draftIndex, draft] of drafts.entries()) {
      const existing = draft.id == null ? undefined : option.values.find(value => value.id === draft.id);
      if (existing) {
        if (!existing.active || existing.swatchHex !== draft.swatchHex) {
          await firstValueFrom(this.api.updateValue(this.productId, option.id, {
            ...existing, label: draft.label, swatchHex: draft.swatchHex, active: true
          }));
        }
      } else {
        await firstValueFrom(this.api.createValue(this.productId, option.id, {
          label: draft.label, code: this.slug(draft.label),
          ...(draft.swatchHex ? { swatchHex: draft.swatchHex } : {}),
          sortOrder: option.values.length + draftIndex, active: true
        }));
      }
    }
  }

  private setSavedOptions(options: CatalogOption[]): void {
    this.options.set(options);
    this.sizes.set(this.activeDrafts('size', options));
    this.colors.set(this.activeDrafts('color', options));
    this.savedSignature.set(this.signature());
  }
  private activeDrafts(type: VariationType, options = this.options()): DraftChoice[] {
    return this.option(type, options)?.values.filter(value => value.active)
      .map(value => ({ id: value.id, label: value.label, swatchHex: value.swatchHex })) ?? [];
  }
  private previouslySaved(type: VariationType, label: string): DraftChoice | undefined {
    const value = this.option(type)?.values.find(item => item.label.toLowerCase() === label.toLowerCase());
    return value ? { id: value.id, label: value.label, swatchHex: value.swatchHex } : undefined;
  }
  private markDraftChanged(): void { this.message.set('Unsaved changes. Click “Save sizes & colors” to rebuild the combinations.'); }
  private signature(): string { return JSON.stringify({ sizes: this.sizes(), colors: this.colors() }); }
  private option(type: VariationType, options = this.options()): CatalogOption | undefined { return options.find(option => option.code === type); }
  private title(type: VariationType): string { return type === 'size' ? 'Size' : 'Color'; }
  private slug(value: string): string { return value.toLowerCase().trim().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, ''); }
}
