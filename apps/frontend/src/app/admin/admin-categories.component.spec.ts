import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of } from 'rxjs';
import { Product } from '../product.service';
import { ProductService } from '../product.service';
import { AdminCatalogService, AdminCategoryNode, CategoryOption } from './admin-catalog.service';
import { AdminCategoriesComponent } from './admin-categories.component';

describe('AdminCategoriesComponent',()=>{
  it('keeps products hidden by default and routes add/edit actions through Products',async()=>{
    const category:AdminCategoryNode={id:7,parentId:null,name:'Shelves',slug:'shelves',active:true,position:0,directProductCount:1,children:[]};
    const option:CategoryOption={id:7,name:'Shelves',path:'Shelves',active:true,depth:0};
    const product={id:31,name:'Oak shelf',slug:'oak-shelf',categoryId:7,active:true,imageUrls:[],sizes:[],configuration:{benefits:[],specifications:{},sections:[],faqs:[],options:[],variants:[]}} as unknown as Product;
    const navigate=jasmine.createSpy('navigate').and.resolveTo(true);

    await TestBed.configureTestingModule({
      imports:[AdminCategoriesComponent],
      providers:[
        {provide:AdminCatalogService,useValue:{categoryTree:()=>of([category]),categoryOptions:()=>of([option])}},
        {provide:ProductService,useValue:{getAdminProducts:()=>of([product])}},
        {provide:Router,useValue:{navigate}}
      ]
    }).compileComponents();

    const fixture=TestBed.createComponent(AdminCategoriesComponent);
    fixture.detectChanges();
    const element=fixture.nativeElement as HTMLElement;
    expect(element.querySelector('.category-product-row')).toBeNull();

    element.querySelector<HTMLInputElement>('.show-products-toggle input')!.click();
    fixture.detectChanges();
    expect(element.querySelector('.category-product-row')?.textContent).toContain('Oak shelf');

    element.querySelector<HTMLButtonElement>('.add-product-action')!.click();
    expect(navigate).toHaveBeenCalledWith(['/admin/products'],{queryParams:{categoryId:7}});

    element.querySelector<HTMLButtonElement>('.category-product-row button')!.click();
    expect(navigate).toHaveBeenCalledWith(['/admin/products'],{queryParams:{productId:31}});
  });
});
