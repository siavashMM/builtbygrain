import { Routes } from '@angular/router';
import { adminAuthGuard } from './admin/admin-auth.guard';
import { AdminLoginComponent } from './admin/admin-login.component';
import { AdminProductsComponent, unsavedAdminChangesGuard } from './admin/admin-products.component';
import { ShopHomeComponent } from './shop/shop-home.component';
import { ProductDetailComponent } from './shop/product-detail.component';
import { CartComponent } from './cart/cart.component';
import { AdminShellComponent } from './admin/admin-shell.component';
import { AdminDashboardComponent } from './admin/admin-dashboard.component';
import { AdminPlaceholderComponent } from './admin/admin-placeholder.component';

export const routes: Routes = [
  { path: '', component: ShopHomeComponent },
  { path: 'products/:slug', component: ProductDetailComponent },
  { path: 'cart', component: CartComponent },
  { path: 'admin/login', component: AdminLoginComponent },
  {
    path: 'admin',
    component: AdminShellComponent,
    canActivate: [adminAuthGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      { path: 'dashboard', component: AdminDashboardComponent },
      { path: 'products', component: AdminProductsComponent, canDeactivate: [unsavedAdminChangesGuard], data: { workspace: 'products' } },
      { path: 'categories', component: AdminProductsComponent, canDeactivate: [unsavedAdminChangesGuard], data: { workspace: 'categories' } },
      { path: 'orders', component: AdminPlaceholderComponent, data: { title: 'Orders', icon: '▤', description: 'Track and manage customer orders.', note: 'The current repository has a customer order endpoint but no admin order-management contract yet.' } },
      { path: 'inventory', component: AdminPlaceholderComponent, data: { title: 'Inventory', icon: '▦', description: 'Monitor stock across product variants.', note: 'Variant stock remains editable in Products until a dedicated inventory API is introduced.' } },
      { path: 'customers', component: AdminPlaceholderComponent, data: { title: 'Customers', icon: '○', description: 'Understand and support your customers.' } },
      { path: 'discounts', component: AdminPlaceholderComponent, data: { title: 'Discounts', icon: '%', description: 'Create and organize sales incentives.' } },
      { path: 'content', component: AdminPlaceholderComponent, data: { title: 'Content', icon: '▱', description: 'Manage reusable shop content.' } },
      { path: 'settings', component: AdminPlaceholderComponent, data: { title: 'Settings', icon: '⚙', description: 'Configure your administration workspace.' } }
    ]
  },
  { path: '**', redirectTo: '' }
];
