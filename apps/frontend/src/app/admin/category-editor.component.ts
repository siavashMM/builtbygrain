import { Component, EventEmitter, Input, OnChanges, Output, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { CategoryDetails, CategoryRequest } from './admin-catalog.service';

@Component({selector:'app-category-editor',imports:[ReactiveFormsModule],templateUrl:'./category-editor.component.html'})
export class CategoryEditorComponent implements OnChanges{
  private readonly fb=inject(FormBuilder);
  @Input() category:CategoryDetails|null=null;
  @Input() categories:CategoryDetails[]=[];
  @Input() parentId:number|null=null;
  @Input() saving=false;
  @Output() save=new EventEmitter<CategoryRequest>();
  @Output() cancel=new EventEmitter<void>();
  @Output() deleteCategory=new EventEmitter<void>();
  readonly form=this.fb.nonNullable.group({name:['',[Validators.required,Validators.maxLength(160)]],slug:['',[Validators.required,Validators.pattern(/^[a-z0-9]+(?:-[a-z0-9]+)*$/)]],parentId:this.fb.control<number|null>(null),description:[''],imageUrl:[''],sortOrder:[0,[Validators.min(0)]],active:[true]});
  ngOnChanges():void{const c=this.category;this.form.reset({name:c?.name??'',slug:c?.slug??'',parentId:c?.parentId??this.parentId,description:c?.description??'',imageUrl:c?.imageUrl??'',sortOrder:c?.sortOrder??0,active:c?.active??true});}
  submit():void{this.form.markAllAsTouched();if(this.form.invalid)return;const v=this.form.getRawValue();this.save.emit({...v,description:v.description.trim()||null,imageUrl:v.imageUrl.trim()||null});}
  slugFromName():void{if(this.category||this.form.controls.slug.dirty)return;this.form.controls.slug.setValue(this.form.controls.name.value.toLowerCase().trim().replace(/[^a-z0-9]+/g,'-').replace(/^-|-$/g,''));}
  availableParents():CategoryDetails[]{return this.categories.filter(c=>c.id!==this.category?.id);}
}
