import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { AdminCatalogService, CatalogTreeNode } from './admin-catalog.service';
import { CatalogTreeComponent } from './catalog-tree.component';

describe('CatalogTreeComponent', () => {
  const category: CatalogTreeNode = {
    id: 'category:3', nodeType: 'CATEGORY', parentId: null,
    label: 'Shelves', secondaryLabel: null, status: 'ACTIVE',
    hasChildren: false, iconType: 'category', sortOrder: 0
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [CatalogTreeComponent],
      providers: [{
        provide: AdminCatalogService,
        useValue: { roots: () => of([category]), children: () => of([]) }
      }]
    });
  });

  it('does not reselect a category when Add product is clicked', () => {
    const fixture = TestBed.createComponent(CatalogTreeComponent);
    const component = fixture.componentInstance;
    const selected: CatalogTreeNode[] = [];
    const actions: string[] = [];
    component.nodeSelected.subscribe((node) => selected.push(node));
    component.actionRequested.subscribe((event) => actions.push(event.action));
    fixture.detectChanges();

    fixture.nativeElement.querySelector('.node-select').click();
    fixture.detectChanges();
    const buttons = fixture.nativeElement.querySelectorAll('.catalog-toolbar button');
    buttons[2].click();

    expect(selected).toEqual([category]);
    expect(actions).toEqual(['add-product']);
  });
});
