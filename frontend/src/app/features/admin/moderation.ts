import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatTableModule } from '@angular/material/table';
import { Subject, startWith, switchMap } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

import { ApiService } from '../../core/api.service';
import { WorkQueueRow } from '../../core/api.types';
import { I18nService } from '../../core/i18n.service';
import { TPipe } from '../../core/t.pipe';
import { StatusChip } from '../../shared/status-chip';

/** File de modération : signalements en attente de revue, plus ancien en premier. */
@Component({
  selector: 'cc-admin-moderation',
  imports: [DatePipe, MatButtonModule, MatPaginatorModule, MatTableModule, TPipe, StatusChip],
  styles: `
    table { inline-size: 100%; }
    tr.row { cursor: pointer; }
    tr.row:hover { background: var(--cc-primary-soft); }
  `,
  template: `
    <h1>{{ 'admin.moderation.title' | t }}</h1>
    <div class="cc-card" style="padding:0;overflow:auto">
      <table mat-table [dataSource]="rows()">
        <ng-container matColumnDef="reference">
          <th mat-header-cell *matHeaderCellDef>{{ 'admin.reports.reference' | t }}</th>
          <td mat-cell *matCellDef="let row"><span class="cc-bidi">{{ row.reference }}</span></td>
        </ng-container>
        <ng-container matColumnDef="type">
          <th mat-header-cell *matHeaderCellDef>{{ 'admin.reports.type' | t }}</th>
          <td mat-cell *matCellDef="let row">{{ row.typeLabelFr }}</td>
        </ng-container>
        <ng-container matColumnDef="status">
          <th mat-header-cell *matHeaderCellDef>{{ 'admin.reports.status' | t }}</th>
          <td mat-cell *matCellDef="let row"><cc-status-chip [status]="row.status" /></td>
        </ng-container>
        <ng-container matColumnDef="created">
          <th mat-header-cell *matHeaderCellDef>{{ 'admin.reports.created' | t }}</th>
          <td mat-cell *matCellDef="let row">
            {{ row.createdAt | date: 'short' : undefined : i18n.locale() }}</td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row class="row" *matRowDef="let row; columns: columns" tabindex="0"
            (click)="open(row)" (keydown.enter)="open(row)"></tr>
      </table>
      @if (rows().length === 0) {
        <p class="cc-muted" style="padding:16px">{{ 'explore.empty' | t }}</p>
      }
      <mat-paginator [length]="total()" [pageSize]="pageSize" [pageIndex]="page()"
                     (page)="onPage($event)" [hidePageSize]="true" />
    </div>
  `,
})
export class AdminModeration {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  readonly i18n = inject(I18nService);

  readonly columns = ['reference', 'type', 'status', 'created'];
  readonly pageSize = 20;
  readonly page = signal(0);
  readonly rows = signal<WorkQueueRow[]>([]);
  readonly total = signal(0);

  private readonly trigger = new Subject<void>();

  constructor() {
    this.trigger.pipe(
      startWith(undefined),
      switchMap(() => this.api.adminWorkQueue(
        { publication: 'PENDING_REVIEW' }, this.page(), this.pageSize)),
      takeUntilDestroyed(),
    ).subscribe((result) => {
      this.rows.set(result.items);
      this.total.set(result.total);
    });
  }

  onPage(event: PageEvent): void {
    this.page.set(event.pageIndex);
    this.trigger.next();
  }

  open(row: WorkQueueRow): void {
    void this.router.navigate(['/admin/report', row.id]);
  }
}
