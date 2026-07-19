import { HttpErrorResponse, HttpEventType } from '@angular/common/http';
import { DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { forkJoin } from 'rxjs';

import { Product, ProductService } from '../product.service';
import { AdminCatalogService, CategoryOption } from './admin-catalog.service';
import {
  AdminStorefrontService,
  NavigationGroup,
  StorefrontProductReference,
  StorefrontSettings
} from './admin-storefront.service';

type PageState = 'loading' | 'ready' | 'error';

@Component({
  selector: 'app-admin-storefront',
  imports: [DatePipe],
  templateUrl: './admin-storefront.component.html'
})
export class AdminStorefrontComponent implements OnInit {
  private readonly storefront = inject(AdminStorefrontService);
  private readonly catalog = inject(AdminCatalogService);
  private readonly productService = inject(ProductService);

  protected readonly state = signal<PageState>('loading');
  protected readonly settings = signal<StorefrontSettings | null>(null);
  protected readonly groups = signal<NavigationGroup[]>([]);
  protected readonly categories = signal<CategoryOption[]>([]);
  protected readonly products = signal<Product[]>([]);
  protected readonly groupLabels = signal<Record<number, string>>({});
  protected readonly categorySelections = signal<Partial<Record<number, string>>>({});
  protected readonly productSelections = signal<Partial<Record<number, string>>>({});
  protected readonly heroFile = signal<File | null>(null);
  protected readonly heroAlt = signal('');
  protected readonly heroHeading = signal('');
  protected readonly heroSupportingText = signal('');
  protected readonly newGroupLabel = signal('');
  protected readonly action = signal<string | null>(null);
  protected readonly uploadProgress = signal<number | null>(null);
  protected readonly message = signal('');
  protected readonly messageIsError = signal(false);
  protected readonly saving = computed(() => this.action() !== null);

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    this.state.set('loading');
    forkJoin({
      settings: this.storefront.settings(),
      groups: this.storefront.groups(),
      categories: this.catalog.categoryOptions(),
      products: this.productService.getAdminProducts()
    }).subscribe({
      next: result => {
        this.applySettings(result.settings);
        this.applyGroups(result.groups);
        this.categories.set(result.categories);
        this.products.set(result.products);
        this.state.set('ready');
      },
      error: error => {
        this.state.set('error');
        this.fail(error, 'Storefront configuration could not be loaded.');
      }
    });
  }

  protected saveHeroCopy(): void {
    const imageConfigured = !!this.settings()?.heroImageUrl;
    if (imageConfigured && !this.heroAlt().trim()) {
      this.showValidation('Alt text is required while a homepage hero image is configured.');
      return;
    }
    this.action.set('hero-copy');
    this.storefront.updateSettings({
      heroImageAltText: this.clean(this.heroAlt()),
      heroHeading: this.clean(this.heroHeading()),
      heroSupportingText: this.clean(this.heroSupportingText())
    }).subscribe({
      next: settings => {
        this.applySettings(settings);
        this.done('Homepage hero text saved.');
      },
      error: error => this.failAction(error, 'Homepage hero text could not be saved.')
    });
  }

  protected chooseHeroFile(input: HTMLInputElement): void {
    this.heroFile.set(input.files?.item(0) ?? null);
    this.uploadProgress.set(null);
  }

  protected replaceHero(): void {
    const file = this.heroFile();
    if (!file) {
      this.showValidation('Choose an image to upload.');
      return;
    }
    if (!this.heroAlt().trim()) {
      this.showValidation('Alt text is required before uploading the hero image.');
      return;
    }
    this.action.set('hero-upload');
    this.uploadProgress.set(0);
    this.storefront.replaceHeroImage(file, this.heroAlt().trim()).subscribe({
      next: event => {
        if (event.type === HttpEventType.UploadProgress) {
          this.uploadProgress.set(event.total ? Math.round(100 * event.loaded / event.total) : 0);
        }
        if (event.type === HttpEventType.Response && event.body) {
          this.applySettings(event.body);
          this.heroFile.set(null);
          this.uploadProgress.set(100);
          this.done('Homepage hero image replaced.');
        }
      },
      error: error => {
        this.uploadProgress.set(null);
        this.failAction(error, 'The replacement image could not be uploaded. The current hero remains unchanged.');
      }
    });
  }

  protected createGroup(): void {
    const label = this.newGroupLabel().trim();
    if (!label) {
      this.showValidation('Enter a navigation group label.');
      return;
    }
    this.action.set('group-create');
    this.storefront.createGroup(label).subscribe({
      next: group => {
        this.applyGroups([...this.groups(), group]);
        this.newGroupLabel.set('');
        this.done('Navigation group created. It is inactive until you activate it.');
      },
      error: error => this.failAction(error, 'Navigation group could not be created.')
    });
  }

  protected renameGroup(group: NavigationGroup): void {
    const label = (this.groupLabels()[group.id] ?? '').trim();
    if (!label) {
      this.showValidation('Navigation group labels cannot be empty.');
      return;
    }
    this.action.set(`group-${group.id}-rename`);
    this.storefront.updateGroup(group, label).subscribe({
      next: updated => {
        this.replaceGroup(updated);
        this.done('Navigation group renamed.');
      },
      error: error => this.failAction(error, 'Navigation group could not be renamed.')
    });
  }

  protected toggleGroup(group: NavigationGroup): void {
    this.action.set(`group-${group.id}-status`);
    this.storefront.setGroupActive(group.id, !group.active).subscribe({
      next: updated => {
        this.replaceGroup(updated);
        this.done(`Navigation group ${updated.active ? 'activated' : 'deactivated'}.`);
      },
      error: error => this.failAction(error, 'Navigation group status could not be changed.')
    });
  }

  protected deleteGroup(group: NavigationGroup): void {
    if (!globalThis.confirm(`Delete “${group.label}” and its navigation assignments?`)) return;
    this.action.set(`group-${group.id}-delete`);
    this.storefront.deleteGroup(group.id).subscribe({
      next: () => {
        this.applyGroups(this.groups().filter(item => item.id !== group.id).map((item, index) => ({ ...item, displayOrder: index })));
        this.done('Navigation group deleted.');
      },
      error: error => this.failAction(error, 'Navigation group could not be deleted.')
    });
  }

  protected moveGroup(group: NavigationGroup, direction: -1 | 1): void {
    const ids = this.groups().map(item => item.id);
    const from = ids.indexOf(group.id);
    const to = from + direction;
    if (from < 0 || to < 0 || to >= ids.length) return;
    [ids[from], ids[to]] = [ids[to], ids[from]];
    this.action.set(`group-${group.id}-order`);
    this.storefront.reorderGroups(ids).subscribe({
      next: groups => {
        this.applyGroups(groups);
        this.done('Navigation group order updated.');
      },
      error: error => this.failAction(error, 'Navigation groups could not be reordered.')
    });
  }

  protected assignCategory(group: NavigationGroup): void {
    const categoryId = Number(this.categorySelections()[group.id]);
    if (!categoryId) return;
    if (group.categories.some(category => category.id === categoryId)) {
      this.showValidation('That category is already assigned to this navigation group.');
      return;
    }
    this.action.set(`group-${group.id}-category-add`);
    this.storefront.assignCategory(group.id, categoryId).subscribe({
      next: updated => {
        this.replaceGroup(updated);
        this.setCategorySelection(group.id, '');
        this.done('Category assigned.');
      },
      error: error => this.failAction(error, 'Category could not be assigned.')
    });
  }

  protected removeCategory(group: NavigationGroup, categoryId: number): void {
    this.action.set(`group-${group.id}-category-remove`);
    this.storefront.removeCategory(group.id, categoryId).subscribe({
      next: () => {
        this.replaceGroup({ ...group, categories: group.categories.filter(item => item.id !== categoryId) });
        this.done('Category removed from the navigation group.');
      },
      error: error => this.failAction(error, 'Category could not be removed.')
    });
  }

  protected moveCategory(group: NavigationGroup, categoryId: number, direction: -1 | 1): void {
    const ids = group.categories.map(item => item.id);
    if (!this.swap(ids, categoryId, direction)) return;
    this.action.set(`group-${group.id}-category-order`);
    this.storefront.reorderCategories(group.id, ids).subscribe({
      next: updated => {
        this.replaceGroup(updated);
        this.done('Category order updated.');
      },
      error: error => this.failAction(error, 'Categories could not be reordered.')
    });
  }

  protected assignProduct(group: NavigationGroup): void {
    const productId = Number(this.productSelections()[group.id]);
    if (!productId) return;
    if (group.featuredProducts.length >= 3) {
      this.showValidation('A navigation group can feature no more than three products.');
      return;
    }
    if (group.featuredProducts.some(product => product.id === productId)) {
      this.showValidation('That product is already featured in this navigation group.');
      return;
    }
    this.action.set(`group-${group.id}-product-add`);
    this.storefront.assignFeaturedProduct(group.id, productId).subscribe({
      next: updated => {
        this.replaceGroup(updated);
        this.setProductSelection(group.id, '');
        this.done('Featured product assigned.');
      },
      error: error => this.failAction(error, 'Featured product could not be assigned.')
    });
  }

  protected removeProduct(group: NavigationGroup, productId: number): void {
    this.action.set(`group-${group.id}-product-remove`);
    this.storefront.removeFeaturedProduct(group.id, productId).subscribe({
      next: () => {
        this.replaceGroup({ ...group, featuredProducts: group.featuredProducts.filter(item => item.id !== productId) });
        this.done('Featured product removed.');
      },
      error: error => this.failAction(error, 'Featured product could not be removed.')
    });
  }

  protected moveProduct(group: NavigationGroup, productId: number, direction: -1 | 1): void {
    const ids = group.featuredProducts.map(item => item.id);
    if (!this.swap(ids, productId, direction)) return;
    this.action.set(`group-${group.id}-product-order`);
    this.storefront.reorderFeaturedProducts(group.id, ids).subscribe({
      next: updated => {
        this.replaceGroup(updated);
        this.done('Featured product order updated.');
      },
      error: error => this.failAction(error, 'Featured products could not be reordered.')
    });
  }

  protected availableCategories(group: NavigationGroup): CategoryOption[] {
    const assigned = new Set(group.categories.map(item => item.id));
    return this.categories().filter(category => !assigned.has(category.id));
  }

  protected availableProducts(group: NavigationGroup): Product[] {
    const assigned = new Set(group.featuredProducts.map(item => item.id));
    return this.products().filter(product => product.active && !assigned.has(product.id));
  }

  protected productDetails(reference: StorefrontProductReference): Product | undefined {
    return this.products().find(product => product.id === reference.id);
  }

  protected categoryPath(categoryId: number | undefined): string {
    if (categoryId == null) return 'Category unavailable';
    return this.categories().find(category => category.id === categoryId)?.path ?? 'Category unavailable';
  }

  protected formatPrice(product: Product | undefined): string {
    if (!product) return 'Price unavailable';
    return new Intl.NumberFormat(undefined, { style: 'currency', currency: product.currency }).format(product.priceCents / 100);
  }

  protected productImage(product: Product | undefined): string {
    return product?.imageUrls?.[0] ?? product?.imageUrl ?? '/product-placeholder.svg';
  }

  protected setGroupLabel(groupId: number, label: string): void {
    this.groupLabels.update(labels => ({ ...labels, [groupId]: label }));
  }

  protected setCategorySelection(groupId: number, categoryId: string): void {
    this.categorySelections.update(selections => ({ ...selections, [groupId]: categoryId }));
  }

  protected setProductSelection(groupId: number, productId: string): void {
    this.productSelections.update(selections => ({ ...selections, [groupId]: productId }));
  }

  private applySettings(settings: StorefrontSettings): void {
    this.settings.set(settings);
    this.heroAlt.set(settings.heroImageAltText ?? '');
    this.heroHeading.set(settings.heroHeading ?? '');
    this.heroSupportingText.set(settings.heroSupportingText ?? '');
  }

  private applyGroups(groups: NavigationGroup[]): void {
    const ordered = [...groups].sort((left, right) => left.displayOrder - right.displayOrder);
    this.groups.set(ordered);
    this.groupLabels.set(Object.fromEntries(ordered.map(group => [group.id, group.label])));
  }

  private replaceGroup(updated: NavigationGroup): void {
    this.groups.update(groups => groups.map(group => group.id === updated.id ? updated : group));
    this.setGroupLabel(updated.id, updated.label);
  }

  private swap(ids: number[], id: number, direction: -1 | 1): boolean {
    const from = ids.indexOf(id);
    const to = from + direction;
    if (from < 0 || to < 0 || to >= ids.length) return false;
    [ids[from], ids[to]] = [ids[to], ids[from]];
    return true;
  }

  private clean(value: string): string | null {
    return value.trim() || null;
  }

  private done(message: string): void {
    this.action.set(null);
    this.messageIsError.set(false);
    this.message.set(message);
  }

  private showValidation(message: string): void {
    this.messageIsError.set(true);
    this.message.set(message);
  }

  private failAction(error: unknown, fallback: string): void {
    this.action.set(null);
    this.fail(error, fallback);
  }

  private fail(error: unknown, fallback: string): void {
    this.messageIsError.set(true);
    const response = error instanceof HttpErrorResponse ? error.error : error;
    this.message.set(
      typeof response === 'object' && response !== null && 'message' in response && typeof response.message === 'string'
        ? response.message
        : fallback
    );
  }
}
