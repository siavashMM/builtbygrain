import { Component, EventEmitter, OnInit, Output, inject, signal } from '@angular/core';
import { AdminCatalogService, CatalogTreeNode } from './admin-catalog.service';

interface VisibleNode { node: CatalogTreeNode; depth: number; }

@Component({
  selector: 'app-catalog-tree',
  templateUrl: './catalog-tree.component.html'
})
export class CatalogTreeComponent implements OnInit {
  private readonly catalog = inject(AdminCatalogService);
  @Output() nodeSelected = new EventEmitter<CatalogTreeNode>();
  @Output() actionRequested = new EventEmitter<{action:'add-root'|'add-child'|'add-product'|'rename'|'move'|'toggle-status'|'delete'|'duplicate'|'archive';node:CatalogTreeNode|null}>();
  protected readonly roots = signal<CatalogTreeNode[]>([]);
  protected readonly children = signal<Record<string, CatalogTreeNode[]>>({});
  protected readonly expanded = signal<Set<string>>(new Set());
  protected readonly selectedId = signal<string | null>(null);
  protected readonly query = signal('');
  protected readonly state = signal<'loading'|'ready'|'error'>('loading');

  ngOnInit(): void { this.reload(); }
  reload(): void { this.state.set('loading'); const expanded=new Set(this.expanded());this.catalog.roots().subscribe({ next: roots => { this.roots.set(roots);this.children.set({});this.expanded.set(expanded);this.reloadExpanded([...expanded]);this.state.set('ready'); }, error: () => this.state.set('error') }); }
  protected visibleNodes(): VisibleNode[] {
    const result: VisibleNode[] = []; const term = this.query().trim().toLowerCase();
    const visit = (nodes: CatalogTreeNode[], depth: number) => nodes.forEach(node => {
      if (!term || `${node.label} ${node.secondaryLabel ?? ''}`.toLowerCase().includes(term)) result.push({ node, depth });
      if (this.expanded().has(node.id) || term) visit(this.children()[node.id] ?? [], depth + 1);
    }); visit(this.roots(), 0); return result;
  }
  protected toggle(node: CatalogTreeNode, event?: Event): void {
    event?.stopPropagation(); if (!node.hasChildren) return;
    if (this.expanded().has(node.id)) { this.expanded.update(value => { const next = new Set(value); next.delete(node.id); return next; }); return; }
    const open = () => this.expanded.update(value => new Set([...value, node.id]));
    if (this.children()[node.id]) { open(); return; }
    this.catalog.children(node.id).subscribe({ next: nodes => { this.children.update(value => ({ ...value, [node.id]: nodes })); open(); } });
  }
  protected select(node: CatalogTreeNode): void { this.selectedId.set(node.id); this.nodeSelected.emit(node); }
  protected selected():CatalogTreeNode|null{return this.visibleNodes().find(x=>x.node.id===this.selectedId())?.node??null;}
  protected request(action:'add-root'|'add-child'|'add-product'|'rename'|'move'|'toggle-status'|'delete'|'duplicate'|'archive',node:CatalogTreeNode|null=this.selected()):void{
    // Toolbar actions already target the selected node. Emitting nodeSelected again
    // starts an async details request that can overwrite the editor opened by the action.
    if(node&&this.selectedId()!==node.id)this.select(node);
    this.actionRequested.emit({action,node});
  }
  protected updateQuery(value: string): void { this.query.set(value); if (!value.trim()) return; this.prefetch(this.roots()); }
  protected icon(node: CatalogTreeNode): string { return node.nodeType === 'CATEGORY' ? '▱' : node.nodeType === 'PRODUCT' ? '◇' : '↳'; }
  private prefetch(nodes: CatalogTreeNode[]): void { nodes.filter(n => n.hasChildren && !this.children()[n.id]).forEach(n => this.catalog.children(n.id).subscribe({ next: children => this.children.update(value => ({ ...value, [n.id]: children })) })); }
  private reloadExpanded(ids:string[]):void{ids.forEach(id=>this.catalog.children(id).subscribe({next:nodes=>this.children.update(value=>({...value,[id]:nodes}))}));}
}
