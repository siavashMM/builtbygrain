import { Component, OnInit, ViewChild, inject, signal } from '@angular/core';
import { ActivatedRoute, CanDeactivateFn, ParamMap, Router } from '@angular/router';
import { AdminProductFormComponent } from './admin-product-form.component';
import { Product, ProductRequest, ProductService } from '../product.service';
import { CatalogTreeComponent } from './catalog-tree.component';
import { AdminCatalogService, CatalogTreeNode, CategoryDetails, CategoryOption, CategoryRequest } from './admin-catalog.service';
import { CategoryEditorComponent } from './category-editor.component';
import { AdminProductListComponent } from './admin-product-list.component';

type AdminProductsState = 'loading' | 'ready' | 'error';
type AdminActionState = 'idle' | 'saving' | 'success' | 'error';

@Component({
  selector: 'app-admin-products',
  imports: [
    AdminProductFormComponent,
    CategoryEditorComponent,
    AdminProductListComponent
  ],
  templateUrl: './admin-products.component.html'
})
export class AdminProductsComponent implements OnInit {
  private readonly productService = inject(ProductService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly catalog = inject(AdminCatalogService);
  @ViewChild(CatalogTreeComponent) protected tree?:CatalogTreeComponent;
  @ViewChild(AdminProductFormComponent) private productForm?: AdminProductFormComponent;

  protected readonly products = signal<Product[]>([]);
  protected readonly selectedProduct = signal<Product | null>(null);
  protected readonly selectedNode=signal<CatalogTreeNode|null>(null);
  protected readonly selectedCategory=signal<CategoryDetails|null>(null);
  protected readonly categories=signal<CategoryDetails[]>([]);
  protected readonly categoryOptions=signal<CategoryOption[]>([]);
  protected readonly editorMode=signal<'empty'|'category'|'product'>('empty');
  protected readonly creatingCategory=signal(false);
  protected readonly categoryParentId=signal<number|null>(null);
  protected readonly productCategoryId=signal<number|null>(null);
  protected readonly selectedVariantId=signal<number|null>(null);
  protected readonly productsState = signal<AdminProductsState>('loading');
  protected readonly actionState = signal<AdminActionState>('idle');
  protected readonly message = signal('');
  protected readonly workspace = this.route.snapshot.data['workspace'] === 'categories' ? 'categories' : 'products';
  private requestedCategoryId:number|null=null;
  private requestedProductId:number|null=null;
  private requestedNewProduct=false;

  ngOnInit(): void {
    this.route.queryParamMap.subscribe(params=>this.applyRouteState(params));
    this.loadProducts();
    this.loadCategories();
    this.catalog.categoryOptions().subscribe({next:items=>this.categoryOptions.set(items)});
  }

  protected loadProducts(): void {
    this.productsState.set('loading');

    this.productService.getAdminProducts().subscribe({
      next: (products) => {
        this.products.set(products);
        this.productsState.set('ready');
        this.applyProductRoute();
      },
      error: (error) => {
        this.products.set([]);
        this.productsState.set('error');
        this.handleAdminError(error, 'Admin products could not be loaded.');
      }
    });
  }

  protected editProduct(product: Product): void {
    void this.router.navigate(['/admin/products'], { queryParams: { productId: product.id } });
  }

  protected newProduct(): void {
    void this.router.navigate(['/admin/products'], { queryParams: { new: 'true' } });
  }

  protected selectCatalogNode(node: CatalogTreeNode): void {
    this.selectedNode.set(node);
    if(node.nodeType==='CATEGORY'){const id=this.nodeId(node);this.catalog.category(id).subscribe({next:c=>{this.selectedCategory.set(c);this.creatingCategory.set(false);this.editorMode.set('category');}});return;}
    if (node.nodeType === 'PRODUCT') {
      this.selectedVariantId.set(null);
      const id = Number(node.id.split(':')[1]);
      const product = this.products().find(item => item.id === id);
      if (product) this.editProduct(product);
    } else if (node.nodeType === 'VARIANT' && node.parentId) {
      this.selectedVariantId.set(this.nodeId(node));
      const id = Number(node.parentId.split(':')[1]);
      const product = this.products().find(item => item.id === id);
      if (product) void this.router.navigate(['/admin/products'], { queryParams: { productId: product.id, variantId: this.nodeId(node) } });
    }
  }

  protected handleTreeAction(event:{action:string;node:CatalogTreeNode|null}):void{
    const node=event.node;
    if(event.action==='add-root'){this.openCategoryCreate(null);return;}
    if(!node)return;
    if(event.action==='add-child'&&node.nodeType==='CATEGORY'){this.openCategoryCreate(this.nodeId(node));return;}
    if(event.action==='add-product'&&node.nodeType==='CATEGORY'){void this.router.navigate(['/admin/products'],{queryParams:{categoryId:this.nodeId(node)}});return;}
    if(event.action==='rename'){this.selectCatalogNode(node);return;}
    if(event.action==='move'){this.moveSelected(node);return;}
    if(event.action==='toggle-status'){this.toggleStatus(node);return;}
    if(event.action==='delete'&&node.nodeType==='CATEGORY')this.deleteSelectedCategory();
    if(event.action==='archive'&&node.nodeType==='PRODUCT'){const p=this.products().find(x=>x.id===this.nodeId(node));if(p)this.productService.deactivateProduct(p.id).subscribe({next:updated=>{this.handleProductChanged(updated,'Product archived.');}});}
  }

  protected saveCategory(request:CategoryRequest):void{this.actionState.set('saving');const current=this.creatingCategory()?null:this.selectedCategory();const action=current?this.catalog.updateCategory(current.id,request):this.catalog.createCategory(request);action.subscribe({next:c=>{this.selectedCategory.set(c);this.creatingCategory.set(false);this.actionState.set('success');this.message.set(current?'Category updated.':'Category created.');this.loadCategories();this.tree?.reload();},error:e=>this.handleAdminError(e,'Category could not be saved.')});}
  protected cancelCategory():void{if(this.selectedCategory()){this.creatingCategory.set(false);}else this.editorMode.set('empty');}
  protected deleteSelectedCategory():void{const c=this.selectedCategory();if(!c||!globalThis.confirm(`Delete ${c.name}? Categories with products cannot be deleted.`))return;this.catalog.deleteCategory(c.id).subscribe({next:()=>{this.selectedCategory.set(null);this.editorMode.set('empty');this.message.set('Category deleted.');this.loadCategories();this.tree?.reload();},error:e=>this.handleAdminError(e,'Category contains products or child categories. Deactivate it instead.')});}

  protected saveProduct(event: { request: ProductRequest; images: File[] }): void {
    const selectedProduct = this.selectedProduct();
    const action = selectedProduct === null
      ? event.images.length > 0
        ? this.productService.createProductWithImages(event.request, event.images)
        : this.productService.createProduct(event.request)
      : this.productService.updateProduct(selectedProduct.id, event.request);

    this.actionState.set('saving');
    this.message.set('');

    action.subscribe({
      next: (product) => {
        this.openProduct(product);
        this.actionState.set('success');
        this.message.set(selectedProduct === null ? 'Product created.' : 'Product updated.');
        void this.router.navigate(['/admin/products'], { queryParams: { productId: product.id }, replaceUrl: selectedProduct === null });
        this.loadProducts();
        this.tree?.reload();
      },
      error: (error) => {
        this.handleAdminError(error, 'Product could not be saved.');
      }
    });
  }

  protected deleteProduct(product: Product): void {
    if (!globalThis.confirm(`Delete ${product.name}?`)) {
      return;
    }

    this.actionState.set('saving');
    this.message.set('');

    this.productService.deleteProduct(product.id).subscribe({
      next: () => {
        if (this.selectedProduct()?.id === product.id) {
          this.selectedProduct.set(null);
        }
        this.actionState.set('success');
        this.message.set('Product deleted.');
        this.loadProducts();
      },
      error: (error) => {
        this.handleAdminError(error, 'Product could not be deleted.');
      }
    });
  }

  protected handleImageUploaded(product: Product): void {
    this.selectedProduct.set(product);
    this.actionState.set('success');
    this.message.set('Product image updated.');
    this.loadProducts();
  }

  protected removeImage(imageIndex: number): void {
    const product = this.selectedProduct();
    if (product === null) return;
    this.productService.removeProductImage(product.id, imageIndex).subscribe({
      next: (updated) => this.handleImageUploaded(updated),
      error: (error) => this.handleAdminError(error, 'Image could not be removed.')
    });
  }
  protected duplicateCurrent():void{const p=this.selectedProduct();if(!p)return;this.productService.duplicateProduct(p.id).subscribe({next:copy=>{this.handleProductChanged(copy,'Product duplicated.');this.editProduct(copy);}});}
  protected archiveCurrent():void{const p=this.selectedProduct();if(!p||!confirm(`Archive ${p.name}?`))return;this.productService.deactivateProduct(p.id).subscribe({next:u=>this.handleProductChanged(u,'Product archived.')});}
  protected currentCategoryName():string{return this.selectedProduct()?.categoryName??this.categories().find(c=>c.id===this.productCategoryId())?.name??'';}

  protected resetCatalog(): void {
    const confirmation = globalThis.prompt(
      'This permanently deletes every product, variant, size, color, and category. Uploaded files stay on disk.\n\nType RESET CATALOG to continue:'
    );
    if (confirmation !== 'RESET CATALOG') {
      if (confirmation !== null) {
        this.actionState.set('error');
        this.message.set('Catalog reset cancelled: the confirmation text did not match.');
      }
      return;
    }

    this.actionState.set('saving');
    this.message.set('Resetting catalog…');
    this.catalog.resetCatalog(confirmation).subscribe({
      next: result => {
        this.products.set([]);
        this.selectedProduct.set(null);
        this.selectedNode.set(null);
        this.selectedCategory.set(null);
        this.categories.set([]);
        this.selectedVariantId.set(null);
        this.productCategoryId.set(null);
        this.creatingCategory.set(false);
        this.editorMode.set('empty');
        this.productsState.set('ready');
        this.actionState.set('success');
        this.message.set(
          `Catalog reset: ${result.productsDeleted} products, ${result.variantsDeleted} variants, and ${result.categoriesDeleted} categories deleted. Create a root category to begin again.`
        );
        this.tree?.reload();
      },
      error: error => this.handleAdminError(error, 'Catalog could not be reset.')
    });
  }

  public hasUnsavedChanges(): boolean {
    return this.productForm?.hasUnsavedChanges() ?? false;
  }

  private handleAdminError(error: unknown, fallback: string): void {
    if (this.requiresAdminLogin(error)) {
      void this.router.navigate(['/admin/login'], {
        queryParams: { reason: 'admin-required' }
      });
      return;
    }

    this.actionState.set('error');
    this.message.set(this.describeAdminError(error, fallback));
  }

  private requiresAdminLogin(error: unknown): boolean {
    if (typeof error === 'object' && error !== null && 'status' in error) {
      const status = Number((error as { status: unknown }).status);
      return status === 401 || status === 403;
    }

    return false;
  }

  private describeAdminError(error: unknown, fallback: string): string {
    if (typeof error === 'object' && error !== null && 'status' in error) {
      const status = Number((error as { status: unknown }).status);
      if (status === 409) {
        return 'That slug is already used by another product.';
      }
      if (status === 400) {
        return 'Check name, slug, price, currency, sizes, and image format.';
      }
    }

    return fallback;
  }
  private loadCategories():void{this.catalog.categories().subscribe({next:items=>this.categories.set(items)});}
  private openCategoryCreate(parentId:number|null):void{this.selectedCategory.set(null);this.categoryParentId.set(parentId);this.creatingCategory.set(true);this.editorMode.set('category');}
  private nodeId(node:CatalogTreeNode):number{return Number(node.id.split(':')[1]);}
  private moveSelected(node:CatalogTreeNode):void{const choices=this.categories().filter(c=>c.id!==this.nodeId(node));const label=choices.map(c=>`${c.id}: ${c.name}`).join('\n');const value=globalThis.prompt(`Move to category ID (blank for top level):\n${label}`);if(value===null)return;const target=value.trim()?Number(value):null;if(node.nodeType==='CATEGORY')this.catalog.moveCategory(this.nodeId(node),target,0).subscribe({next:()=>{this.message.set('Category moved.');this.tree?.reload();this.loadCategories();},error:e=>this.handleAdminError(e,'Category could not be moved.')});else if(node.nodeType==='PRODUCT'&&target)this.productService.moveProduct(this.nodeId(node),target).subscribe({next:p=>this.handleProductChanged(p,'Product moved.'),error:e=>this.handleAdminError(e,'Product could not be moved.')});}
  private toggleStatus(node:CatalogTreeNode):void{const active=node.status==='ACTIVE';if(node.nodeType==='CATEGORY')this.catalog.setCategoryStatus(this.nodeId(node),!active).subscribe({next:c=>{this.selectedCategory.set(c);this.message.set(`Category ${c.active?'activated':'deactivated'}.`);this.tree?.reload();},error:e=>this.handleAdminError(e,'Status could not be changed.')});else{const p=this.products().find(x=>x.id===this.nodeId(node));if(!p)return;(p.active?this.productService.deactivateProduct(p.id):this.productService.activateProduct(p.id)).subscribe({next:u=>this.handleProductChanged(u,`Product ${u.active?'activated':'archived'}.`)});}}
  private handleProductChanged(product:Product,message:string):void{this.selectedProduct.set(product);this.message.set(message);this.loadProducts();this.tree?.reload();}
  private applyRouteState(params:ParamMap):void{
    this.requestedCategoryId=this.numberQueryParam(params,'categoryId');
    this.requestedProductId=this.numberQueryParam(params,'productId');
    this.requestedNewProduct=params.get('new')==='true';
    this.selectedVariantId.set(this.numberQueryParam(params,'variantId'));
    if(this.requestedProductId!==null){this.applyProductRoute();return;}
    if(this.requestedCategoryId!==null||this.requestedNewProduct){
      this.selectedProduct.set(null);
      this.productCategoryId.set(this.requestedCategoryId??this.categories()[0]?.id??null);
      this.editorMode.set('product');
      this.actionState.set('idle');
      this.message.set('');
      return;
    }
    if(this.workspace==='products'){
      this.selectedProduct.set(null);
      this.productCategoryId.set(null);
      this.editorMode.set('empty');
      this.actionState.set('idle');
      this.message.set('');
    }
  }
  private applyProductRoute():void{if(this.requestedProductId===null)return;const product=this.products().find(value=>value.id===this.requestedProductId);if(product)this.openProduct(product);}
  private openProduct(product:Product):void{this.selectedProduct.set(product);this.productCategoryId.set(product.categoryId);this.editorMode.set('product');this.actionState.set('idle');this.message.set('');}
  private numberQueryParam(params:ParamMap,name:string):number|null{const raw=params.get(name);if(raw===null||raw.trim()==='')return null;const value=Number(raw);return Number.isSafeInteger(value)&&value>0?value:null;}
}

export const unsavedAdminChangesGuard: CanDeactivateFn<AdminProductsComponent> = component =>
  !component.hasUnsavedChanges() || globalThis.confirm('Discard your unsaved product changes and leave this page?');
