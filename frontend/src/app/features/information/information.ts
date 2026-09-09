import { Component, effect, inject, input, signal } from '@angular/core';
import { Router } from '@angular/router';
import { MatTabsModule } from '@angular/material/tabs';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { firstValueFrom } from 'rxjs';

import { ApiService } from '../../core/api.service';
import { ContentPage } from '../../core/api.types';
import { I18nService } from '../../core/i18n.service';
import { TPipe } from '../../core/t.pipe';

const SLUGS = ['faq', 'how', 'terms', 'privacy', 'legal', 'accessibility', 'api'] as const;

/** Pages d'information FR/AR/EN (contenu éditorial en base, rendu texte). */
@Component({
  selector: 'cc-information',
  imports: [MatTabsModule, MatProgressSpinnerModule, TPipe],
  styles: `
    .page { max-inline-size: 900px; margin: 0 auto; padding: 20px 16px; }
    .body { white-space: pre-wrap; line-height: 1.6; }
  `,
  template: `
    <div class="page">
      <mat-tab-group [selectedIndex]="selectedIndex()"
                     (selectedIndexChange)="onTab($event)">
        @for (s of slugs; track s) {
          <mat-tab [label]="'info.' + s | t" />
        }
      </mat-tab-group>
      <div class="cc-card" style="margin-top:16px">
        @if (page()) {
          <h1>{{ i18n.label(page()!.title) }}</h1>
          <div class="body">{{ i18n.label(page()!.body) }}</div>
        } @else {
          <mat-progress-spinner mode="indeterminate" diameter="32" />
        }
      </div>
    </div>
  `,
})
export class Information {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  readonly i18n = inject(I18nService);

  readonly slug = input<string>();
  readonly slugs = SLUGS;
  readonly page = signal<ContentPage | null>(null);
  readonly selectedIndex = signal(0);

  constructor() {
    effect(() => {
      const slug = this.slug() && SLUGS.includes(this.slug() as never) ? this.slug()! : 'faq';
      this.selectedIndex.set(SLUGS.indexOf(slug as (typeof SLUGS)[number]));
      void this.load(slug);
    });
  }

  private async load(slug: string): Promise<void> {
    this.page.set(null);
    this.page.set(await firstValueFrom(this.api.content(slug)));
  }

  onTab(index: number): void {
    void this.router.navigate(['/info', SLUGS[index]]);
  }
}
