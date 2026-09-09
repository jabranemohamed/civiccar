import { Component, inject, signal } from '@angular/core';
import { MatTableModule } from '@angular/material/table';
import { firstValueFrom } from 'rxjs';

import { ApiService } from '../../core/api.service';
import { Department } from '../../core/api.types';
import { TPipe } from '../../core/t.pipe';

/** Équipes municipales fictives (lecture) : nom FR/AR, badge démo. */
@Component({
  selector: 'cc-admin-departments',
  imports: [MatTableModule, TPipe],
  styles: `table { inline-size: 100%; }`,
  template: `
    <h1>{{ 'admin.nav.departments' | t }}</h1>
    <div class="cc-banner" style="margin-block-end:12px">{{ 'admin.departments.demo' | t }}</div>
    <div class="cc-card" style="padding:0;overflow:auto">
      <table mat-table [dataSource]="departments()">
        <ng-container matColumnDef="code">
          <th mat-header-cell *matHeaderCellDef>Code</th>
          <td mat-cell *matCellDef="let dept"><span class="cc-bidi">{{ dept.code }}</span></td>
        </ng-container>
        <ng-container matColumnDef="nameFr">
          <th mat-header-cell *matHeaderCellDef>FR</th>
          <td mat-cell *matCellDef="let dept">{{ dept.nameFr }}</td>
        </ng-container>
        <ng-container matColumnDef="nameAr">
          <th mat-header-cell *matHeaderCellDef>AR</th>
          <td mat-cell *matCellDef="let dept"><span class="cc-bidi">{{ dept.nameAr }}</span></td>
        </ng-container>
        <ng-container matColumnDef="active">
          <th mat-header-cell *matHeaderCellDef>{{ 'admin.catalog.active' | t }}</th>
          <td mat-cell *matCellDef="let dept">{{ dept.active ? '✓' : '—' }}</td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns"></tr>
      </table>
    </div>
  `,
})
export class AdminDepartments {
  private readonly api = inject(ApiService);
  readonly columns = ['code', 'nameFr', 'nameAr', 'active'];
  readonly departments = signal<Department[]>([]);

  constructor() {
    void firstValueFrom(this.api.adminDepartments()).then((d) => this.departments.set(d));
  }
}
