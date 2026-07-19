import { HttpEventType } from '@angular/common/http';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { AdminStorefrontService, NavigationGroup, StorefrontSettings } from './admin-storefront.service';

describe('AdminStorefrontService', () => {
  let service: AdminStorefrontService;
  let http: HttpTestingController;

  const settings: StorefrontSettings = {
    heroImageUrl: '/hero.jpg', heroImageAltText: 'Oak desk', heroHeading: 'Heading',
    heroSupportingText: 'Supporting text', updatedAt: '2026-07-18T12:00:00Z'
  };

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(AdminStorefrontService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('reads and updates homepage settings through the real endpoints', () => {
    service.settings().subscribe(value => expect(value.heroImageUrl).toBe('/hero.jpg'));
    let request = http.expectOne('/api/admin/storefront/settings');
    expect(request.request.method).toBe('GET');
    request.flush(settings);

    service.updateSettings({ heroImageAltText: 'New alt', heroHeading: null, heroSupportingText: null }).subscribe();
    request = http.expectOne('/api/admin/storefront/settings');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body.heroImageAltText).toBe('New alt');
    request.flush({ ...settings, heroImageAltText: 'New alt' });
  });

  it('uploads hero replacements as multipart data with progress enabled', () => {
    const events: number[] = [];
    service.replaceHeroImage(new File(['image'], 'hero.png', { type: 'image/png' }), 'Accessible hero').subscribe(event => events.push(event.type));
    const request = http.expectOne('/api/admin/storefront/settings/hero-image');
    expect(request.request.method).toBe('POST');
    expect(request.request.reportProgress).toBeTrue();
    expect(request.request.body instanceof FormData).toBeTrue();
    expect((request.request.body as FormData).get('altText')).toBe('Accessible hero');
    request.event({ type: HttpEventType.UploadProgress, loaded: 5, total: 10 });
    request.flush({ ...settings, heroImageUrl: '/new-hero.png' });
    expect(events).toContain(HttpEventType.UploadProgress);
    expect(events).toContain(HttpEventType.Response);
  });

  it('maps group CRUD, status, ordering, and assignments to the backend contract', () => {
    const group = { id: 7, label: 'Office', active: false, displayOrder: 0, categories: [], featuredProducts: [] } as NavigationGroup;
    service.groups().subscribe();
    expectRequest('GET', '/api/admin/storefront/navigation-groups', []);
    service.createGroup('Office').subscribe();
    let request = http.expectOne('/api/admin/storefront/navigation-groups');
    expect(request.request.method).toBe('POST'); expect(request.request.body).toEqual({ label: 'Office', active: false }); request.flush(group);
    service.updateGroup(group, 'Workspace').subscribe();
    request = http.expectOne('/api/admin/storefront/navigation-groups/7');
    expect(request.request.method).toBe('PUT'); expect(request.request.body).toEqual({ label: 'Workspace', active: false }); request.flush(group);
    service.setGroupActive(7, true).subscribe();
    expectRequest('PATCH', '/api/admin/storefront/navigation-groups/7/activate', group);
    service.reorderGroups([8, 7]).subscribe();
    request = http.expectOne('/api/admin/storefront/navigation-groups/reorder');
    expect(request.request.body).toEqual({ ids: [8, 7] }); request.flush([]);
    service.assignCategory(7, 11).subscribe();
    request = http.expectOne('/api/admin/storefront/navigation-groups/7/categories');
    expect(request.request.body).toEqual({ id: 11 }); request.flush(group);
    service.reorderCategories(7, [12, 11]).subscribe();
    request = http.expectOne('/api/admin/storefront/navigation-groups/7/categories/reorder');
    expect(request.request.body).toEqual({ ids: [12, 11] }); request.flush(group);
    service.assignFeaturedProduct(7, 21).subscribe();
    request = http.expectOne('/api/admin/storefront/navigation-groups/7/featured-products');
    expect(request.request.body).toEqual({ id: 21 }); request.flush(group);
    service.reorderFeaturedProducts(7, [22, 21]).subscribe();
    request = http.expectOne('/api/admin/storefront/navigation-groups/7/featured-products/reorder');
    expect(request.request.body).toEqual({ ids: [22, 21] }); request.flush(group);
    service.removeCategory(7, 11).subscribe();
    expectRequest('DELETE', '/api/admin/storefront/navigation-groups/7/categories/11', null);
    service.removeFeaturedProduct(7, 21).subscribe();
    expectRequest('DELETE', '/api/admin/storefront/navigation-groups/7/featured-products/21', null);
    service.deleteGroup(7).subscribe();
    expectRequest('DELETE', '/api/admin/storefront/navigation-groups/7', null);
  });

  function expectRequest(method: string, url: string, response: any): void {
    const request = http.expectOne(url);
    expect(request.request.method).toBe(method);
    request.flush(response);
  }
});
