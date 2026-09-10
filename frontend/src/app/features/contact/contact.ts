import { Component, inject, signal } from '@angular/core';
import { FormField, email, form, maxLength, required, submit, validate, requiredError } from '@angular/forms/signals';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar } from '@angular/material/snack-bar';
import { firstValueFrom } from 'rxjs';

import { ApiService } from '../../core/api.service';
import { I18nService } from '../../core/i18n.service';
import { TPipe } from '../../core/t.pipe';
import { markAllTouched } from '../../core/forms';

/** Contact général (Signal Forms) : validation serveur, honeypot invisible, copie facultative. */
@Component({
  selector: 'cc-contact',
  imports: [FormField, MatButtonModule, MatCheckboxModule, MatFormFieldModule,
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
              (submit)="$event.preventDefault(); send()">
          <mat-form-field appearance="outline">
            <mat-label>{{ 'contact.name' | t }}</mat-label>
            <input matInput [formField]="message.name" id="contact-name" />
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>{{ 'contact.email' | t }}</mat-label>
            <input matInput type="email" [formField]="message.email" id="contact-email" />
            <mat-error>{{ 'wizard.contact.email.invalid' | t }}</mat-error>
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>{{ 'contact.message' | t }}</mat-label>
            <textarea matInput [formField]="message.message" rows="6"
                      id="contact-message"></textarea>
          </mat-form-field>
          <!-- Honeypot : invisible pour les humains -->
          <input class="hp" [formField]="message.website" tabindex="-1" aria-hidden="true"
                 autocomplete="off" />
          <mat-checkbox [formField]="message.copyRequested">{{ 'contact.copy' | t }}</mat-checkbox>
          <mat-checkbox [formField]="message.consent" id="contact-consent">
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

  readonly sent = signal(false);

  private readonly model = signal({
    name: '', email: '', message: '', website: '', copyRequested: false, consent: false,
  });
  readonly message = form(this.model, (path) => {
    required(path.name);
    required(path.email);
    email(path.email);
    required(path.message);
    maxLength(path.message, 4000);
    // Consentement : coché obligatoirement (équivalent requiredTrue)
    validate(path.consent, ({ value }) => (value() ? null : requiredError()));
  });

  async send(): Promise<void> {
    if (this.message().invalid()) {
      markAllTouched(this.message);
      if (!this.model().consent) {
        this.snackBar.open(this.i18n.t('wizard.consent.required'), undefined, { duration: 4000 });
      }
    }
    await submit(this.message, async () => {
      try {
        await firstValueFrom(this.api.sendContact(this.model()));
        this.sent.set(true);
      } catch {
        this.snackBar.open(this.i18n.t('contact.error'), undefined, { duration: 5000 });
      }
    });
  }
}
