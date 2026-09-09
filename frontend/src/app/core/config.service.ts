import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { AppConfig } from './api.types';

/** Configuration serveur (carte, limites, urgences) chargée au bootstrap. */
@Injectable({ providedIn: 'root' })
export class ConfigService {
  private readonly http = inject(HttpClient);

  readonly config = signal<AppConfig | null>(null);

  async load(): Promise<void> {
    try {
      this.config.set(await firstValueFrom(this.http.get<AppConfig>('/api/v1/config')));
    } catch {
      // L'app reste utilisable avec des valeurs par défaut ; les vues gèrent config nulle.
      this.config.set(null);
    }
  }

  get(): AppConfig | null {
    return this.config();
  }
}
