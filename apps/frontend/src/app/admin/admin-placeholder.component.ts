import { Component, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';

@Component({
  selector: 'app-admin-placeholder',
  imports: [RouterLink],
  template: `
    <section class="admin-route-page admin-placeholder-page">
      <header class="admin-page-heading">
        <div><p class="admin-kicker">Administration</p><h1>{{ title }}</h1><p>{{ description }}</p></div>
      </header>
      <article class="admin-card empty-module-card">
        <span class="empty-module-icon" aria-hidden="true">{{ icon }}</span>
        <h2>{{ title }} workspace</h2>
        <p>This area is part of the new administration structure. It is intentionally not connected to invented data or replacement APIs.</p>
        <p class="module-note">{{ note }}</p>
        <a routerLink="/admin/products" class="admin-primary-link">Continue to product management</a>
      </article>
    </section>
  `
})
export class AdminPlaceholderComponent {
  private readonly route = inject(ActivatedRoute);
  protected readonly title = String(this.route.snapshot.data['title'] ?? 'Workspace');
  protected readonly description = String(this.route.snapshot.data['description'] ?? 'Manage this part of your business.');
  protected readonly icon = String(this.route.snapshot.data['icon'] ?? '◇');
  protected readonly note = String(this.route.snapshot.data['note'] ?? 'Backend support can be connected here when its API is available.');
}
