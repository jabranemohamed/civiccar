import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTabsModule } from '@angular/material/tabs';
import { firstValueFrom } from 'rxjs';

import { ApiService } from '../../core/api.service';
import { AdminContentPage } from '../../core/api.types';
import { I18nService } from '../../core/i18n.service';
import { TPipe } from '../../core/t.pipe';

/** Édition des pages d'information : FR / AR / EN par onglets ; la zone AR est saisie en RTL. */
@Component({
  selector: 'cc-admin-content',
  imports: [ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatInputModule,
    MatSelectModule, MatTabsModule, TPipe],
  styles: `
    .full { inline-size: 100%; }
    .rtl textarea, .rtl input { direction: rtl; }
    mat-tab-group { margin-block-start: 8px; }
  `,
  template: `
    <h1>{{ 'admin.nav.content' | t }}</h1>
    <div class="cc-card">
      <mat-form-field appearance="outline" subscriptSizing="dynamic" style="inline-size:300px">
        <mat-label>{{ 'admin.content.page' | t }}</mat-label>
        <mat-select [value]="selected()?.slug" (valueChange)="select($event)">
          @for (page of pages(); track page.slug) {
            <mat-option [value]="page.slug">{{ page.slug }}</mat-option>
          }
        </mat-select>
      </mat-form-field>

      @if (selected()) {
        <form [formGroup]="form" (ngSubmit)="save()">
          <mat-tab-group>
            <mat-tab label="Français">
              <mat-form-field appearance="outline" class="full">
                <mat-label>{{ 'admin.content.title' | t }}</mat-label>
                <input matInput formControlName="titleFr" />
              </mat-form-field>
              <mat-form-field appearance="outline" class="full">
                <mat-label>{{ 'admin.content.body' | t }}</mat-label>
                <textarea matInput formControlName="bodyFr" rows="12"></textarea>
              </mat-form-field>
            </mat-tab>
            <mat-tab label="العربية">
              <mat-form-field appearance="outline" class="full rtl">
                <mat-label>{{ 'admin.content.title' | t }}</mat-label>
                <input matInput formControlName="titleAr" dir="rtl" />
              </mat-form-field>
              <mat-form-field appearance="outline" class="full rtl">
                <mat-label>{{ 'admin.content.body' | t }}</mat-label>
                <textarea matInput formControlName="bodyAr" rows="12" dir="rtl"></textarea>
              </mat-form-field>
            </mat-tab>
            <mat-tab label="English">
              <mat-form-field appearance="outline" class="full">
                <mat-label>{{ 'admin.content.title' | t }}</mat-label>
                <input matInput formControlName="titleEn" />
              </mat-form-field>
              <mat-form-field appearance="outline" class="full">
                <mat-label>{{ 'admin.content.body' | t }}</mat-label>
                <textarea matInput formControlName="bodyEn" rows="12"></textarea>
              </mat-form-field>
            </mat-tab>
          </mat-tab-group>
          <button mat-flat-button type="submit">{{ 'common.confirm' | t }}</button>
        </form>
      }
    </div>
  `,
})
export class AdminContent {
  private readonly api = inject(ApiService);
  private readonly fb = inject(FormBuilder);
  private readonly snackBar = inject(MatSnackBar);
  private readonly i18n = inject(I18nService);

  readonly pages = signal<AdminContentPage[]>([]);
  readonly selected = signal<AdminContentPage | null>(null);

  readonly form = this.fb.nonNullable.group({
    titleFr: '', titleAr: '', titleEn: '',
    bodyFr: '', bodyAr: '', bodyEn: '',
  });

  constructor() {
    void firstValueFrom(this.api.adminContent()).then((pages) => {
      this.pages.set(pages);
      if (pages.length > 0) this.select(pages[0].slug);
    });
  }

  select(slug: string): void {
    const page = this.pages().find((p) => p.slug === slug) ?? null;
    this.selected.set(page);
    if (page) {
      this.form.setValue({
        titleFr: page.titleFr, titleAr: page.titleAr, titleEn: page.titleEn,
        bodyFr: page.bodyFr, bodyAr: page.bodyAr, bodyEn: page.bodyEn,
      });
    }
  }

  async save(): Promise<void> {
    const page = this.selected();
    if (!page) return;
    try {
      await firstValueFrom(this.api.adminUpdateContent(page.slug, this.form.getRawValue()));
      const pages = await firstValueFrom(this.api.adminContent());
      this.pages.set(pages);
      this.snackBar.open(this.i18n.t('common.saved'), undefined, { duration: 3000 });
    } catch {
      this.snackBar.open(this.i18n.t('common.error'), undefined, { duration: 5000 });
    }
  }
}
