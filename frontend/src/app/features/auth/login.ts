import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatIconModule } from '@angular/material/icon';

import { AuthService } from '../../core/auth.service';
import { ConfigService } from '../../core/config.service';
import { TPipe } from '../../core/t.pipe';

/** Connexion des agents (sessions Spring conservées). Erreur générique en cas d'échec. */
@Component({
  selector: 'cc-login',
  imports: [ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatInputModule,
    MatIconModule, TPipe],
  styles: `
    .page { min-block-size: 60vh; display: grid; place-items: center; padding: 24px 16px; }
    form { display: flex; flex-direction: column; gap: 10px; inline-size: 340px; max-inline-size: 100%; }
    h1 { text-align: center; color: var(--cc-primary); margin-bottom: 0; }
  `,
  template: `
    <div class="page">
      <form class="cc-card" [formGroup]="form" (ngSubmit)="submit()">
        <h1>{{ appName }}</h1>
        <p class="cc-muted" style="text-align:center">{{ 'login.staffOnly' | t }}</p>
        @if (error()) {
          <div class="cc-banner" role="alert">{{ 'login.error.message' | t }}</div>
        }
        <mat-form-field appearance="outline">
          <mat-label>{{ 'login.username' | t }}</mat-label>
          <input matInput formControlName="username" autocomplete="username" required
                 name="username" />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>{{ 'login.password' | t }}</mat-label>
          <input matInput type="password" formControlName="password"
                 autocomplete="current-password" required name="password" />
        </mat-form-field>
        <button mat-flat-button type="submit" [disabled]="pending()">
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
  private readonly fb = inject(FormBuilder);

  readonly error = signal(false);
  readonly pending = signal(false);

  readonly form = this.fb.nonNullable.group({
    username: ['', Validators.required],
    password: ['', Validators.required],
  });

  get appName(): string {
    return this.config.get()?.appName ?? 'CivicCare Tunis';
  }

  async submit(): Promise<void> {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.pending.set(true);
    this.error.set(false);
    const { username, password } = this.form.getRawValue();
    const ok = await this.auth.login(username, password);
    this.pending.set(false);
    if (ok) {
      const target = this.route.snapshot.queryParamMap.get('continue') ?? '/admin';
      void this.router.navigateByUrl(target);
    } else {
      this.error.set(true);
    }
  }
}
