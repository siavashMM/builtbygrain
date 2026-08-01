import { Routes } from '@angular/router';
import { unsavedAdminChangesGuard } from './admin-products.component';

export const ADMIN_ROUTES: Routes = [{
  path: '',
  loadComponent: () => import('./admin-shell.component').then(module => module.AdminShellComponent),
  children: [
    { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
    { path: 'dashboard', loadComponent: () => import('./admin-dashboard.component').then(module => module.AdminDashboardComponent) },
    {
      path: 'products',
      loadComponent: () => import('./admin-products.component').then(module => module.AdminProductsComponent),
      canDeactivate: [unsavedAdminChangesGuard],
      data: { workspace: 'products' }
    },
    { path: 'categories', loadComponent: () => import('./admin-categories.component').then(module => module.AdminCategoriesComponent) },
    { path: 'storefront', loadComponent: () => import('./admin-storefront.component').then(module => module.AdminStorefrontComponent) },
    { path: 'settings', loadComponent: () => import('./admin-settings.component').then(module => module.AdminSettingsComponent) },
    ...['orders', 'inventory', 'customers', 'discounts', 'content'].map(path => ({
      path,
      loadComponent: () => import('./admin-placeholder.component').then(module => module.AdminPlaceholderComponent),
      data: placeholderData(path)
    }))
  ]
}];

function placeholderData(path: string): { title: string; icon: string; description: string; note?: string } {
  const values: Record<string, { title: string; icon: string; description: string; note?: string }> = {
    orders: { title: 'Orders', icon: '▤', description: 'Track and manage customer orders.' },
    inventory: { title: 'Inventory', icon: '▦', description: 'Monitor stock across product variants.' },
    customers: { title: 'Customers', icon: '○', description: 'Understand and support your customers.' },
    discounts: { title: 'Discounts', icon: '%', description: 'Create and organize sales incentives.' },
    content: { title: 'Content', icon: '▱', description: 'Manage reusable shop content.' },
    settings: { title: 'Settings', icon: '⚙', description: 'Configure your administration workspace.' }
  };
  return values[path];
}
