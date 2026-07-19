import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink } from '@angular/router';
import { Subject, Subscription, catchError, distinctUntilChanged, filter, map, merge, of, startWith, switchMap, tap } from 'rxjs';
import { ProductCardComponent } from './product-card.component';
import { ShopCatalogService, ShopCategoryPage } from './shop-catalog.service';
import { ShopNavigationComponent } from './shop-navigation.component';

type CategoryPageState = 'loading' | 'ready' | 'not-found' | 'error';

@Component({
  selector: 'app-shop-category-page',
  imports: [RouterLink, ShopNavigationComponent, ProductCardComponent],
  template: `
    <main class="storefront-home storefront-category-page">
      <app-shop-navigation />

      @if (state() === 'loading') {
        <section class="category-page-state" aria-busy="true" aria-label="Loading category">
          <div class="product-skeleton"><span></span><i></i><i></i></div>
        </section>
      } @else if (state() === 'not-found') {
        <section class="category-page-state">
          <p class="home-eyebrow">Category not found</p>
          <h1>This collection is no longer here.</h1>
          <a routerLink="/">Return to the shop</a>
        </section>
      } @else if (state() === 'error') {
        <section class="category-page-state" role="alert">
          <p class="home-eyebrow">Unable to load</p>
          <h1>Our shelves are momentarily out of reach.</h1>
          <button type="button" (click)="reload()">Try again</button>
        </section>
      } @else if (page(); as current) {
        <nav class="category-breadcrumbs" aria-label="Breadcrumb">
          <a routerLink="/">Home</a><span aria-hidden="true">/</span>
          @for (crumb of current.breadcrumbs; track crumb.id; let last = $last) {
            @if (last) { <span aria-current="page">{{ crumb.name }}</span> }
            @else { <a [routerLink]="crumb.path">{{ crumb.name }}</a><span aria-hidden="true">/</span> }
          }
        </nav>

        <header class="category-page-hero" [class.has-image]="!!current.category.imageUrl">
          <div>
            <p class="home-eyebrow">Collection</p>
            <h1>{{ current.category.name }}</h1>
            @if (current.category.description) { <p>{{ current.category.description }}</p> }
          </div>
          @if (current.category.imageUrl) {
            <img [src]="current.category.imageUrl" [alt]="current.category.name + ' collection'">
          }
        </header>

        <section class="category-products" aria-labelledby="category-products-title">
          <header>
            <h2 id="category-products-title">Pieces in this collection</h2>
            <span>{{ current.products.length }} {{ current.products.length === 1 ? 'piece' : 'pieces' }}</span>
          </header>
          @if (current.products.length === 0) {
            <div class="home-state-panel category-products-empty">
              <h3>No pieces are available yet.</h3>
              <p>New work is taking shape. Please visit this collection again soon.</p>
            </div>
          } @else {
            <div class="home-product-grid">
              @for (product of current.products; track product.id) { <app-product-card [product]="product" /> }
            </div>
          }
        </section>
      }
    </main>
  `
})
export class ShopCategoryPageComponent implements OnInit, OnDestroy {
  private readonly catalog = inject(ShopCatalogService);
  private readonly router = inject(Router);
  private readonly reloadRequest = new Subject<void>();
  private subscription?: Subscription;
  protected readonly state = signal<CategoryPageState>('loading');
  protected readonly page = signal<ShopCategoryPage | null>(null);

  ngOnInit(): void {
    this.subscription = merge(
      this.router.events.pipe(filter(event => event instanceof NavigationEnd)),
      this.reloadRequest
    ).pipe(
      startWith(null),
      map(() => this.categoryPath()),
      distinctUntilChanged(),
      tap(() => { this.state.set('loading'); this.page.set(null); }),
      switchMap(path => this.catalog.categoryPage(path).pipe(
        map(page => ({ page, error: null as HttpErrorResponse | null })),
        catchError((error: HttpErrorResponse) => of({ page: null, error }))
      ))
    ).subscribe(result => {
      if (result.page) {
        this.page.set(result.page);
        this.state.set('ready');
      } else {
        this.state.set(result.error?.status === 404 ? 'not-found' : 'error');
      }
    });
  }

  ngOnDestroy(): void { this.subscription?.unsubscribe(); }
  protected reload(): void { this.reloadRequest.next(); }
  private categoryPath(): string { return this.router.url.split(/[?#]/, 1)[0].replace(/^\/category\/?/, ''); }
}
