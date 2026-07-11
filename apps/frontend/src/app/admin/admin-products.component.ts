import { Component, OnInit, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AdminAuthService } from './admin-auth.service';
import { AdminImageUploadComponent } from './admin-image-upload.component';
import { AdminProductFormComponent } from './admin-product-form.component';
import { AdminProductListComponent } from './admin-product-list.component';
import { Product, ProductRequest, ProductService } from '../product.service';

type AdminProductsState = 'loading' | 'ready' | 'error';
type AdminActionState = 'idle' | 'saving' | 'success' | 'error';

@Component({
  selector: 'app-admin-products',
  imports: [
    AdminImageUploadComponent,
    AdminProductFormComponent,
    AdminProductListComponent
  ],
  templateUrl: './admin-products.component.html'
})
export class AdminProductsComponent implements OnInit {
  private readonly authService = inject(AdminAuthService);
  private readonly productService = inject(ProductService);
  private readonly router = inject(Router);

  protected readonly products = signal<Product[]>([]);
  protected readonly selectedProduct = signal<Product | null>(null);
  protected readonly productsState = signal<AdminProductsState>('loading');
  protected readonly actionState = signal<AdminActionState>('idle');
  protected readonly message = signal('');
  protected readonly adminUsername = this.authService.getUsername() ?? 'admin';

  ngOnInit(): void {
    this.loadProducts();
  }

  protected loadProducts(): void {
    this.productsState.set('loading');

    this.productService.getAdminProducts().subscribe({
      next: (products) => {
        this.products.set(products);
        this.productsState.set('ready');
      },
      error: (error) => {
        this.products.set([]);
        this.productsState.set('error');
        this.handleAdminError(error, 'Admin products could not be loaded.');
      }
    });
  }

  protected editProduct(product: Product): void {
    this.selectedProduct.set(product);
    this.actionState.set('idle');
    this.message.set('');
  }

  protected newProduct(): void {
    this.selectedProduct.set(null);
    this.actionState.set('idle');
    this.message.set('');
  }

  protected saveProduct(event: { request: ProductRequest; images: File[] }): void {
    const selectedProduct = this.selectedProduct();
    const action = selectedProduct === null
      ? this.productService.createProductWithImages(event.request, event.images)
      : this.productService.updateProduct(selectedProduct.id, event.request);

    this.actionState.set('saving');
    this.message.set('');

    action.subscribe({
      next: (product) => {
        this.selectedProduct.set(product);
        this.actionState.set('success');
        this.message.set(selectedProduct === null ? 'Product created.' : 'Product updated.');
        this.loadProducts();
      },
      error: (error) => {
        this.handleAdminError(error, 'Product could not be saved.');
      }
    });
  }

  protected deleteProduct(product: Product): void {
    if (!globalThis.confirm(`Delete ${product.name}?`)) {
      return;
    }

    this.actionState.set('saving');
    this.message.set('');

    this.productService.deleteProduct(product.id).subscribe({
      next: () => {
        if (this.selectedProduct()?.id === product.id) {
          this.selectedProduct.set(null);
        }
        this.actionState.set('success');
        this.message.set('Product deleted.');
        this.loadProducts();
      },
      error: (error) => {
        this.handleAdminError(error, 'Product could not be deleted.');
      }
    });
  }

  protected handleImageUploaded(product: Product): void {
    this.selectedProduct.set(product);
    this.actionState.set('success');
    this.message.set('Product image updated.');
    this.loadProducts();
  }

  protected removeImage(imageIndex: number): void {
    const product = this.selectedProduct();
    if (product === null) return;
    this.productService.removeProductImage(product.id, imageIndex).subscribe({
      next: (updated) => this.handleImageUploaded(updated),
      error: (error) => this.handleAdminError(error, 'Image could not be removed.')
    });
  }

  protected logout(): void {
    this.authService.logout();
    void this.router.navigate(['/admin/login']);
  }

  private handleAdminError(error: unknown, fallback: string): void {
    if (this.isUnauthorized(error)) {
      this.authService.logout();
      void this.router.navigate(['/admin/login']);
      return;
    }

    this.actionState.set('error');
    this.message.set(this.describeAdminError(error, fallback));
  }

  private isUnauthorized(error: unknown): boolean {
    if (typeof error === 'object' && error !== null && 'status' in error) {
      const status = Number((error as { status: unknown }).status);
      return status === 401 || status === 403;
    }

    return false;
  }

  private describeAdminError(error: unknown, fallback: string): string {
    if (typeof error === 'object' && error !== null && 'status' in error) {
      const status = Number((error as { status: unknown }).status);
      if (status === 409) {
        return 'That slug is already used by another product.';
      }
      if (status === 400) {
        return 'Check name, slug, price, currency, sizes, and image format.';
      }
    }

    return fallback;
  }
}
