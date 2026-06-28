import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export interface BackendHealth {
  status: string;
  timestamp: string;
}

@Injectable({
  providedIn: 'root'
})
export class HealthService {
  private readonly http = inject(HttpClient);

  getHealth(): Observable<BackendHealth> {
    return this.http.get<BackendHealth>('/api/health');
  }
}
