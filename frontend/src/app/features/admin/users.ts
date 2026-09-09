import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { firstValueFrom } from 'rxjs';

import { ApiService } from '../../core/api.service';
import { AdminUser, Department } from '../../core/api.types';
import { I18nService } from '../../core/i18n.service';
import { TPipe } from '../../core/t.pipe';

/** Comptes internes (ADMIN) : liste, activation, création d'un agent (comptes démo). */
@Component({
  selector: 'cc-admin-users',
  imports: [ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatInputModule,
    MatSelectModule, MatSlideToggleModule, MatTableModule, TPipe],
  styles: `
    table { inline-size: 100%; }
    .create { display: flex; gap: 10px; flex-wrap: wrap; align-items: baseline; }
    .create mat-form-field { inline-size: 200px; }
  `,
  template: `
    <h1>{{ 'admin.nav.users' | t }}</h1>
    <div class="cc-card" style="padding:0;overflow:auto;margin-block-end:14px">
      <table mat-table [dataSource]="users()">
        <ng-container matColumnDef="username">
          <th mat-header-cell *matHeaderCellDef>{{ 'login.username' | t }}</th>
          <td mat-cell *matCellDef="let user"><span class="cc-bidi">{{ user.username }}</span></td>
        </ng-container>
        <ng-container matColumnDef="displayName">
          <th mat-header-cell *matHeaderCellDef>{{ 'admin.users.displayName' | t }}</th>
          <td mat-cell *matCellDef="let user">{{ user.displayName }}</td>
        </ng-container>
        <ng-container matColumnDef="roles">
          <th mat-header-cell *matHeaderCellDef>{{ 'admin.users.roles' | t }}</th>
          <td mat-cell *matCellDef="let user">{{ user.roles.join(', ') }}</td>
        </ng-container>
        <ng-container matColumnDef="departments">
          <th mat-header-cell *matHeaderCellDef>{{ 'admin.reports.department' | t }}</th>
          <td mat-cell *matCellDef="let user">{{ user.departments.join(', ') || '—' }}</td>
        </ng-container>
        <ng-container matColumnDef="enabled">
          <th mat-header-cell *matHeaderCellDef>{{ 'admin.catalog.active' | t }}</th>
          <td mat-cell *matCellDef="let user">
            <mat-slide-toggle [checked]="user.enabled"
                              (change)="setEnabled(user, $event.checked)" /></td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns"></tr>
      </table>
    </div>

    <div class="cc-card">
      <h2 style="font-size:16px;margin:0 0 10px">{{ 'admin.users.create' | t }}</h2>
      <form class="create" [formGroup]="form" (ngSubmit)="create()">
        <mat-form-field appearance="outline" subscriptSizing="dynamic">
          <mat-label>{{ 'login.username' | t }}</mat-label>
          <input matInput formControlName="username" autocomplete="off" />
        </mat-form-field>
        <mat-form-field appearance="outline" subscriptSizing="dynamic">
          <mat-label>{{ 'admin.users.displayName' | t }}</mat-label>
          <input matInput formControlName="displayName" />
        </mat-form-field>
        <mat-form-field appearance="outline" subscriptSizing="dynamic">
          <mat-label>{{ 'login.password' | t }}</mat-label>
          <input matInput type="password" formControlName="password" autocomplete="new-password" />
        </mat-form-field>
        <mat-form-field appearance="outline" subscriptSizing="dynamic">
          <mat-label>{{ 'admin.users.roles' | t }}</mat-label>
          <mat-select formControlName="role">
            <mat-option value="AGENT">AGENT</mat-option>
            <mat-option value="MODERATOR">MODERATOR</mat-option>
          </mat-select>
        </mat-form-field>
        <mat-form-field appearance="outline" subscriptSizing="dynamic">
          <mat-label>{{ 'admin.reports.department' | t }}</mat-label>
          <mat-select formControlName="departmentIds" multiple>
            @for (dept of departments(); track dept.id) {
              <mat-option [value]="dept.id">{{ dept.nameFr }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        <button mat-flat-button type="submit" [disabled]="form.invalid">
          {{ 'common.confirm' | t }}</button>
      </form>
    </div>
  `,
})
export class AdminUsers {
  private readonly api = inject(ApiService);
  private readonly fb = inject(FormBuilder);
  private readonly snackBar = inject(MatSnackBar);
  private readonly i18n = inject(I18nService);

  readonly columns = ['username', 'displayName', 'roles', 'departments', 'enabled'];
  readonly users = signal<AdminUser[]>([]);
  readonly departments = signal<Department[]>([]);

  readonly form = this.fb.nonNullable.group({
    username: ['', [Validators.required, Validators.pattern(/^[a-z0-9.\-_]{3,40}$/)]],
    displayName: ['', Validators.required],
    password: ['', [Validators.required, Validators.minLength(8)]],
    role: ['AGENT', Validators.required],
    departmentIds: [[] as string[]],
  });

  constructor() {
    void this.reload();
    void firstValueFrom(this.api.adminDepartments()).then((d) => this.departments.set(d));
  }

  private async reload(): Promise<void> {
    this.users.set(await firstValueFrom(this.api.adminUsers()));
  }

  async setEnabled(user: AdminUser, enabled: boolean): Promise<void> {
    try {
      await firstValueFrom(this.api.adminSetUserEnabled(user.id, enabled));
    } catch {
      this.snackBar.open(this.i18n.t('common.error'), undefined, { duration: 5000 });
    }
    await this.reload();
  }

  async create(): Promise<void> {
    if (this.form.invalid) return;
    const value = this.form.getRawValue();
    try {
      await firstValueFrom(this.api.adminCreateUser({
        username: value.username,
        password: value.password,
        displayName: value.displayName,
        roles: [value.role],
        departmentIds: value.departmentIds,
      }));
      this.form.reset({
        username: '', displayName: '', password: '', role: 'AGENT', departmentIds: [],
      });
      await this.reload();
    } catch (error: unknown) {
      const status = (error as { status?: number }).status;
      this.snackBar.open(status === 409
        ? this.i18n.t('admin.users.duplicate') : this.i18n.t('common.error'),
        undefined, { duration: 5000 });
    }
  }
}
