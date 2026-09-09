import {
  ApplicationConfig, inject, provideAppInitializer, provideBrowserGlobalErrorListeners,
  provideZonelessChangeDetection,
} from '@angular/core';
import { provideHttpClient, withInterceptors, withXsrfConfiguration } from '@angular/common/http';
import { provideRouter, withComponentInputBinding, withInMemoryScrolling } from '@angular/router';
import { provideNativeDateAdapter } from '@angular/material/core';
import { MatIconRegistry } from '@angular/material/icon';
import { Directionality } from '@angular/cdk/bidi';

import { routes } from './app.routes';
import { CcDirectionality, I18nService } from './core/i18n.service';
import { ConfigService } from './core/config.service';
import { AuthService } from './core/auth.service';
import { telemetryInterceptor, initTelemetry } from './core/telemetry';

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
