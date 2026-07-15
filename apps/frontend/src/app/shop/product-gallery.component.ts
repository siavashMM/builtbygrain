import { Component, input, output } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatTooltipModule } from '@angular/material/tooltip';

@Component({
  selector: 'app-product-gallery',
  imports: [MatCardModule, MatTooltipModule],
  template: `
    <div class="editorial-gallery">
      <mat-card class="hero-image-card" appearance="outlined">
        <button type="button" class="gallery-image-button" (click)="zoom.emit()" matTooltip="View larger image" [attr.aria-label]="'Open larger image of ' + productName()">
          <img [src]="images()[selectedIndex()]" [alt]="productName() + ', image ' + (selectedIndex() + 1)" width="900" height="1100">
          <span class="zoom-hint" aria-hidden="true">↗</span>
        </button>
      </mat-card>
      @if (images().length > 1) {
        <div class="supporting-images" aria-label="More product images">
          @for (image of images(); track image; let index = $index) {
            @if (index !== selectedIndex()) {
              <button type="button" class="supporting-image" [class.active]="index === selectedIndex()" (click)="select.emit(index)" [attr.aria-label]="'Show image ' + (index + 1)" [attr.aria-pressed]="index === selectedIndex()">
                <img [src]="image" alt="" width="480" height="580" loading="lazy">
              </button>
            }
          }
        </div>
      }
    </div>
  `
})
export class ProductGalleryComponent {
  readonly images = input.required<string[]>(); readonly productName = input.required<string>(); readonly selectedIndex = input(0);
  readonly select = output<number>(); readonly zoom = output<void>();
}
