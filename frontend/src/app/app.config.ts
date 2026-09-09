import {
  ApplicationConfig, inject, provideAppInitializer, provideBrowserGlobalErrorListeners,
  provideZonelessChangeDetection,
} from '@angular/core';
import { registerLocaleData } from '@angular/common';
import localeFr from '@angular/common/locales/fr';
import localeArTn from '@angular/common/locales/ar-TN';
import { provideHttpClient, withInterceptors, withXsrfConfiguration } from '@angular/common/http';
import { provideRouter, withComponentInputBinding, withInMemoryScrolling } from '@angular/router';
import { provideNativeDateAdapter } from '@angular/material/core';
import { MatIconRegistry } from '@angular/material/icon';
import { MatPaginatorIntl } from '@angular/material/paginator';
import { Directionality } from '@angular/cdk/bidi';

import { routes } from './app.routes';
import { CcDirectionality, I18nService } from './core/i18n.service';
import { CcPaginatorIntl } from './core/paginator-intl';
import { ConfigService } from './core/config.service';
import { AuthService } from './core/auth.service';
import { telemetryInterceptor, initTelemetry } from './core/telemetry';

// Données de locale pour les pipes date/number : sans cet enregistrement, DatePipe lève
// NG02100 en «fr»/«ar» et casse le rendu des vues qui l'utilisent (lignes de tables vides).
// «en» (en-US) est intégré ; l'arabe utilise les formats tunisiens sous l'alias «ar».
registerLocaleData(localeFr);
registerLocaleData(localeArTn, 'ar');

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideZonelessChangeDetection(),
    provideRouter(routes, withComponentInputBinding(),
      withInMemoryScrolling({ scrollPositionRestoration: 'enabled' })),
    // Chemins relatifs même origine : l'intercepteur XSRF d'Angular pose X-XSRF-TOKEN
    // depuis le cookie XSRF-TOKEN (jamais sur des URL absolues cross-origin).
    provideHttpClient(
      withXsrfConfiguration({ cookieName: 'XSRF-TOKEN', headerName: 'X-XSRF-TOKEN' }),
      withInterceptors([telemetryInterceptor]),
    ),
    provideNativeDateAdapter(),
    // Direction dynamique FR/AR pour tous les overlays CDK/Material
    { provide: Directionality, useClass: CcDirectionality },
    // Paginator localisé (FR/AR/EN, mis à jour à la bascule de langue)
    { provide: MatPaginatorIntl, useClass: CcPaginatorIntl },
    // Bootstrap : catalogue i18n, configuration serveur (émet aussi le cookie XSRF),
    // session courante, icônes Material Symbols auto-hébergées, télémétrie optionnelle.
    provideAppInitializer(async () => {
      inject(MatIconRegistry).setDefaultFontSetClass('material-symbols-outlined');
      initTelemetry();
      const i18n = inject(I18nService);
      const config = inject(ConfigService);
      const auth = inject(AuthService);
      await Promise.all([i18n.load(), config.load(), auth.resolve()]);
    }),
  ],
};
