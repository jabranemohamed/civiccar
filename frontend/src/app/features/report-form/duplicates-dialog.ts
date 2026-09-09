import { Component, inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';

import { DuplicateCandidate } from '../../core/api.types';
import { I18nService } from '../../core/i18n.service';
import { TPipe } from '../../core/t.pipe';

/** Doublons candidats avant envoi : consulter/suivre l'existant ou confirmer un incident distinct. */
@Component({
  selector: 'cc-duplicates-dialog',
  imports: [MatDialogModule, MatButtonModule, TPipe],
  template: `
    <h2 mat-dialog-title>{{ 'wizard.duplicates.title' | t }}</h2>
    <mat-dialog-content>
      <p>{{ 'wizard.duplicates.intro' | t }}</p>
      @for (candidate of data; track candidate.summary.id) {
        <div style="display:flex;align-items:center;gap:10px;padding-block:4px">
          <span>
            {{ i18n.label(candidate.summary.typeLabels) }}
            · <span class="cc-bidi">{{ candidate.summary.reference }}</span>
            · {{ candidate.distanceMeters }} m
          </span>
          <a mat-button [href]="'/requests/' + candidate.summary.reference" target="_blank">
            {{ 'wizard.duplicates.view' | t }}
          </a>
        </div>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button (click)="ref.close(false)">{{ 'common.cancel' | t }}</button>
      <button mat-flat-button id="confirm-distinct" (click)="ref.close(true)">
        {{ 'wizard.duplicates.confirm' | t }}
      </button>
    </mat-dialog-actions>
  `,
})
export class DuplicatesDialog {
  readonly data = inject<DuplicateCandidate[]>(MAT_DIALOG_DATA);
  readonly ref = inject(MatDialogRef<DuplicatesDialog>);
  readonly i18n = inject(I18nService);
}
