import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { BehaviorSubject, of } from 'rxjs';
import { ProductService } from '../product.service';
import { AdminCatalogService } from './admin-catalog.service';
import { AdminProductsComponent } from './admin-products.component';

describe('AdminProductsComponent',()=>{
  it('opens a new product with the category from the route preselected',async()=>{
    const queryParams=new BehaviorSubject(convertToParamMap({categoryId:'7'}));
    await TestBed.configureTestingModule({
      imports:[AdminProductsComponent],
      providers:[
        provideRouter([]),
        {provide:ActivatedRoute,useValue:{snapshot:{data:{workspace:'products'}},queryParamMap:queryParams.asObservable()}},
        {provide:ProductService,useValue:{getAdminProducts:()=>of([])}},
        {provide:AdminCatalogService,useValue:{categories:()=>of([{id:7,parentId:null,name:'Shelves',slug:'shelves',description:null,imageUrl:null,sortOrder:0,active:true}]),categoryOptions:()=>of([{id:7,name:'Shelves',path:'Office / Shelves',active:true,depth:1}] )}}
      ]
    }).compileComponents();

    const fixture=TestBed.createComponent(AdminProductsComponent);
    fixture.detectChanges();
    const element=fixture.nativeElement as HTMLElement;
    expect(element.querySelector('.product-editor-heading h2')?.textContent).toContain('New product');
    expect(element.querySelector<HTMLInputElement>('input[list="product-category-options"]')?.value).toBe('Office / Shelves');

    queryParams.next(convertToParamMap({}));
    fixture.detectChanges();
    expect(element.querySelector('.product-index-card h2')?.textContent).toContain('All products');
    expect(element.querySelector('.product-editor-heading')).toBeNull();
  });
});
