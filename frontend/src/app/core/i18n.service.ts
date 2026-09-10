import { DOCUMENT } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Injectable, computed, effect, inject, signal } from '@angular/core';
import { Direction, Directionality } from '@angular/cdk/bidi';
import { firstValueFrom } from 'rxjs';

export type Locale = 'fr' | 'ar' | 'en';

const STORAGE_KEY = 'cc_locale';
const SUPPORTED: Locale[] = ['fr', 'ar', 'en'];

/**
 * i18n à l'exécution : catalogues JSON (générés depuis les bundles Java), locale en
 * signal, bascule FR/AR/EN sans rechargement. Met à jour `lang`/`dir` du document ;
 * la Directionality CDK (overlays, menus, datepicker, stepper) suit via CcDirectionality.
 */
@Injectable({ providedIn: 'root' })
export class I18nService {
  private readonly http = inject(HttpClient);
  private readonly document = inject(DOCUMENT);

  private readonly catalogs = new Map<Locale, Record<string, string>>();
  readonly locale = signal<Locale>(this.initialLocale());
  readonly dir = computed<Direction>(() => (this.locale() === 'ar' ? 'rtl' : 'ltr'));
  /** Incrémenté à chaque catalogue chargé pour invalider le pipe. */
  readonly version = signal(0);

  constructor() {
    effect(() => {
      const locale = this.locale();
      this.document.documentElement.lang = locale;
      this.document.documentElement.dir = this.dir();
      localStorage.setItem(STORAGE_KEY, locale);
    });
  }

  private initialLocale(): Locale {
    const stored = localStorage.getItem(STORAGE_KEY) as Locale | null;
    if (stored && SUPPORTED.includes(stored)) {
      return stored;
    }
    const nav = (navigator.language || 'fr').slice(0, 2) as Locale;
    return SUPPORTED.includes(nav) ? nav : 'fr';
  }

  /** Charge le catalogue de la locale courante (appelé au bootstrap et au changement). */
  async load(locale: Locale = this.locale()): Promise<void> {
    if (!this.catalogs.has(locale)) {
      const data = await firstValueFrom(
        this.http.get<Record<string, string>>(`/i18n/${locale}.json`),
      );
      this.catalogs.set(locale, data);
    }
    this.version.update((v) => v + 1);
  }

  async setLocale(locale: Locale): Promise<void> {
    await this.load(locale);
    this.locale.set(locale);
  }

  /** Traduction avec paramètres positionnels {0}, {1}. Clé absente -> !clé. */
  t(key: string, ...params: unknown[]): string {
    const catalog = this.catalogs.get(this.locale()) ?? this.catalogs.get('fr');
    let value = catalog?.[key];
    if (value === undefined) {
      return `!${key}`;
    }
    params.forEach((p, i) => {
      value = value!.split(`{${i}}`).join(String(p));
    });
    return value;
  }

  /** Libellé localisé d'un objet {fr, ar, en} avec repli sur le français. */
  label(labels: Partial<Record<Locale, string>> | undefined): string {
    if (!labels) {
      return '';
    }
    return labels[this.locale()] || labels.fr || '';
  }

  /** Nom localisé d'une équipe (fr/ar seulement ; l'anglais replie sur le français). */
  deptName(dept: { nameFr: string; nameAr: string } | null | undefined): string {
    if (!dept) {
      return '';
    }
    return this.locale() === 'ar' ? dept.nameAr : dept.nameFr;
  }
}

/** Directionality CDK pilotée par la locale : overlays/menus/datepicker corrects en RTL. */
@Injectable()
export class CcDirectionality extends Directionality {
  private readonly i18n = inject(I18nService);
  override get value(): Direction {
    return this.i18n.dir();
  }
}
