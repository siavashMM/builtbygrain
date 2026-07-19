import { Component, OnInit, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AdminCatalogService, AdminCategoryNode, CategoryOption } from './admin-catalog.service';
import { Product, ProductService } from '../product.service';

interface VisibleCategory { node:AdminCategoryNode; depth:number; siblingIndex:number; siblingCount:number; }

@Component({selector:'app-admin-categories',templateUrl:'./admin-categories.component.html'})
export class AdminCategoriesComponent implements OnInit {
  private readonly catalog=inject(AdminCatalogService);
  private readonly productsService=inject(ProductService);
  private readonly router=inject(Router);
  protected readonly tree=signal<AdminCategoryNode[]>([]);
  protected readonly options=signal<CategoryOption[]>([]);
  protected readonly products=signal<Product[]>([]);
  protected readonly showProducts=signal(false);
  protected readonly expanded=signal<Set<number>>(new Set());
  protected readonly query=signal('');
  protected readonly state=signal<'loading'|'ready'|'error'>('loading');
  protected readonly message=signal('');
  protected readonly error=signal(false);
  protected readonly creatingParentId=signal<number|null|undefined>(undefined);
  protected readonly renamingId=signal<number|null>(null);
  protected readonly movingId=signal<number|null>(null);

  ngOnInit():void{this.reload();this.productsService.getAdminProducts().subscribe({next:products=>this.products.set(products),error:()=>this.products.set([])});}
  protected reload(highlightId?:number):void{this.state.set('loading');this.catalog.categoryTree().subscribe({next:tree=>{this.tree.set(tree);this.state.set('ready');if(highlightId!=null)this.expandAncestors(highlightId);},error:()=>this.state.set('error')});this.catalog.categoryOptions().subscribe({next:value=>this.options.set(value)});}
  protected visible():VisibleCategory[]{const result:VisibleCategory[]=[];const term=this.query().trim().toLocaleLowerCase();const matches=(node:AdminCategoryNode):boolean=>!term||node.name.toLocaleLowerCase().includes(term)||this.path(node.id).toLocaleLowerCase().includes(term)||node.children.some(matches);const visit=(nodes:AdminCategoryNode[],depth:number)=>nodes.forEach((node,index)=>{if(!matches(node))return;result.push({node,depth,siblingIndex:index,siblingCount:nodes.length});if(term||this.expanded().has(node.id))visit(node.children,depth+1);});visit(this.tree(),0);return result;}
  protected toggle(node:AdminCategoryNode):void{this.expanded.update(value=>{const next=new Set(value);next.has(node.id)?next.delete(node.id):next.add(node.id);return next;});}
  protected beginCreate(parentId:number|null):void{this.creatingParentId.set(parentId);if(parentId!=null)this.expanded.update(value=>new Set([...value,parentId]));this.clearActions();this.creatingParentId.set(parentId);}
  protected saveCreate(input:HTMLInputElement):void{const name=input.value.trim();if(!name){input.focus();return;}const parentId=this.creatingParentId()??null;this.catalog.createSimpleCategory(name,parentId).subscribe({next:category=>{this.creatingParentId.set(undefined);this.notify('Category created.');this.reload(category.id);},error:e=>this.fail(e,'Category could not be created.')});}
  protected beginRename(node:AdminCategoryNode):void{this.clearActions();this.renamingId.set(node.id);}
  protected saveRename(node:AdminCategoryNode,input:HTMLInputElement):void{const name=input.value.trim();if(!name){input.focus();return;}this.catalog.renameCategory(node.id,name).subscribe({next:()=>{this.renamingId.set(null);this.notify('Category renamed. Its published URL remains stable.');this.reload(node.id);},error:e=>this.fail(e,'Category could not be renamed.')});}
  protected beginMove(node:AdminCategoryNode):void{this.clearActions();this.movingId.set(node.id);}
  protected saveMove(node:AdminCategoryNode,select:HTMLSelectElement):void{const parentId=select.value===''?null:Number(select.value);this.catalog.reparentCategory(node.id,parentId).subscribe({next:()=>{this.movingId.set(null);this.notify('Category moved.');this.reload(node.id);},error:e=>this.fail(e,'Category cannot be moved there.')});}
  protected movePosition(item:VisibleCategory,direction:-1|1):void{this.catalog.positionCategory(item.node.id,item.siblingIndex+direction).subscribe({next:()=>{this.notify('Category order updated.');this.reload(item.node.id);},error:e=>this.fail(e,'Category could not be reordered.')});}
  protected toggleStatus(node:AdminCategoryNode):void{this.catalog.setCategoryStatus(node.id,!node.active).subscribe({next:()=>{this.notify(`Category ${node.active?'deactivated':'activated'}.`);this.reload(node.id);},error:e=>this.fail(e,'Category status could not be changed.')});}
  protected remove(node:AdminCategoryNode):void{if(!globalThis.confirm(`Delete “${node.name}”? Only empty categories can be deleted.`))return;this.catalog.deleteCategory(node.id).subscribe({next:()=>{this.notify('Category deleted.');this.reload();},error:e=>this.fail(e,'This category is not empty. Move its children and assigned products first.')});}
  protected cancel():void{this.clearActions();}
  protected allowedParents(node:AdminCategoryNode):CategoryOption[]{const blocked=new Set<number>([node.id]);const find=(nodes:AdminCategoryNode[]):AdminCategoryNode|undefined=>{for(const value of nodes){if(value.id===node.id)return value;const child=find(value.children);if(child)return child;}return undefined;};const add=(value:AdminCategoryNode|undefined)=>{value?.children.forEach(child=>{blocked.add(child.id);add(child);});};add(find(this.tree()));return this.options().filter(option=>!blocked.has(option.id));}
  protected path(id:number):string{return this.options().find(option=>option.id===id)?.path??'';}
  protected productsFor(categoryId:number):Product[]{return this.products().filter(product=>product.categoryId===categoryId);}
  protected toggleProducts():void{this.showProducts.update(value=>!value);}
  protected addProduct(categoryId:number):void{void this.router.navigate(['/admin/products'],{queryParams:{categoryId}});}
  protected editProduct(productId:number):void{void this.router.navigate(['/admin/products'],{queryParams:{productId}});}
  protected setQuery(value:string):void{this.query.set(value);}
  private expandAncestors(id:number):void{const option=this.options().find(value=>value.id===id);if(!option)return;const names=option.path.split(' / ');const ids=this.options().filter(value=>names.includes(value.name)&&option.path.startsWith(value.path)).map(value=>value.id);this.expanded.update(value=>new Set([...value,...ids]));}
  private clearActions():void{this.creatingParentId.set(undefined);this.renamingId.set(null);this.movingId.set(null);}
  private notify(message:string):void{this.message.set(message);this.error.set(false);}
  private fail(response:unknown,fallback:string):void{this.error.set(true);this.message.set(typeof response==='object'&&response!==null&&'error'in response&&typeof (response as {error?:{detail?:string;message?:string}}).error==='object'?((response as {error:{detail?:string;message?:string}}).error.detail??(response as {error:{detail?:string;message?:string}}).error.message??fallback):fallback);}
}
