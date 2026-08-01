import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of } from 'rxjs';
import { AdminAuthService } from './admin-auth.service';
import { AdminSettingsComponent } from './admin-settings.component';

describe('AdminSettingsComponent', () => {
  let fixture: ComponentFixture<AdminSettingsComponent>;
  let component: AdminSettingsComponent;
  let auth: jasmine.SpyObj<AdminAuthService>;
  let router: jasmine.SpyObj<Router>;

  beforeEach(async () => {
    auth = jasmine.createSpyObj<AdminAuthService>('AdminAuthService', ['changePassword']);
    router = jasmine.createSpyObj<Router>('Router', ['navigate']);
    auth.changePassword.and.returnValue(of(undefined));
    router.navigate.and.resolveTo(true);

    await TestBed.configureTestingModule({
      imports: [AdminSettingsComponent],
      providers: [
        { provide: AdminAuthService, useValue: auth },
        { provide: Router, useValue: router }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(AdminSettingsComponent);
    component = fixture.componentInstance;
  });

  it('rejects mismatched new passwords before calling the API', () => {
    (component as any).currentPassword.set('current-password');
    (component as any).newPassword.set('new-secure-password');
    (component as any).newPasswordConfirmation.set('different-password');

    (component as any).changePassword(new Event('submit'));

    expect(auth.changePassword).not.toHaveBeenCalled();
    expect((component as any).errorMessage()).toBe('The new passwords do not match.');
  });

  it('changes the password and sends the signed-out admin back to login', () => {
    (component as any).currentPassword.set('current-password');
    (component as any).newPassword.set('new-secure-password');
    (component as any).newPasswordConfirmation.set('new-secure-password');

    (component as any).changePassword(new Event('submit'));

    expect(auth.changePassword).toHaveBeenCalledWith('current-password', 'new-secure-password');
    expect(router.navigate).toHaveBeenCalledWith(['/admin/login'], {
      queryParams: { reason: 'password-changed' }
    });
  });
});
