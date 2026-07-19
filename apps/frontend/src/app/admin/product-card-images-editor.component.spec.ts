import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { Product, ProductService } from '../product.service';
import { AdminCatalogService, CatalogImage } from './admin-catalog.service';
import { ProductCardImagesEditorComponent } from './product-card-images-editor.component';

describe('ProductCardImagesEditorComponent',()=>{
  it('deletes the selected catalog image by id and reloads the media library',async()=>{
    const image:CatalogImage={id:37,url:'/api/public/uploads/products/photo.jpg',filename:'photo.jpg',sortOrder:0,shared:true,active:true,usages:['Product gallery','Variant: desk-oak']};
    const catalog={
      images:jasmine.createSpy().and.returnValue(of([image])),
      listingImages:jasmine.createSpy().and.returnValue(of({primaryImageId:null,hoverImageId:null})),
      deleteImage:jasmine.createSpy().and.returnValue(of(void 0))
    };
    await TestBed.configureTestingModule({
      imports:[ProductCardImagesEditorComponent],
      providers:[
        {provide:AdminCatalogService,useValue:catalog},
        {provide:ProductService,useValue:{}}
      ]
    }).compileComponents();
    spyOn(globalThis,'confirm').and.returnValue(true);

    const fixture=TestBed.createComponent(ProductCardImagesEditorComponent);
    fixture.componentRef.setInput('product',{id:11} as Product);
    fixture.detectChanges();
    const deleteButton=Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('button')).find(button=>button.textContent?.includes('Delete image'));
    deleteButton?.click();

    expect(globalThis.confirm).toHaveBeenCalledWith('Delete this image everywhere? It will be removed from the product gallery, product cards, and any variants using it.');
    expect(catalog.deleteImage).toHaveBeenCalledOnceWith(11,37);
    expect(catalog.images).toHaveBeenCalledTimes(2);
  });
});
