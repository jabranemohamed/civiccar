import { ChangeDetectorRef, Pipe, PipeTransform, effect, inject } from '@angular/core';
import { I18nService } from './i18n.service';

/**
 * Pipe de traduction impur. En zoneless, une vue dont le template ne lit les signaux
 * qu'à travers un pipe n'est pas marquée sale au changement de locale : l'effet
 * ci-dessous notifie explicitement la vue hôte (markForCheck), ce qui fait re-exécuter
 * le pipe impur. L'effet vit et meurt avec la vue (injecteur de nœud du pipe).
 */
@Pipe({ name: 't', pure: false })
export class TPipe implements PipeTransform {
  private readonly i18n = inject(I18nService);
  private readonly cdr = inject(ChangeDetectorRef);

  constructor() {
    effect(() => {
      this.i18n.locale();
      this.i18n.version();
      this.cdr.markForCheck();
    });
  }

  transform(key: string, ...params: unknown[]): string {
    return this.i18n.t(key, ...params);
  }
}
