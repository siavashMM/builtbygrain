import { Component, OnInit, inject, signal } from '@angular/core';
import { HealthService } from './health.service';

type BackendState = 'checking' | 'online' | 'offline';

@Component({
  selector: 'app-root',
  imports: [],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App implements OnInit {
  private readonly healthService = inject(HealthService);

  protected readonly backendState = signal<BackendState>('checking');
  protected readonly backendStatus = signal('Checking backend');
  protected readonly backendTimestamp = signal<string | null>(null);

  ngOnInit(): void {
    this.refreshBackendStatus();
  }

  protected refreshBackendStatus(): void {
    this.backendState.set('checking');
    this.backendStatus.set('Checking backend');
    this.backendTimestamp.set(null);

    this.healthService.getHealth().subscribe({
      next: (health) => {
        this.backendState.set('online');
        this.backendStatus.set(health.status);
        this.backendTimestamp.set(health.timestamp);
      },
      error: () => {
        this.backendState.set('offline');
        this.backendStatus.set('Unavailable');
        this.backendTimestamp.set(null);
      }
    });
  }
}
