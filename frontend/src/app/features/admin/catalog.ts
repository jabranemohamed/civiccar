import { Component, computed, inject, signal } from '@angular/core';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { firstValueFrom } from 'rxjs';

import { ApiService } from '../../core/api.service';
import { AdminCatalog, Department } from '../../core/api.types';
import { I18nService } from '../../core/i18n.service';
import { TPipe } from '../../core/t.pipe';

/** Catalogue (ADMIN) : activer/désactiver les types, ajuster le routage vers les équipes. */
@Component({
  selector: 'cc-admin-catalog',
  imports: [MatSlideToggleModule, MatFormFieldModule, MatSelectModule, TPipe],
  styles: `
    .type-row {
      display: flex; align-items: center; gap: 14px; flex-wrap: wrap;
      padding-block: 8px; border-block-end: 1px solid var(--cc-bg);
    }
    .labels { flex: 1; min-inline-size: 260px; }
    .group { font-size: 12px; color: var(--cc-text-muted); }
  `,
  template: `
    <h1>{{ 'admin.nav.catalog' | t }}</h1>
    <div class="cc-card">
      @for (type of types(); track type.id) {
        <div class="type-row">
          <div class="labels">
            <div class="group">{{ type.groupLabelFr }}</div>
            <strong>{{ type.labelFr }}</strong>
            <span class="cc-muted"> · <span class="cc-bidi">{{ type.labelAr }}</span>
              · {{ type.labelEn }}</span>
          </div>
          <mat-form-field appearance="outline" subscriptSizing="dynamic"
                          style="inline-size:240px">
            <mat-label>{{ 'admin.reports.department' | t }}</mat-label>
            <mat-select [value]="ruleOf(type.id)?.departmentId ?? null"
                        (valueChange)="routeTo(type.id, $event)">
              @for (dept of departments(); track dept.id) {
                <mat-option [value]="dept.id">{{ dept.nameFr }}</mat-option>
              }
            </mat-select>
          </mat-form-field>
          <mat-slide-toggle [checked]="type.active"
                            (change)="setActive(type.id, $event.checked)">
            {{ 'admin.catalog.active' | t }}
          </mat-slide-toggle>
        </div>
      }
    </div>
  `,
})
export class AdminCatalogPage {
  private readonly api = inject(ApiService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly i18n = inject(I18nService);

  readonly catalog = signal<AdminCatalog | null>(null);
  readonly departments = signal<Department[]>([]);
  readonly types = computed(() => this.catalog()?.types ?? []);

  constructor() {
    void this.reload();
    void firstValueFrom(this.api.adminDepartments()).then((d) => this.departments.set(d));
  }

  private async reload(): Promise<void> {
    this.catalog.set(await firstValueFrom(this.api.adminCatalog()));
  }

  ruleOf(typeId: string) {
    return this.catalog()?.routingRules.find((rule) => rule.serviceTypeId === typeId) ?? null;
  }

  async setActive(typeId: string, active: boolean): Promise<void> {
    try {
      await firstValueFrom(this.api.adminSetTypeActive(typeId, active));
    } catch {
      this.snackBar.open(this.i18n.t('common.error'), undefined, { duration: 5000 });
    }
    await this.reload();
  }

  async routeTo(typeId: string, departmentId: string): Promise<void> {
    const rule = this.ruleOf(typeId);
    if (!rule) return;
    try {
      await firstValueFrom(this.api.adminUpdateRouting(rule.id, departmentId, true));
    } catch {
      this.snackBar.open(this.i18n.t('common.error'), undefined, { duration: 5000 });
    }
    await this.reload();
  }
}
