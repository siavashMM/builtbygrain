import { Routes } from '@angular/router';
import { adminAuthGuard } from './admin/admin-auth.guard';
import { accountGuard } from './account/account.guard';

export const routes: Routes = [
  { path: '', loadComponent: () => import('./shop/shop-home.component').then(module => module.ShopHomeComponent) },
  { path: 'category', children: [{
    path: '**',
    loadComponent: () => import('./shop/shop-category-page.component').then(module => module.ShopCategoryPageComponent)
  }] },
  {
    path: 'products/:slug',
    loadComponent: () => import('./shop/product-detail.component').then(module => module.ProductDetailComponent)
  },
  { path: 'cart', loadComponent: () => import('./cart/cart.component').then(module => module.CartComponent) },
  {
    path: 'checkout/account',
    loadComponent: () => import('./checkout/checkout-account.component').then(module => module.CheckoutAccountComponent)
  },
  {
    path: 'checkout/delivery',
    loadComponent: () => import('./checkout/checkout-delivery.component').then(module => module.CheckoutDeliveryComponent)
  },
  {
    path: 'checkout/payment',
    loadComponent: () => import('./checkout/checkout-payment.component').then(module => module.CheckoutPaymentComponent)
  },
  {
    path: 'account/sign-in',
    data: { mode: 'sign-in' },
    loadComponent: () => import('./account/account-auth.component').then(module => module.AccountAuthComponent)
  },
  {
    path: 'account/register',
    data: { mode: 'register' },
    loadComponent: () => import('./account/account-auth.component').then(module => module.AccountAuthComponent)
  },
  {
    path: 'account/forgot-password',
    data: { mode: 'forgot-password' },
    loadComponent: () => import('./account/account-auth.component').then(module => module.AccountAuthComponent)
  },
  {
    path: 'account/reset-password',
    data: { mode: 'reset-password' },
    loadComponent: () => import('./account/account-auth.component').then(module => module.AccountAuthComponent)
  },
  {
    path: 'account',
    canActivate: [accountGuard],
    children: [
      {
        path: '',
        pathMatch: 'full',
        data: { view: 'overview' },
        loadComponent: () => import('./account/account.component').then(module => module.AccountComponent)
      },
      ...['details', 'addresses', 'security', 'orders', 'returns', 'claims'].map(path => ({
        path,
        data: { view: path },
        loadComponent: () => import('./account/account.component').then(module => module.AccountComponent)
      }))
    ]
  },
  {
    path: 'admin/login',
    loadComponent: () => import('./admin/admin-login.component').then(module => module.AdminLoginComponent)
  },
  {
    path: 'admin',
    canActivate: [adminAuthGuard],
    loadChildren: () => import('./admin/admin.routes').then(module => module.ADMIN_ROUTES)
  },
  { path: '**', redirectTo: '' }
];
