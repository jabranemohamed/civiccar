import { Component, inject, input, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { firstValueFrom } from 'rxjs';

import { ApiService } from '../../core/api.service';
import { TPipe } from '../../core/t.pipe';

/**
 * Liens d'action e-mail (chemins conservés /s/confirm/{t}, /s/unsubscribe/{t}) :
 * la page GET affiche une confirmation, l'action est un POST déclenché au clic —
 * jamais exécutée par un scanner de messagerie.
 */
@Component({
  selector: 'cc-subscription-action',
  imports: [MatButtonModule, TPipe],
  styles: `.page { max-inline-size: 560px; margin: 40px auto; padding: 0 16px;
                   display: flex; flex-direction: column; gap: 12px; align-items: center;
                   text-align: center; }`,
  template: `
    <div class="page cc-card">
      @if (result() === null) {
        <h1>{{ prefix() + '.title' | t }}</h1>
        <p>{{ prefix() + '.body' | t }}</p>
        <button mat-flat-button id="confirm-action" (click)="run()">
          {{ prefix() + '.button' | t }}
        </button>
      } @else if (result()) {
        <h1>{{ prefix() + '.success' | t }}</h1>
      } @else {
        <h1>{{ 'subscription.confirm.invalid' | t }}</h1>
      }
    </div>
  `,
})
export class SubscriptionAction {
  private readonly api = inject(ApiService);
  private readonly route = inject(ActivatedRoute);

  readonly token = input.required<string>();
  readonly result = signal<boolean | null>(null);

  prefix(): string {
    const mode = this.route.snapshot.data['mode'] as 'confirm' | 'unsubscribe';
    return mode === 'unsubscribe' ? 'subscription.unsubscribe' : 'subscription.confirm';
  }

  async run(): Promise<void> {
    const mode = this.route.snapshot.data['mode'] as 'confirm' | 'unsubscribe';
    const call = mode === 'unsubscribe'
      ? this.api.unsubscribe(this.token())
      : this.api.confirmSubscription(this.token());
    try {
      const response = await firstValueFrom(call);
      this.result.set(response.ok);
    } catch {
      this.result.set(false);
    }
  }
}
