import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { Router } from '@angular/router';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { firstValueFrom } from 'rxjs';

import { ApiService } from '../../core/api.service';
import { ReportSummary } from '../../core/api.types';
import { I18nService } from '../../core/i18n.service';
import { TPipe } from '../../core/t.pipe';
import { StatusChip } from '../../shared/status-chip';

/** Suivis de cet appareil (cookie cc_device conservé) : dossiers publiés uniquement. */
@Component({
  selector: 'cc-following',
  imports: [DatePipe, MatProgressSpinnerModule, TPipe, StatusChip],
  styles: `
    .page { max-inline-size: 860px; margin: 0 auto; padding: 20px 16px;
            display: flex; flex-direction: column; gap: 12px; }
    .card { display: flex; gap: 12px; align-items: center; }
    .card img { inline-size: 60px; block-size: 60px; object-fit: cover; border-radius: 10px; }
  `,
  template: `
    <div class="page">
      <h1>{{ 'following.title' | t }} ({{ items().length }})</h1>
      <div class="cc-muted">{{ 'following.hint' | t }}</div>
      @if (loading()) {
        <mat-progress-spinner mode="indeterminate" diameter="32" />
      } @else if (items().length === 0) {
        <div class="cc-muted">{{ 'following.empty' | t }}</div>
      } @else {
        @for (item of items(); track item.id) {
          <div class="cc-card cc-card--hover card" tabindex="0" role="link"
               (click)="open(item)" (keydown.enter)="open(item)">
            @if (item.thumbKey) { <img [src]="'/media/' + item.thumbKey" alt="" /> }
            <div>
              <div style="font-weight:600">{{ i18n.label(item.typeLabels) }}</div>
              <cc-status-chip [status]="item.status" [archived]="item.archived" />
              <div class="cc-muted" style="font-size:12px">
                <span class="cc-bidi">{{ item.reference }}</span>
                · {{ item.createdAt | date: 'mediumDate' : undefined : i18n.locale() }}
              </div>
            </div>
          </div>
        }
      }
    </div>
  `,
})
export class Following {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  readonly i18n = inject(I18nService);

  readonly items = signal<ReportSummary[]>([]);
  readonly loading = signal(true);

  constructor() {
    void this.load();
  }

  private async load(): Promise<void> {
    try {
      const result = await firstValueFrom(this.api.bookmarks());
      this.items.set(result.items);
    } finally {
      this.loading.set(false);
    }
  }

  open(item: ReportSummary): void {
    void this.router.navigate(['/requests', item.reference]);
  }
}
