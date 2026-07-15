import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ProductService } from '../product.service';
import { AdminCatalogService } from './admin-catalog.service';

@Component({
  selector: 'app-admin-dashboard',
  imports: [RouterLink],
  template: `
    <section class="admin-route-page">
      <header class="admin-page-heading dashboard-heading">
        <div><p class="admin-kicker">Overview</p><h1>Good to see you.</h1><p>Review your catalog and continue where attention is needed.</p></div>
        <a routerLink="/admin/products" class="admin-primary-link">Add product</a>
      </header>
      <div class="metric-grid" aria-label="Catalog summary">
        <article class="admin-card metric-card"><span>Products</span><strong>{{ loading() ? '—' : productCount() }}</strong><small>{{ activeCount() }} active</small></article>
        <article class="admin-card metric-card"><span>Categories</span><strong>{{ loading() ? '—' : categoryCount() }}</strong><small>Catalog structure</small></article>
        <article class="admin-card metric-card"><span>Stock alerts</span><strong>{{ loading() ? '—' : outOfStockCount() }}</strong><small>Products marked unavailable</small></article>
      </div>
      <div class="dashboard-grid">
        <article class="admin-card dashboard-panel"><header><div><p class="admin-kicker">Catalog</p><h2>Product management</h2></div><a routerLink="/admin/products">Open products →</a></header><p>Edit product information, media, options, prices, stock, and variant combinations in one focused workspace.</p></article>
        <article class="admin-card dashboard-panel"><header><div><p class="admin-kicker">Structure</p><h2>Categories</h2></div><a routerLink="/admin/categories">Open categories →</a></header><p>Organize the existing catalog tree and control category status without changing the backend model.</p></article>
      </div>
    </section>
  `
})
export class AdminDashboardComponent implements OnInit {
  private readonly products = inject(ProductService);
  private readonly catalog = inject(AdminCatalogService);
  protected readonly loading = signal(true);
  protected readonly productCount = signal(0);
  protected readonly activeCount = signal(0);
  protected readonly categoryCount = signal(0);
  protected readonly outOfStockCount = signal(0);

  ngOnInit(): void {
    let pending = 2;
    const complete = () => { pending -= 1; if (pending === 0) this.loading.set(false); };
    this.products.getAdminProducts().subscribe({
      next: items => { this.productCount.set(items.length); this.activeCount.set(items.filter(item => item.active).length); this.outOfStockCount.set(items.filter(item => !item.inStock).length); complete(); },
      error: complete
    });
    this.catalog.categories().subscribe({ next: items => { this.categoryCount.set(items.length); complete(); }, error: complete });
  }
}
