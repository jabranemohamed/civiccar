import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { FormField, form, required, submit } from '@angular/forms/signals';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatIconModule } from '@angular/material/icon';

import { AuthService } from '../../core/auth.service';
import { ConfigService } from '../../core/config.service';
import { TPipe } from '../../core/t.pipe';
import { markAllTouched } from '../../core/forms';

/**
 * Connexion des agents (sessions Spring conservées). Erreur générique en cas d'échec.
 * Formulaire en Signal Forms : modèle signal + directive [formField].
 */
@Component({
  selector: 'cc-login',
  imports: [FormField, MatButtonModule, MatFormFieldModule, MatInputModule, MatIconModule, TPipe],
  styles: `
    .page { min-block-size: 60vh; display: grid; place-items: center; padding: 24px 16px; }
    form { display: flex; flex-direction: column; gap: 10px; inline-size: 340px; max-inline-size: 100%; }
    h1 { text-align: center; color: var(--cc-primary); margin-bottom: 0; }
  `,
  template: `
    <div class="page">
      <form class="cc-card" (submit)="$event.preventDefault(); connect()">
        <h1>{{ appName }}</h1>
        <p class="cc-muted" style="text-align:center">{{ 'login.staffOnly' | t }}</p>
        @if (error()) {
          <div class="cc-banner" role="alert">{{ 'login.error.message' | t }}</div>
        }
        <mat-form-field appearance="outline">
          <mat-label>{{ 'login.username' | t }}</mat-label>
          <input matInput [formField]="credentials.username" autocomplete="username" />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>{{ 'login.password' | t }}</mat-label>
          <input matInput type="password" [formField]="credentials.password"
                 autocomplete="current-password" />
        </mat-form-field>
        <button mat-flat-button type="submit" [disabled]="credentials().submitting()">
          {{ 'login.submit' | t }}
        </button>
      </form>
    </div>
  `,
})
export class Login {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly config = inject(ConfigService);

  readonly error = signal(false);

  private readonly model = signal({ username: '', password: '' });
  readonly credentials = form(this.model, (path) => {
    required(path.username);
    required(path.password);
  });

  get appName(): string {
    return this.config.get()?.appName ?? 'CivicCare Tunis';
  }

  /** submit() marque les champs touchés et ignore l'action si le formulaire est invalide. */
  async connect(): Promise<void> {
    this.error.set(false);
    if (this.credentials().invalid()) {
      markAllTouched(this.credentials);
      return;
    }
    await submit(this.credentials, async () => {
      const { username, password } = this.model();
      const ok = await this.auth.login(username, password);
      if (ok) {
        const target = this.route.snapshot.queryParamMap.get('continue') ?? '/admin';
        void this.router.navigateByUrl(target);
      } else {
        this.error.set(true);
      }
    });
  }
}
