import { Component, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatListModule } from '@angular/material/list';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { BreakpointObserver } from '@angular/cdk/layout';
import { toSignal } from '@angular/core/rxjs-interop';
import { map } from 'rxjs';

import { AuthService } from '../../core/auth.service';
import { I18nService } from '../../core/i18n.service';
import { TPipe } from '../../core/t.pipe';

/** Back-office : sidenav blanche, item actif arrondi, en-tête avec identité et déconnexion. */
@Component({
  selector: 'cc-admin-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MatSidenavModule, MatListModule,
    MatToolbarModule, MatButtonModule, MatIconModule, TPipe],
  styles: `
    .container { min-height: 100vh; }
    mat-sidenav { inline-size: 240px; border: none; background: var(--cc-surface); padding: 12px; }
    .nav-item {
      display: flex; align-items: center; gap: 12px; padding: 10px 14px; border-radius: 12px;
      color: var(--cc-text-muted); text-decoration: none; font-weight: 500; margin-block: 2px;
    }
    .nav-item.active { background: var(--cc-primary); color: white; }
    .nav-item:hover:not(.active) { background: var(--cc-primary-soft); color: var(--cc-primary); }
    mat-toolbar { background: transparent; gap: 8px; }
    .content { padding: 16px 20px; }
    .who { font-size: 13px; color: var(--cc-text-muted); }
  `,
  template: `
    <mat-sidenav-container class="container">
      <mat-sidenav [mode]="isMobile() ? 'over' : 'side'" [opened]="!isMobile()" #nav>
        <a routerLink="/" style="font-weight:800;color:var(--cc-primary);font-size:18px;
           text-decoration:none;display:block;padding:12px 14px">CivicCare</a>
        @for (item of items; track item.path) {
          <a class="nav-item" [routerLink]="item.path" routerLinkActive="active"
             [routerLinkActiveOptions]="{ exact: item.exact }" (click)="isMobile() && nav.close()">
            <mat-icon>{{ item.icon }}</mat-icon> {{ item.label | t }}
          </a>
        }
      </mat-sidenav>
      <mat-sidenav-content>
        <mat-toolbar>
          @if (isMobile()) {
            <button mat-icon-button (click)="nav.toggle()" aria-label="menu">
              <mat-icon>menu</mat-icon>
            </button>
          }
          <span style="flex:1"></span>
          <a mat-button routerLink="/">{{ 'nav.explore' | t }}</a>
          <span class="who">{{ auth.me().displayName }}</span>
          <button mat-stroked-button (click)="logout()">
            <mat-icon>logout</mat-icon> {{ 'nav.logout' | t }}
          </button>
        </mat-toolbar>
        <div class="content admin-dense"><router-outlet /></div>
      </mat-sidenav-content>
    </mat-sidenav-container>
  `,
})
export class AdminShell {
  readonly auth = inject(AuthService);
  readonly i18n = inject(I18nService);
  private readonly router = inject(Router);
  private readonly breakpoints = inject(BreakpointObserver);

  readonly isMobile = toSignal(
    this.breakpoints.observe('(max-width: 900px)').pipe(map((s) => s.matches)),
    { initialValue: false });

  readonly items = [
    { path: '/admin', icon: 'dashboard', label: 'admin.nav.dashboard', exact: true },
    { path: '/admin/reports', icon: 'assignment', label: 'admin.nav.reports', exact: false },
    { path: '/admin/moderation', icon: 'visibility', label: 'admin.nav.moderation', exact: false },
    { path: '/admin/catalog', icon: 'category', label: 'admin.nav.catalog', exact: false },
    { path: '/admin/departments', icon: 'groups', label: 'admin.nav.departments', exact: false },
    { path: '/admin/users', icon: 'manage_accounts', label: 'admin.nav.users', exact: false },
    { path: '/admin/content', icon: 'description', label: 'admin.nav.content', exact: false },
    { path: '/admin/contact', icon: 'inbox', label: 'admin.nav.contact', exact: false },
    { path: '/admin/settings', icon: 'settings', label: 'admin.nav.settings', exact: false },
  ];

  async logout(): Promise<void> {
    await this.auth.logout();
    void this.router.navigate(['/']);
  }
}
