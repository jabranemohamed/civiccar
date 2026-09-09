import { Injectable, effect, inject } from '@angular/core';
import { MatPaginatorIntl } from '@angular/material/paginator';
import { I18nService } from './i18n.service';

/** Libellés du paginator localisés FR/AR/EN et rafraîchis au changement de locale. */
@Injectable()
export class CcPaginatorIntl extends MatPaginatorIntl {
  private readonly i18n = inject(I18nService);

  constructor() {
    super();
    // MatPaginatorIntl notifie ses consommateurs via `changes`
    effect(() => {
      this.i18n.locale();
      this.i18n.version();
      this.apply();
      this.changes.next();
    });
  }

  private apply(): void {
    this.itemsPerPageLabel = this.i18n.t('paginator.itemsPerPage');
    this.nextPageLabel = this.i18n.t('paginator.next');
    this.previousPageLabel = this.i18n.t('paginator.previous');
    this.getRangeLabel = (page, pageSize, length) => {
      if (length === 0 || pageSize === 0) {
        return this.i18n.t('paginator.range', 0, 0, length);
      }
      const start = page * pageSize + 1;
      const end = Math.min((page + 1) * pageSize, length);
      return this.i18n.t('paginator.range', start, end, length);
    };
  }
}
