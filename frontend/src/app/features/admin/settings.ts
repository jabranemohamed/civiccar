import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { MatTableModule } from '@angular/material/table';
import { firstValueFrom } from 'rxjs';

import { ApiService } from '../../core/api.service';
import { AdminSettings, AuditEntry } from '../../core/api.types';
import { I18nService } from '../../core/i18n.service';
import { TPipe } from '../../core/t.pipe';

/** Paramètres effectifs (lecture, config par environnement) + journal d'audit (ADMIN). */
@Component({
  selector: 'cc-admin-settings',
  imports: [DatePipe, MatTableModule, TPipe],
  styles: `
    dl { display: grid; grid-template-columns: 280px 1fr; gap: 6px 14px; margin: 0; }
    dt { color: var(--cc-text-muted); }
    dd { margin: 0; }
    table { inline-size: 100%; }
  `,
  template: `
    <h1>{{ 'admin.nav.settings' | t }}</h1>
    @if (settings()) {
      @let s = settings()!;
      @if (s.boundary.demo) {
        <div class="cc-banner" style="margin-block-end:12px">{{ 'boundary.demoNotice' | t }}</div>
      }
      <div class="cc-card" style="margin-block-end:14px">
        <dl>
          <dt>{{ 'admin.settings.boundary' | t }}</dt>
          <dd>{{ s.boundary.nameFr }} ({{ s.boundary.code }}) —
            {{ s.boundary.source }}, {{ s.boundary.license }}</dd>
          <dt>{{ 'admin.settings.timezone' | t }}</dt><dd>{{ s.timezone }}</dd>
          <dt>{{ 'admin.settings.country' | t }}</dt><dd>{{ s.countryCode }}</dd>
          <dt>{{ 'admin.settings.duplicateRadius' | t }}</dt>
          <dd>{{ s.duplicateRadiusMeters }} m</dd>
          <dt>{{ 'admin.settings.archiveAfter' | t }}</dt><dd>{{ s.archiveAfterDays }} j</dd>
          <dt>{{ 'admin.settings.purgeAfter' | t }}</dt><dd>{{ s.purgeAfterDays }} j</dd>
          <dt>{{ 'admin.settings.geocoder' | t }}</dt><dd>{{ s.geocoderMode }}</dd>
          <dt>Open311</dt><dd><span class="cc-bidi">{{ s.open311Jurisdiction }}</span></dd>
          <dt>{{ 'admin.settings.tiles' | t }}</dt>
          <dd style="overflow-wrap:anywhere"><span class="cc-bidi">{{ s.tileUrl }}</span></dd>
        </dl>
      </div>
    }

    <h2 style="font-size:16px">{{ 'admin.settings.audit' | t }}</h2>
    <div class="cc-card" style="padding:0;overflow:auto">
      <table mat-table [dataSource]="audit()">
        <ng-container matColumnDef="at">
          <th mat-header-cell *matHeaderCellDef>{{ 'admin.reports.created' | t }}</th>
          <td mat-cell *matCellDef="let entry">
            {{ entry.at | date: 'medium' : undefined : i18n.locale() }}</td>
        </ng-container>
        <ng-container matColumnDef="actor">
          <th mat-header-cell *matHeaderCellDef>{{ 'admin.audit.actor' | t }}</th>
          <td mat-cell *matCellDef="let entry">{{ entry.actor }}</td>
        </ng-container>
        <ng-container matColumnDef="action">
          <th mat-header-cell *matHeaderCellDef>{{ 'admin.audit.action' | t }}</th>
          <td mat-cell *matCellDef="let entry">{{ actionLabel(entry.action) }}</td>
        </ng-container>
        <ng-container matColumnDef="target">
          <th mat-header-cell *matHeaderCellDef>{{ 'admin.audit.target' | t }}</th>
          <td mat-cell *matCellDef="let entry">
            {{ targetLabel(entry.targetType) }}
            <span class="cc-bidi">{{ entry.targetId }}</span></td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns"></tr>
      </table>
    </div>
  `,
})
export class AdminSettingsPage {
  private readonly api = inject(ApiService);
  readonly i18n = inject(I18nService);

  readonly columns = ['at', 'actor', 'action', 'target'];
  readonly settings = signal<AdminSettings | null>(null);
  readonly audit = signal<AuditEntry[]>([]);

  constructor() {
    void firstValueFrom(this.api.adminSettings()).then((s) => this.settings.set(s));
    void firstValueFrom(this.api.adminAudit()).then((a) => this.audit.set(a));
  }

  /** Libellé traduit d'une action d'audit ; code brut si clé inconnue (codes futurs). */
  actionLabel(code: string): string {
    if (code.startsWith('STATUS_')) {
      return this.i18n.t('audit.action.status', this.i18n.t('status.' + code.slice(7)));
    }
    const label = this.i18n.t('audit.action.' + code);
    return label.startsWith('!') ? code : label;
  }

  targetLabel(type: string): string {
    const label = this.i18n.t('audit.target.' + type);
    return label.startsWith('!') ? type : label;
  }
}
