import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatSnackBar } from '@angular/material/snack-bar';
import { firstValueFrom } from 'rxjs';

import { ApiService } from '../../core/api.service';
import { ContactMessage } from '../../core/api.types';
import { I18nService } from '../../core/i18n.service';
import { TPipe } from '../../core/t.pipe';

/** Messages de contact citoyens : lecture + marquage « traité ». */
@Component({
  selector: 'cc-admin-contact-inbox',
  imports: [DatePipe, MatButtonModule, TPipe],
  styles: `
    .message { border-block-end: 1px solid var(--cc-bg); padding-block: 12px; }
    .head { display: flex; gap: 10px; align-items: baseline; flex-wrap: wrap; }
    .processed { opacity: 0.55; }
  `,
  template: `
    <h1>{{ 'admin.nav.contact' | t }}</h1>
    <div class="cc-card">
      @if (messages().length === 0) {
        <p class="cc-muted">{{ 'explore.empty' | t }}</p>
      }
      @for (message of messages(); track message.id) {
        <div class="message" [class.processed]="message.status === 'PROCESSED'">
          <div class="head">
            <strong>{{ message.name }}</strong>
            <span class="cc-bidi cc-muted">{{ message.email }}</span>
            <span class="cc-muted">
              {{ message.createdAt | date: 'medium' : undefined : i18n.locale() }}</span>
            @if (message.copyRequested) {
              <span class="cc-chip cc-chip--pending_review">{{ 'contact.copy' | t }}</span>
            }
            @if (message.status === 'PROCESSED') {
              <span class="cc-chip cc-chip--done_or_ordered">{{ 'admin.contact.processed' | t }}</span>
            }
          </div>
          <p style="white-space:pre-wrap;margin-block:6px">{{ message.body }}</p>
          @if (message.status !== 'PROCESSED') {
            <button mat-stroked-button (click)="markProcessed(message)">
              {{ 'admin.contact.markProcessed' | t }}</button>
          }
        </div>
      }
    </div>
  `,
})
export class AdminContactInbox {
  private readonly api = inject(ApiService);
  private readonly snackBar = inject(MatSnackBar);
  readonly i18n = inject(I18nService);

  readonly messages = signal<ContactMessage[]>([]);

  constructor() {
    void this.reload();
  }

  private async reload(): Promise<void> {
    this.messages.set(await firstValueFrom(this.api.adminContactMessages()));
  }

  async markProcessed(message: ContactMessage): Promise<void> {
    try {
      await firstValueFrom(this.api.adminMarkContactProcessed(message.id));
    } catch {
      this.snackBar.open(this.i18n.t('common.error'), undefined, { duration: 5000 });
    }
    await this.reload();
  }
}
