import { Routes } from '@angular/router';
import { adminAuthGuard } from './admin/admin-auth.guard';
import { AdminLoginComponent } from './admin/admin-login.component';
import { AdminProductsComponent } from './admin/admin-products.component';
import { ShopHomeComponent } from './shop/shop-home.component';
import { ProductDetailComponent } from './shop/product-detail.component';
import { CartComponent } from './cart/cart.component';

export const routes: Routes = [
  { path: '', component: ShopHomeComponent },
  { path: 'products/:slug', component: ProductDetailComponent },
  { path: 'cart', component: CartComponent },
  { path: 'admin/login', component: AdminLoginComponent },
  {
    path: 'admin/products',
    component: AdminProductsComponent,
    canActivate: [adminAuthGuard]
  },
  { path: '**', redirectTo: '' }
];
