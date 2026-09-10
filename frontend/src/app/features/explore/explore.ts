import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DatePipe } from '@angular/common';
import { Router } from '@angular/router';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Subject, debounceTime, distinctUntilChanged, startWith, switchMap } from 'rxjs';
import { toObservable } from '@angular/core/rxjs-interop';

import { ApiService } from '../../core/api.service';
import { CatalogGroup, MapPoint, ReportSummary, SearchFilters, WorkflowStatus } from '../../core/api.types';
import { ConfigService } from '../../core/config.service';
import { I18nService } from '../../core/i18n.service';
import { TPipe } from '../../core/t.pipe';
import { CcMap } from '../../shared/map';
import { StatusChip } from '../../shared/status-chip';

const PAGE_SIZE = 10;
type Period = 'any' | 'today' | 'week' | 'month';

/**
 * Accueil : carte + liste des signalements publiés, mêmes filtres et même visibilité.
 * Les réponses obsolètes (recherche, emprise) sont ignorées via switchMap ; la liste
 * reste utilisable si la carte échoue.
 */
@Component({
  selector: 'cc-explore',
  imports: [DatePipe, MatFormFieldModule, MatInputModule, MatSelectModule,
    MatCheckboxModule, MatButtonModule, MatIconModule, MatPaginatorModule,
    MatProgressSpinnerModule, TPipe, CcMap, StatusChip],
  styles: `
    .layout { display: flex; gap: 16px; padding: 16px; align-items: flex-start; flex: 1; }
    .panel { inline-size: 430px; max-inline-size: 100%; display: flex; flex-direction: column; gap: 12px; }
    /* Colonne carte collante à hauteur d'écran : la liste défile, la carte reste visible */
    .map-wrap {
      flex: 1; min-block-size: 420px;
      position: sticky; inset-block-start: 16px;
      block-size: calc(100vh - 32px);
    }
    .filters { display: flex; gap: 10px; flex-wrap: wrap; }
    // assez large pour «Catégorie» / «Toutes les dates» sans troncature en mobile
    .filters mat-form-field { flex: 1; min-inline-size: 160px; }
    .results { display: flex; flex-direction: column; gap: 10px; }
    .card { display: flex; gap: 12px; align-items: center; }
    .card img { inline-size: 64px; block-size: 64px; object-fit: cover; border-radius: 10px; }
    .card .title { font-weight: 600; }
    .card .meta { font-size: 12px; color: var(--cc-text-muted); }
    .count { font-weight: 700; }
    .view-toggle { display: none; }
    @media (max-width: 900px) {
      .layout { flex-direction: column; }
      .panel { inline-size: 100%; }
      .view-toggle { display: flex; gap: 8px; }
      .hidden-mobile { display: none; }
    }
  `,
  template: `
    <div class="layout">
      <div class="panel">
        <div class="cc-banner">{{ 'emergency.generic' | t }}</div>
        <div class="cc-muted" style="font-size:12px">{{ 'app.demo.disclaimer' | t }}</div>
        <h1 style="margin:4px 0">{{ 'home.title' | t }}</h1>

        <div class="view-toggle" role="group">
          <button mat-stroked-button (click)="mobileView.set('list')"
                  [disabled]="mobileView() === 'list'">
            <mat-icon>list</mat-icon> {{ 'home.view.list' | t }}</button>
          <button mat-stroked-button (click)="mobileView.set('map')"
                  [disabled]="mobileView() === 'map'">
            <mat-icon>map</mat-icon> {{ 'home.view.map' | t }}</button>
        </div>

        <mat-form-field appearance="outline" subscriptSizing="dynamic">
          <mat-label>{{ 'common.search' | t }}</mat-label>
          <input matInput [value]="text()" (input)="text.set($any($event.target).value)"
                 [placeholder]="'home.search.placeholder' | t"
                 id="explore-search" />
          <mat-icon matSuffix>search</mat-icon>
        </mat-form-field>

        <div class="filters">
          <mat-form-field appearance="outline" subscriptSizing="dynamic">
            <mat-label>{{ 'filter.category' | t }}</mat-label>
            <mat-select [value]="groupId()" (valueChange)="groupId.set($event); reload()">
              <mat-option [value]="null">{{ 'filter.all' | t }}</mat-option>
              @for (group of groups(); track group.id) {
                <mat-option [value]="group.id">{{ i18n.label(group.labels) }}</mat-option>
              }
            </mat-select>
          </mat-form-field>
          <mat-form-field appearance="outline" subscriptSizing="dynamic">
            <mat-label>{{ 'filter.status' | t }}</mat-label>
            <mat-select [value]="status()" (valueChange)="status.set($event); reload()">
              <mat-option [value]="null">{{ 'filter.all' | t }}</mat-option>
              @for (st of statuses; track st) {
                <mat-option [value]="st">{{ 'status.' + st | t }}</mat-option>
              }
            </mat-select>
          </mat-form-field>
          <mat-form-field appearance="outline" subscriptSizing="dynamic">
            <mat-label>{{ 'filter.period' | t }}</mat-label>
            <mat-select [value]="period()" (valueChange)="period.set($event); reload()">
              <mat-option value="any">{{ 'filter.period.any' | t }}</mat-option>
              <mat-option value="today">{{ 'filter.period.today' | t }}</mat-option>
              <mat-option value="week">{{ 'filter.period.week' | t }}</mat-option>
              <mat-option value="month">{{ 'filter.period.month' | t }}</mat-option>
            </mat-select>
          </mat-form-field>
        </div>
        <mat-checkbox [checked]="includeArchived()"
                      (change)="includeArchived.set($event.checked); reload()">
          {{ 'filter.archives' | t }}
        </mat-checkbox>
        <button mat-button (click)="resetFilters()">{{ 'filter.reset' | t }}</button>

        <div class="count" role="status" id="result-count">
          {{ 'home.results.count' | t: total() }}
        </div>

        @if (loading()) {
          <mat-progress-spinner mode="indeterminate" diameter="32" />
        } @else if (items().length === 0) {
          <div class="cc-muted">{{ 'home.results.empty' | t }}</div>
        } @else {
          <div class="results" id="results-list">
            @for (item of items(); track item.id) {
              <div class="cc-card cc-card--hover card" tabindex="0" role="link"
                   [attr.data-report-id]="item.id"
                   (click)="open(item)" (keydown.enter)="open(item)">
                @if (item.thumbKey) {
                  <img [src]="'/media/' + item.thumbKey" alt="" />
                }
                <div>
                  <div class="title">{{ i18n.label(item.typeLabels) }}</div>
                  <div class="meta">{{ item.address }}</div>
                  <cc-status-chip [status]="item.status" [archived]="item.archived" />
                  <div class="meta">
                    {{ item.createdAt | date: 'mediumDate' : undefined : i18n.locale() }}
                    · <span class="cc-bidi">{{ item.reference }}</span>
                  </div>
                </div>
              </div>
            }
          </div>
          <mat-paginator [length]="total()" [pageSize]="pageSize" [pageIndex]="page()"
                         (page)="onPage($event)" [hidePageSize]="true" />
        }
      </div>

      <div class="map-wrap" [class.hidden-mobile]="mobileView() === 'list'">
        @if (mapFailed()) {
          <div class="cc-banner">{{ 'home.map.unavailable' | t }}</div>
        } @else {
          <cc-map height="100%" [points]="points()"
                  (moved)="onMoved($event)" (mapError)="mapFailed.set(true)"
                  (markerClick)="focusCard($event)" />
        }
      </div>
    </div>
  `,
})
export class Explore {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  readonly i18n = inject(I18nService);
  private readonly config = inject(ConfigService);

  readonly statuses: WorkflowStatus[] = ['OPEN', 'IN_PROGRESS', 'DONE_OR_ORDERED', 'OUT_OF_SCOPE'];
  readonly pageSize = PAGE_SIZE;

  readonly text = signal('');
  readonly groupId = signal<string | null>(null);
  readonly status = signal<WorkflowStatus | null>(null);
  readonly period = signal<Period>('any');
  readonly includeArchived = signal(false);
  readonly page = signal(0);

  readonly groups = signal<CatalogGroup[]>([]);
  readonly items = signal<ReportSummary[]>([]);
  readonly total = signal(0);
  readonly points = signal<MapPoint[]>([]);
  readonly loading = signal(true);
  readonly mapFailed = signal(false);
  readonly mobileView = signal<'list' | 'map'>('list');

  private readonly listTrigger = new Subject<void>();
  private readonly bboxSubject = new Subject<[number, number, number, number]>();
  private lastBbox: [number, number, number, number] = [9.9, 36.6, 10.45, 37.0];

  constructor() {
    this.api.catalog().pipe(takeUntilDestroyed()).subscribe((groups) => this.groups.set(groups));

    // Recherche texte débouncée ; switchMap ignore les réponses obsolètes
    toObservable(this.text).pipe(debounceTime(350), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe(() => this.reload());

    this.listTrigger.pipe(
      startWith(undefined),
      switchMap(() => {
        this.loading.set(true);
        return this.api.searchReports(this.filters(), this.page(), PAGE_SIZE);
      }),
      takeUntilDestroyed(),
    ).subscribe({
      next: (result) => {
        this.items.set(result.items);
        this.total.set(result.total);
        this.loading.set(false);
        this.refreshMap();
      },
      error: () => this.loading.set(false),
    });

    this.bboxSubject.pipe(
      debounceTime(250),
      switchMap((bbox) => {
        this.lastBbox = bbox;
        return this.api.mapPoints(bbox, this.filters());
      }),
      takeUntilDestroyed(),
    ).subscribe((points) => this.points.set(points));
  }

  filters(): SearchFilters {
    const tz = this.config.get()?.timezone ?? 'Africa/Tunis';
    let from: string | undefined;
    if (this.period() !== 'any') {
      // Début de journée/semaine/mois dans le fuseau Africa/Tunis
      const now = new Date();
      const local = new Date(now.toLocaleString('en-US', { timeZone: tz }));
      const start = new Date(local);
      start.setHours(0, 0, 0, 0);
      if (this.period() === 'week') {
        start.setDate(start.getDate() - ((start.getDay() + 6) % 7));
      } else if (this.period() === 'month') {
        start.setDate(1);
      }
      const offsetMs = local.getTime() - now.getTime();
      from = new Date(start.getTime() - offsetMs).toISOString();
    }
    return {
      text: this.text() || undefined,
      groupId: this.groupId() ?? undefined,
      status: this.status() ?? undefined,
      from,
      includeArchived: this.includeArchived(),
    };
  }

  reload(): void {
    this.page.set(0);
    this.listTrigger.next();
  }

  onPage(event: PageEvent): void {
    this.page.set(event.pageIndex);
    this.listTrigger.next();
  }

  resetFilters(): void {
    this.text.set('');
    this.groupId.set(null);
    this.status.set(null);
    this.period.set('any');
    this.includeArchived.set(false);
    this.reload();
  }

  onMoved(bbox: [number, number, number, number]): void {
    this.bboxSubject.next(bbox);
  }

  private refreshMap(): void {
    this.bboxSubject.next(this.lastBbox);
  }

  open(item: ReportSummary): void {
    void this.router.navigate(['/requests', item.reference]);
  }

  focusCard(id: string): void {
    const summary = this.items().find((i) => i.id === id);
    if (summary) {
      void this.router.navigate(['/requests', summary.reference]);
    } else {
      document.querySelector(`[data-report-id="${id}"]`)?.scrollIntoView({ behavior: 'smooth' });
    }
  }
}
