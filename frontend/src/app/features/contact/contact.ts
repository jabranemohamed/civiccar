import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar } from '@angular/material/snack-bar';
import { firstValueFrom } from 'rxjs';

import { ApiService } from '../../core/api.service';
import { I18nService } from '../../core/i18n.service';
import { TPipe } from '../../core/t.pipe';

/** Contact général : validation serveur, honeypot invisible, copie facultative. */
@Component({
  selector: 'cc-contact',
  imports: [ReactiveFormsModule, MatButtonModule, MatCheckboxModule, MatFormFieldModule,
    MatInputModule, TPipe],
  styles: `
    .page { max-inline-size: 640px; margin: 0 auto; padding: 20px 16px;
            display: flex; flex-direction: column; gap: 8px; }
    .hp { position: absolute; inset-inline-start: -9999px; }
  `,
  template: `
    <div class="page">
      @if (sent()) {
        <div class="cc-card"><h1>{{ 'contact.success' | t }}</h1></div>
      } @else {
        <h1>{{ 'contact.title' | t }}</h1>
        <p class="cc-muted">{{ 'contact.intro' | t }}</p>
        <form class="cc-card" style="display:flex;flex-direction:column;gap:8px"
              [formGroup]="form" (ngSubmit)="submit()">
          <mat-form-field appearance="outline">
            <mat-label>{{ 'contact.name' | t }}</mat-label>
            <input matInput formControlName="name" id="contact-name" required />
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>{{ 'contact.email' | t }}</mat-label>
            <input matInput type="email" formControlName="email" id="contact-email" required />
            <mat-error>{{ 'wizard.contact.email.invalid' | t }}</mat-error>
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>{{ 'contact.message' | t }}</mat-label>
            <textarea matInput formControlName="message" rows="6" maxlength="4000"
                      id="contact-message" required></textarea>
          </mat-form-field>
          <!-- Honeypot : invisible pour les humains -->
          <input class="hp" formControlName="website" tabindex="-1" aria-hidden="true"
                 autocomplete="off" />
          <mat-checkbox formControlName="copyRequested">{{ 'contact.copy' | t }}</mat-checkbox>
          <mat-checkbox formControlName="consent" id="contact-consent">
            {{ 'contact.consent' | t }}
          </mat-checkbox>
          <button mat-flat-button type="submit" id="contact-submit">{{ 'contact.submit' | t }}</button>
        </form>
      }
    </div>
  `,
})
export class Contact {
  private readonly api = inject(ApiService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly i18n = inject(I18nService);
  private readonly fb = inject(FormBuilder);

  readonly sent = signal(false);

  readonly form = this.fb.nonNullable.group({
    name: ['', Validators.required],
    email: ['', [Validators.required, Validators.email]],
    message: ['', Validators.required],
    website: [''],
    copyRequested: [false],
    consent: [false, Validators.requiredTrue],
  });

  async submit(): Promise<void> {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      if (this.form.controls.consent.invalid) {
        this.snackBar.open(this.i18n.t('wizard.consent.required'), undefined, { duration: 4000 });
      }
      return;
    }
    try {
      await firstValueFrom(this.api.sendContact(this.form.getRawValue()));
      this.sent.set(true);
    } catch {
      this.snackBar.open(this.i18n.t('contact.error'), undefined, { duration: 5000 });
    }
  }
}
