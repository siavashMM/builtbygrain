import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { App } from './app';
import { HealthService } from './health.service';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        {
          provide: HealthService,
          useValue: {
            getHealth: () => of({ status: 'ok', timestamp: '2026-06-28T12:00:00Z' })
          }
        }
      ]
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it('should render the homepage and backend status', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('h1')?.textContent).toContain('Handcrafted wooden goods');
    expect(compiled.textContent).toContain('ok');
  });
});
