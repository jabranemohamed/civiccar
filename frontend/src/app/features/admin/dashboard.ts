import { Component, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { firstValueFrom } from 'rxjs';

import { ApiService } from '../../core/api.service';
import { DashboardStats } from '../../core/api.types';
import { TPipe } from '../../core/t.pipe';

/** Tableau de bord : tuiles pastel (style « clean ») + dossiers par équipe. */
@Component({
  selector: 'cc-admin-dashboard',
  imports: [DecimalPipe, MatIconModule, MatProgressSpinnerModule, TPipe],
  styles: `
    .tiles { display: flex; gap: 14px; flex-wrap: wrap; margin-block: 14px; }
    .dept { display: flex; align-items: center; gap: 12px; padding-block: 6px; }
    .dept .name { inline-size: 320px; max-inline-size: 45%; }
    .bar-track { flex: 1; }
    .bar { block-size: 10px; border-radius: 6px; background: var(--cc-primary); min-inline-size: 2%; }
  `,
  template: `
    <h1>{{ 'admin.nav.dashboard' | t }}</h1>
    @if (!stats()) {
      <mat-progress-spinner mode="indeterminate" diameter="36" />
    } @else {
      @let s = stats()!;
      <div class="tiles">
        <div class="cc-stat cc-stat--blue">
          <div class="cc-stat__icon"><mat-icon>assignment</mat-icon></div>
          <div><div class="cc-stat__value">{{ s.total }}</div>
            <div class="cc-stat__label">{{ 'admin.dashboard.total' | t }}</div></div>
        </div>
        <div class="cc-stat cc-stat--teal">
          <div class="cc-stat__icon"><mat-icon>radio_button_unchecked</mat-icon></div>
          <div><div class="cc-stat__value">{{ s.open }}</div>
            <div class="cc-stat__label">{{ 'admin.dashboard.open' | t }}</div></div>
        </div>
        <div class="cc-stat cc-stat--orange">
          <div class="cc-stat__icon"><mat-icon>settings</mat-icon></div>
          <div><div class="cc-stat__value">{{ s.inProgress }}</div>
            <div class="cc-stat__label">{{ 'admin.dashboard.inProgress' | t }}</div></div>
        </div>
        <div class="cc-stat cc-stat--green">
          <div class="cc-stat__icon"><mat-icon>hourglass_top</mat-icon></div>
          <div><div class="cc-stat__value">{{ s.pendingReview }}</div>
            <div class="cc-stat__label">{{ 'admin.dashboard.pendingReview' | t }}</div></div>
        </div>
        <div class="cc-stat cc-stat--blue">
          <div class="cc-stat__icon"><mat-icon>schedule</mat-icon></div>
          <div><div class="cc-stat__value">{{ s.avgCloseDays === null ? '—'
              : (s.avgCloseDays | number: '1.0-1') }}</div>
            <div class="cc-stat__label">{{ 'admin.dashboard.avgClose' | t }}</div></div>
        </div>
        <div class="cc-stat cc-stat--orange">
          <div class="cc-stat__icon"><mat-icon>outgoing_mail</mat-icon></div>
          <div><div class="cc-stat__value">{{ s.outboxBacklog }}</div>
            <div class="cc-stat__label">{{ 'admin.dashboard.outboxBacklog' | t }}</div></div>
        </div>
      </div>
      <div class="cc-card">
        <h2>{{ 'admin.dashboard.byDepartment' | t }}</h2>
        @for (entry of byDepartment(); track entry[0]) {
          <div class="dept">
            <span class="name">{{ entry[0] }} : {{ entry[1] }}</span>
            <div class="bar-track">
              <div class="bar" [style.inline-size.%]="barWidth(entry[1])"></div>
            </div>
          </div>
        }
      </div>
    }
  `,
})
export class AdminDashboard {
  private readonly api = inject(ApiService);
  readonly stats = signal<DashboardStats | null>(null);

  constructor() {
    void firstValueFrom(this.api.adminDashboard()).then((stats) => this.stats.set(stats));
  }

  byDepartment(): [string, number][] {
    return Object.entries(this.stats()?.byDepartment ?? {});
  }

  barWidth(count: number): number {
    const max = Math.max(1, ...Object.values(this.stats()?.byDepartment ?? {}));
    return Math.max(2, (count * 100) / max);
  }
}
