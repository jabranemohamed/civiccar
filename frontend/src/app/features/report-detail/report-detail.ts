import { Component, effect, inject, input, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormField, email as emailValidator, form, required, submit } from '@angular/forms/signals';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { ApiService } from '../../core/api.service';
import { ReportDetail } from '../../core/api.types';
import { I18nService } from '../../core/i18n.service';
import { TPipe } from '../../core/t.pipe';
import { markAllTouched } from '../../core/forms';
import { StatusChip } from '../../shared/status-chip';

/** Fiche publique : un dossier non publié répond comme un dossier inexistant. */
@Component({
  selector: 'cc-report-detail',
  imports: [DatePipe, FormField, MatButtonModule, MatCheckboxModule, MatFormFieldModule,
    MatIconModule, MatInputModule, MatProgressSpinnerModule, RouterLink, TPipe, StatusChip],
  styles: `
    .page { max-inline-size: 860px; margin: 0 auto; padding: 20px 16px; display: flex;
            flex-direction: column; gap: 16px; }
    .head { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
    h1 { margin: 0; font-size: 24px; }
    .gallery { display: flex; gap: 10px; flex-wrap: wrap; }
    .gallery img { max-inline-size: 240px; border-radius: var(--cc-radius-s); }
    .subscribe { display: flex; flex-direction: column; gap: 8px; max-inline-size: 420px; }
  `,
  template: `
    <div class="page">
      @if (loading()) {
        <mat-progress-spinner mode="indeterminate" diameter="36" />
      } @else if (!detail()) {
        <h1>{{ 'detail.notFound' | t }}</h1>
        <a routerLink="/">{{ 'nav.explore' | t }}</a>
      } @else {
        @let d = detail()!;
        <div class="cc-card">
          <h1>{{ d.typeLabel }}</h1>
          <div class="cc-muted">{{ d.groupLabel }} · <span class="cc-bidi">{{ d.reference }}</span></div>
          <div class="head" style="margin-top:10px">
            <cc-status-chip [status]="d.status" [archived]="d.archived" />
            <span class="cc-muted">
              {{ d.createdAt | date: 'medium' : undefined : i18n.locale() }}
            </span>
            <button mat-stroked-button id="follow-button" (click)="toggleFollow()">
              <mat-icon>{{ followed() ? 'star' : 'star_outline' }}</mat-icon>
              {{ (followed() ? 'detail.unfollow' : 'detail.follow') | t }}
            </button>
          </div>
        </div>

        @if (d.media.length > 0) {
          <div class="cc-card">
            <h2>{{ 'detail.photos' | t }}</h2>
            <div class="gallery">
              @for (m of d.media; track m.full) {
                <a [href]="m.full" target="_blank" rel="noopener">
                  <img [src]="m.full" [alt]="'detail.photos' | t" />
                </a>
              }
            </div>
          </div>
        }

        <div class="cc-card">
          <h2>{{ 'detail.address' | t }}</h2>
          @if (d.address) { <div>{{ d.address }}</div> }
          @if (d.addressDetails) { <div>{{ d.addressDetails }}</div> }
          <div class="cc-muted cc-bidi">{{ d.position.lat.toFixed(5) }}, {{ d.position.lon.toFixed(5) }}</div>
        </div>

        @if (d.description) {
          <div class="cc-card">
            <h2>{{ 'detail.description' | t }}</h2>
            <p style="white-space:pre-wrap">{{ d.description }}</p>
            @for (field of publicFields(); track field[0]) {
              <div>{{ field[0] }} : {{ field[1] }}</div>
            }
          </div>
        }

        <div class="cc-card">
          <h2>{{ 'detail.timeline' | t }}</h2>
          @for (entry of d.timeline; track entry.at) {
            <div class="cc-timeline-entry">
              <div class="cc-timeline-date">
                {{ entry.at | date: 'medium' : undefined : i18n.locale() }}
              </div>
              @if (entry.status) {
                <strong>{{ 'status.' + entry.status | t }}</strong>
              } @else {
                <div style="white-space:pre-wrap">{{ entry.message }}</div>
              }
            </div>
          }
        </div>

        <div class="cc-card subscribe">
          <h2>{{ 'detail.subscribe.title' | t }}</h2>
          <mat-form-field appearance="outline">
            <mat-label>{{ 'detail.subscribe.email' | t }}</mat-label>
            <input matInput type="email" [formField]="subscription.email" id="subscribe-email" />
            <mat-error>{{ 'wizard.contact.email.invalid' | t }}</mat-error>
          </mat-form-field>
          <mat-checkbox [formField]="subscription.consent">{{ 'detail.subscribe.consent' | t }}</mat-checkbox>
          <button mat-flat-button id="subscribe-button" (click)="subscribe()">
            <mat-icon>mail</mat-icon> {{ 'detail.subscribe.submit' | t }}
          </button>
        </div>
      }
    </div>
  `,
})
export class ReportDetailPage {
  private readonly api = inject(ApiService);
  private readonly snackBar = inject(MatSnackBar);
  readonly i18n = inject(I18nService);

  /** Paramètre de route lié automatiquement (withComponentInputBinding). */
  readonly reference = input.required<string>();

  readonly detail = signal<ReportDetail | null>(null);
  readonly loading = signal(true);
  readonly followed = signal(false);

  private readonly subscriptionModel = signal({ email: '', consent: false });
  readonly subscription = form(this.subscriptionModel, (path) => {
    required(path.email);
    emailValidator(path.email);
  });

  constructor() {
    // Rechargement sur changement de référence OU de langue (libellés localisés serveur)
    effect(() => {
      const reference = this.reference();
      const lang = this.i18n.locale();
      void this.load(reference, lang);
    });
  }

  private async load(reference: string, lang: string): Promise<void> {
    this.loading.set(true);
    try {
      const detail = await firstValueFrom(this.api.reportDetail(reference, lang));
      this.detail.set(detail);
      const bookmark = await firstValueFrom(this.api.isBookmarked(detail.id));
      this.followed.set(bookmark.bookmarked);
    } catch {
      this.detail.set(null);
    } finally {
      this.loading.set(false);
    }
  }

  publicFields(): [string, string][] {
    return Object.entries(this.detail()?.publicFields ?? {});
  }

  async toggleFollow(): Promise<void> {
    const detail = this.detail();
    if (!detail) return;
    const result = await firstValueFrom(this.api.toggleBookmark(detail.id));
    this.followed.set(result.bookmarked);
    if (result.bookmarked) {
      this.snackBar.open(this.i18n.t('detail.followed'), undefined, { duration: 3000 });
    }
  }

  async subscribe(): Promise<void> {
    const detail = this.detail();
    if (!detail) return;
    if (this.subscription().invalid()) {
      markAllTouched(this.subscription);
      return;
    }
    if (!this.subscriptionModel().consent) {
      this.snackBar.open(this.i18n.t('wizard.consent.required'), undefined, { duration: 4000 });
      return;
    }
    await submit(this.subscription, async () => {
      await firstValueFrom(this.api.subscribe(detail.id, detail.reference,
        this.subscriptionModel().email));
      // Message identique quel que soit l'état réel : pas de divulgation d'abonnement
      this.snackBar.open(this.i18n.t('detail.subscribe.sent'), undefined, { duration: 6000 });
      this.subscriptionModel.set({ email: '', consent: false });
      this.subscription().reset();
    });
  }
}
