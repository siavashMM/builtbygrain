import { Routes } from '@angular/router';
import { adminAuthGuard } from './admin/admin-auth.guard';
import { AdminLoginComponent } from './admin/admin-login.component';
import { AdminProductsComponent } from './admin/admin-products.component';
import { ShopHomeComponent } from './shop/shop-home.component';

export const routes: Routes = [
  { path: '', component: ShopHomeComponent },
  { path: 'admin/login', component: AdminLoginComponent },
  {
    path: 'admin/products',
    component: AdminProductsComponent,
    canActivate: [adminAuthGuard]
  },
  { path: '**', redirectTo: '' }
];
