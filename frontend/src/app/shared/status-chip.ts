import { Component, computed, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { TPipe } from '../core/t.pipe';

const ICONS: Record<string, string> = {
  OPEN: 'radio_button_unchecked',
  IN_PROGRESS: 'settings',
  DONE_OR_ORDERED: 'check_circle',
  OUT_OF_SCOPE: 'output',
  PENDING_REVIEW: 'hourglass_top',
  PUBLISHED: 'visibility',
  HIDDEN: 'visibility_off',
};

/** Pilule de statut : icône + libellé textuel traduit (jamais la couleur seule). */
@Component({
  selector: 'cc-status-chip',
  imports: [MatIconModule, TPipe],
  template: `
    <span class="cc-chip" [class]="chipClass()">
      <mat-icon inline>{{ icon() }}</mat-icon>
      {{ key() | t }}
    </span>
  `,
})
export class StatusChip {
  readonly status = input.required<string>();
  /** 'status' (workflow) ou 'publication'. */
  readonly kind = input<'status' | 'publication'>('status');
  readonly archived = input(false);

  readonly icon = computed(() => (this.archived() ? 'inventory_2' : ICONS[this.status()] ?? 'circle'));
  readonly key = computed(() =>
    this.archived() ? 'status.archived' : `${this.kind()}.${this.status()}`);
  readonly chipClass = computed(() => `cc-chip--${this.status().toLowerCase()}`);
}
