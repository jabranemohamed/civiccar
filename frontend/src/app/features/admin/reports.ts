import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { Router } from '@angular/router';
import { MatTableModule } from '@angular/material/table';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { Subject, startWith, switchMap } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { firstValueFrom } from 'rxjs';

import { ApiService } from '../../core/api.service';
import { Department, PublicationStatus, WorkQueueRow, WorkflowStatus } from '../../core/api.types';
import { I18nService } from '../../core/i18n.service';
import { TPipe } from '../../core/t.pipe';
import { StatusChip } from '../../shared/status-chip';

/** File de travail : tri/pagination côté serveur (jamais tout le jeu en mémoire). */
@Component({
  selector: 'cc-admin-reports',
  imports: [DatePipe, MatTableModule, MatPaginatorModule, MatFormFieldModule, MatSelectModule,
    MatCheckboxModule, MatButtonModule, MatIconModule, TPipe, StatusChip],
  styles: `
    .filters { display: flex; gap: 10px; flex-wrap: wrap; align-items: center; margin-block: 10px; }
    .filters mat-form-field { inline-size: 180px; }
    table { inline-size: 100%; }
    tr.row { cursor: pointer; }
    tr.row:hover { background: var(--cc-primary-soft); }
  `,
  template: `
    <h1>{{ 'admin.reports.title' | t }}</h1>
    <div class="filters">
      <mat-form-field appearance="outline" subscriptSizing="dynamic">
        <mat-label>{{ 'admin.reports.status' | t }}</mat-label>
        <mat-select [value]="status()" (valueChange)="status.set($event); reload()">
          <mat-option [value]="null">{{ 'filter.all' | t }}</mat-option>
          @for (st of statuses; track st) {
            <mat-option [value]="st">{{ 'status.' + st | t }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
      <mat-form-field appearance="outline" subscriptSizing="dynamic">
        <mat-label>{{ 'admin.reports.publication' | t }}</mat-label>
        <mat-select [value]="publication()" (valueChange)="publication.set($event); reload()">
          <mat-option [value]="null">{{ 'filter.all' | t }}</mat-option>
          @for (p of publications; track p) {
            <mat-option [value]="p">{{ 'publication.' + p | t }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
      <mat-form-field appearance="outline" subscriptSizing="dynamic">
        <mat-label>{{ 'admin.reports.department' | t }}</mat-label>
        <mat-select [value]="departmentId()" (valueChange)="departmentId.set($event); reload()">
          <mat-option [value]="null">{{ 'filter.all' | t }}</mat-option>
          @for (dept of departments(); track dept.id) {
            <mat-option [value]="dept.id">{{ dept.nameFr }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
      <mat-checkbox [checked]="onlyMine()" (change)="onlyMine.set($event.checked); reload()">
        {{ 'admin.reports.filter.mine' | t }}</mat-checkbox>
      <mat-checkbox [checked]="onlyUnassigned()" (change)="onlyUnassigned.set($event.checked); reload()">
        {{ 'admin.reports.unassigned' | t }}</mat-checkbox>
      <button mat-stroked-button (click)="exportCsv()">
        <mat-icon>download</mat-icon> {{ 'admin.dashboard.export' | t }}</button>
    </div>

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
        <ng-container matColumnDef="publication">
          <th mat-header-cell *matHeaderCellDef>{{ 'admin.reports.publication' | t }}</th>
          <td mat-cell *matCellDef="let row">
            <cc-status-chip [status]="row.publication" kind="publication" /></td>
        </ng-container>
        <ng-container matColumnDef="department">
          <th mat-header-cell *matHeaderCellDef>{{ 'admin.reports.department' | t }}</th>
          <td mat-cell *matCellDef="let row">{{ row.departmentName ?? '—' }}</td>
        </ng-container>
        <ng-container matColumnDef="assignee">
          <th mat-header-cell *matHeaderCellDef>{{ 'admin.reports.assignee' | t }}</th>
          <td mat-cell *matCellDef="let row">
            {{ row.assigneeName ?? ('admin.reports.unassigned' | t) }}</td>
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
      <mat-paginator [length]="total()" [pageSize]="pageSize" [pageIndex]="page()"
                     (page)="onPage($event)" [hidePageSize]="true" />
    </div>
  `,
})
export class AdminReports {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  readonly i18n = inject(I18nService);

  readonly columns = ['reference', 'type', 'status', 'publication', 'department', 'assignee', 'created'];
  readonly statuses: WorkflowStatus[] = ['OPEN', 'IN_PROGRESS', 'DONE_OR_ORDERED', 'OUT_OF_SCOPE'];
  readonly publications: PublicationStatus[] = ['PENDING_REVIEW', 'PUBLISHED', 'HIDDEN'];
  readonly pageSize = 20;

  readonly status = signal<WorkflowStatus | null>(null);
  readonly publication = signal<PublicationStatus | null>(null);
  readonly departmentId = signal<string | null>(null);
  readonly onlyMine = signal(false);
  readonly onlyUnassigned = signal(false);
  readonly page = signal(0);

  readonly rows = signal<WorkQueueRow[]>([]);
  readonly total = signal(0);
  readonly departments = signal<Department[]>([]);

  private readonly trigger = new Subject<void>();

  constructor() {
    void firstValueFrom(this.api.adminDepartments()).then((d) => this.departments.set(d));
    this.trigger.pipe(
      startWith(undefined),
      switchMap(() => this.api.adminWorkQueue({
        status: this.status() ?? undefined,
        publication: this.publication() ?? undefined,
        departmentId: this.departmentId() ?? undefined,
        onlyMine: this.onlyMine(),
        onlyUnassigned: this.onlyUnassigned(),
      }, this.page(), this.pageSize)),
      takeUntilDestroyed(),
    ).subscribe((result) => {
      this.rows.set(result.items);
      this.total.set(result.total);
    });
  }

  reload(): void {
    this.page.set(0);
    this.trigger.next();
  }

  onPage(event: PageEvent): void {
    this.page.set(event.pageIndex);
    this.trigger.next();
  }

  open(row: WorkQueueRow): void {
    void this.router.navigate(['/admin/report', row.id]);
  }

  exportCsv(): void {
    const params = new URLSearchParams();
    if (this.status()) params.set('status', this.status()!);
    if (this.publication()) params.set('publication', this.publication()!);
    if (this.departmentId()) params.set('departmentId', this.departmentId()!);
    window.open('/api/v1/admin/reports/export.csv?' + params.toString(), '_blank');
  }
}
