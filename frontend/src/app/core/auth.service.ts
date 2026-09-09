import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { Me } from './api.types';

/**
 * Session Spring Security (cookie HttpOnly, jamais lu en JS). L'état est résolu au
 * démarrage et après login/logout ; les guards attendent cette résolution pour éviter
 * les redirections en boucle au refresh d'une page admin.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);

  readonly me = signal<Me>({ authenticated: false });
  readonly resolved = signal(false);

  readonly isAuthenticated = computed(() => this.me().authenticated);
  readonly roles = computed(() => this.me().roles ?? []);
  readonly isAdmin = computed(() => this.roles().includes('ADMIN'));
  readonly isModerator = computed(() =>
    this.roles().includes('MODERATOR') || this.roles().includes('ADMIN'));

  /** Résout la session courante (bootstrap + après navigation de retour). */
  async resolve(): Promise<Me> {
    try {
      const me = await firstValueFrom(this.http.get<Me>('/api/v1/auth/me'));
      this.me.set(me);
    } catch {
      this.me.set({ authenticated: false });
    } finally {
      this.resolved.set(true);
    }
    return this.me();
  }

  /** Connexion : POST formulaire (mécanisme Spring Security conservé). 401 -> false. */
  async login(username: string, password: string): Promise<boolean> {
    const body = new URLSearchParams({ username, password });
    try {
      await firstValueFrom(this.http.post('/api/v1/auth/login', body.toString(), {
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      }));
      await this.resolve();
      return this.isAuthenticated();
    } catch {
      return false;
    }
  }

  /** Déconnexion POST (protégée CSRF). */
  async logout(): Promise<void> {
    try {
      await firstValueFrom(this.http.post('/api/v1/auth/logout', {}));
    } finally {
      this.me.set({ authenticated: false });
    }
  }
}
