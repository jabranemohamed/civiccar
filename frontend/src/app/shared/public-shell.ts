import { Component, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { I18nService, Locale } from '../core/i18n.service';
import { ConfigService } from '../core/config.service';
import { AuthService } from '../core/auth.service';
import { TPipe } from '../core/t.pipe';

/** Coquille publique : en-tête clair, navigation, sélecteur FR/AR/EN, CTA, pied de page. */
@Component({
  selector: 'cc-public-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MatToolbarModule, MatButtonModule,
    MatIconModule, MatMenuModule, TPipe],
  styles: `
    .shell { min-height: 100vh; display: flex; flex-direction: column; }
    mat-toolbar {
      background: var(--cc-surface);
      box-shadow: var(--cc-shadow);
      gap: 6px;
      flex-wrap: wrap;
      padding-block: 6px;
      height: auto;
      min-height: 64px;
    }
    .brand { color: var(--cc-primary); font-weight: 800; font-size: 19px; text-decoration: none; }
    nav { display: flex; gap: 2px; flex-wrap: wrap; }
    nav a {
      color: var(--cc-text-muted); text-decoration: none; font-size: 14px; font-weight: 500;
      padding: 8px 12px; border-radius: 10px;
    }
    nav a.active, nav a:hover { color: var(--cc-primary); background: var(--cc-primary-soft); }
    .spacer { flex: 1; }
    .content { flex: 1; display: flex; flex-direction: column; }
    footer {
      padding: 14px 24px; color: var(--cc-text-muted); font-size: 12px; text-align: center;
    }
  `,
  template: `
    <div class="shell">
      <mat-toolbar>
        <a routerLink="/" class="brand">{{ appName }}</a>
        <nav aria-label="navigation">
          <a routerLink="/" routerLinkActive="active" [routerLinkActiveOptions]="{ exact: true }">{{ 'nav.explore' | t }}</a>
          <a routerLink="/following" routerLinkActive="active">{{ 'nav.following' | t }}</a>
          <a routerLink="/info" routerLinkActive="active">{{ 'nav.info' | t }}</a>
          <a routerLink="/contact" routerLinkActive="active">{{ 'nav.contact' | t }}</a>
          @if (auth.isAuthenticated()) {
            <a routerLink="/admin" routerLinkActive="active">{{ 'nav.admin' | t }}</a>
          } @else {
            <a routerLink="/login" routerLinkActive="active">{{ 'nav.login' | t }}</a>
          }
        </nav>
        <span class="spacer"></span>
        <button mat-button [matMenuTriggerFor]="langMenu" [attr.aria-label]="'app.language' | t">
          <mat-icon>language</mat-icon>
          {{ localeLabel(i18n.locale()) }}
        </button>
        <mat-menu #langMenu="matMenu">
          @for (loc of locales; track loc) {
            <button mat-menu-item (click)="setLocale(loc)">{{ localeLabel(loc) }}</button>
          }
        </mat-menu>
        <button mat-flat-button (click)="router.navigate(['/report'])" id="cta-report">
          <mat-icon>campaign</mat-icon>
          {{ 'home.report.cta' | t }}
        </button>
      </mat-toolbar>
      <div class="content"><router-outlet /></div>
      <footer>{{ 'footer.demo' | t }}</footer>
    </div>
  `,
})
export class PublicShell {
  readonly i18n = inject(I18nService);
  readonly auth = inject(AuthService);
  readonly router = inject(Router);
  private readonly config = inject(ConfigService);

  readonly locales: Locale[] = ['fr', 'ar', 'en'];

  get appName(): string {
    return this.config.get()?.appName ?? 'CivicCare Tunis';
  }

  localeLabel(locale: Locale): string {
    return { fr: 'Français', ar: 'العربية', en: 'English' }[locale];
  }

  setLocale(locale: Locale): void {
    void this.i18n.setLocale(locale);
  }
}
