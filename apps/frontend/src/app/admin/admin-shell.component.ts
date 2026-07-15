import { Component, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AdminAuthService } from './admin-auth.service';

interface AdminNavItem {
  label: string;
  route: string;
  icon: string;
}

@Component({
  selector: 'app-admin-shell',
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
  templateUrl: './admin-shell.component.html'
})
export class AdminShellComponent {
  private readonly auth = inject(AdminAuthService);
  private readonly router = inject(Router);

  protected readonly mobileNavigationOpen = signal(false);
  protected readonly username = this.auth.getUsername() ?? 'Admin';
  protected readonly primaryNavigation: AdminNavItem[] = [
    { label: 'Dashboard', route: '/admin/dashboard', icon: '⌂' },
    { label: 'Orders', route: '/admin/orders', icon: '▤' },
    { label: 'Products', route: '/admin/products', icon: '◇' },
    { label: 'Categories', route: '/admin/categories', icon: '⊞' },
    { label: 'Inventory', route: '/admin/inventory', icon: '▦' },
    { label: 'Customers', route: '/admin/customers', icon: '○' },
    { label: 'Discounts', route: '/admin/discounts', icon: '%' },
    { label: 'Content', route: '/admin/content', icon: '▱' }
  ];

  protected closeMobileNavigation(): void {
    this.mobileNavigationOpen.set(false);
  }

  protected signOut(): void {
    this.auth.logout();
    void this.router.navigate(['/admin/login']);
  }
}
