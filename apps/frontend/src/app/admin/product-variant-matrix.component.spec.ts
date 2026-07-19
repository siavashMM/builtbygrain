import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { ProductService } from '../product.service';
import { AdminCatalogService, CatalogImage, CatalogVariant } from './admin-catalog.service';
import { ProductVariantMatrixComponent } from './product-variant-matrix.component';

describe('ProductVariantMatrixComponent',()=>{
  it('changes the selected variant photo and marks the chosen tile',async()=>{
    const current=variant('/old.jpg');
    const updated=variant('/new.jpg');
    const images:CatalogImage[]=[image(11,'/old.jpg'),image(12,'/new.jpg')];
    const assignImage=jasmine.createSpy('assignImage').and.returnValue(of(updated));
    await TestBed.configureTestingModule({
      imports:[ProductVariantMatrixComponent],
      providers:[
        {provide:AdminCatalogService,useValue:{variants:()=>of([current]),images:()=>of(images),options:()=>of([{values:[{id:3,active:true}]}]),assignImage}},
        {provide:ProductService,useValue:{}}
      ]
    }).compileComponents();

    const fixture=TestBed.createComponent(ProductVariantMatrixComponent);
    fixture.componentRef.setInput('productId',5);
    fixture.detectChanges();
    fixture.componentInstance.imageVariant.set(current);
    fixture.detectChanges();

    const tiles=(fixture.nativeElement as HTMLElement).querySelectorAll<HTMLButtonElement>('.photo-choice');
    expect(tiles[0].classList).toContain('selected');
    expect((fixture.nativeElement as HTMLElement).textContent).not.toContain('Use this photo');
    tiles[1].click();
    fixture.detectChanges();

    expect(assignImage).toHaveBeenCalledWith(5,21,12,0,true);
    expect(tiles[1].classList).toContain('selected');
    expect((fixture.nativeElement as HTMLElement).querySelector('.photo-dialog-message')?.textContent).toContain('Selected photo updated');
  });
});

function variant(primaryImageUrl:string):CatalogVariant{return{id:21,publicId:'variant-21',productId:5,label:'80 cm · Natural',optionValues:[{optionId:2,optionName:'Color',valueId:3,label:'Natural'}],regularPriceCents:9000,stockQuantity:2,availabilityStatus:'IN_STOCK',active:true,allowBackorder:false,primaryImageUrl,imageUrls:[primaryImageUrl]};}
function image(id:number,url:string):CatalogImage{return{id,url,filename:`photo-${id}.jpg`,sortOrder:id,shared:true,active:true,usages:[]};}
