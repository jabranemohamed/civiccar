import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { I18nService } from './i18n.service';

describe('I18nService', () => {
  let service: I18nService;
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.removeItem('cc_locale');
    TestBed.configureTestingModule({
      providers: [provideZonelessChangeDetection(), provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(I18nService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function loadCatalog(locale: string, catalog: Record<string, string>): Promise<void> {
    const pending = service.load(locale as 'fr' | 'ar' | 'en');
    http.expectOne(`/i18n/${locale}.json`).flush(catalog);
    await pending;
  }

  it('traduit avec paramètres positionnels', async () => {
    await loadCatalog('fr', { 'wizard.photos.count': '{0} photo(s) sur {1}' });
    service.locale.set('fr');
    expect(service.t('wizard.photos.count', 2, 3)).toBe('2 photo(s) sur 3');
  });

  it('signale une clé absente sans lever', async () => {
    await loadCatalog('fr', {});
    expect(service.t('clé.inexistante')).toBe('!clé.inexistante');
  });

  it('bascule la direction en arabe et revient en LTR', async () => {
    await loadCatalog('fr', {});
    expect(service.dir()).toBe('ltr');
    const pending = service.setLocale('ar');
    http.expectOne('/i18n/ar.json').flush({});
    await pending;
    expect(service.locale()).toBe('ar');
    expect(service.dir()).toBe('rtl');
    TestBed.tick();
    expect(document.documentElement.dir).toBe('rtl');
    expect(document.documentElement.lang).toBe('ar');
  });

  it('replie les libellés catalogue sur le français', async () => {
    await loadCatalog('fr', {});
    const pending = service.setLocale('en');
    http.expectOne('/i18n/en.json').flush({});
    await pending;
    expect(service.label({ fr: 'Nid-de-poule', ar: 'حفرة' })).toBe('Nid-de-poule');
    expect(service.label({ fr: 'Nid-de-poule', en: 'Pothole' })).toBe('Pothole');
    expect(service.label(undefined)).toBe('');
  });
});
