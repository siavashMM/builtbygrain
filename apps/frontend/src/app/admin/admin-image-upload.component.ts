import { Component, EventEmitter, Input, Output, inject, signal } from '@angular/core';
import { Product, ProductService } from '../product.service';

type UploadState = 'idle' | 'uploading' | 'error';

@Component({
  selector: 'app-admin-image-upload',
  imports: [],
  templateUrl: './admin-image-upload.component.html'
})
export class AdminImageUploadComponent {
  private readonly productService = inject(ProductService);

  @Input() product: Product | null = null;
  @Output() uploaded = new EventEmitter<Product>();
  @Output() removeImage = new EventEmitter<number>();

  protected readonly selectedFiles = signal<File[]>([]);
  protected readonly state = signal<UploadState>('idle');
  protected readonly errorMessage = signal('');

  protected selectFile(event: Event): void {
    const input = event.target as HTMLInputElement;
    const files = Array.from(input.files ?? []);
    if (files.some((file) => file.size > 5 * 1024 * 1024)) {
      this.selectedFiles.set([]);
      this.errorMessage.set('Each image must be 5 MB or smaller.');
      input.value = '';
      return;
    }
    if (files.length + (this.product?.imageUrls.length ?? 0) > 8) {
      this.selectedFiles.set([]);
      this.errorMessage.set('A product can have at most 8 images.');
      input.value = '';
      return;
    }
    this.selectedFiles.set(files);
    this.errorMessage.set('');
    this.state.set('idle');
  }

  protected upload(): void {
    const files = this.selectedFiles();
    if (this.product === null || files.length === 0) {
      return;
    }

    this.state.set('uploading');
    this.errorMessage.set('');

    this.productService.uploadProductImages(this.product.id, files).subscribe({
      next: (product) => {
        this.state.set('idle');
        this.selectedFiles.set([]);
        this.uploaded.emit(product);
      },
      error: () => {
        this.state.set('error');
        this.errorMessage.set('Image upload failed. Use JPEG, PNG, WebP, or GIF under the configured size limit.');
      }
    });
  }
}
