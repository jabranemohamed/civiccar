import { Routes } from '@angular/router';
import { adminGuard } from './core/auth.guard';

/**
 * Chemins publics identiques à l'ancienne UI Vaadin : les liens e-mail
 * (/s/confirm, /s/unsubscribe), les références (/requests/{ref}) et les favoris
 * restent valides sans redirection.
 */
export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./shared/public-shell').then((m) => m.PublicShell),
    children: [
      { path: '', loadComponent: () => import('./features/explore/explore').then((m) => m.Explore) },
      { path: 'report', loadComponent: () => import('./features/report-form/report-form').then((m) => m.ReportForm) },
      { path: 'requests/:reference', loadComponent: () => import('./features/report-detail/report-detail').then((m) => m.ReportDetailPage) },
      { path: 'following', loadComponent: () => import('./features/following/following').then((m) => m.Following) },
      { path: 'info', loadComponent: () => import('./features/information/information').then((m) => m.Information) },
      { path: 'info/:slug', loadComponent: () => import('./features/information/information').then((m) => m.Information) },
      { path: 'contact', loadComponent: () => import('./features/contact/contact').then((m) => m.Contact) },
      { path: 's/confirm/:token', loadComponent: () => import('./features/subscriptions/subscription-action').then((m) => m.SubscriptionAction), data: { mode: 'confirm' } },
      { path: 's/unsubscribe/:token', loadComponent: () => import('./features/subscriptions/subscription-action').then((m) => m.SubscriptionAction), data: { mode: 'unsubscribe' } },
      { path: 'login', loadComponent: () => import('./features/auth/login').then((m) => m.Login) },
    ],
  },
  {
    path: 'admin',
    canActivate: [adminGuard],
    loadComponent: () => import('./features/admin/admin-shell').then((m) => m.AdminShell),
    children: [
      { path: '', loadComponent: () => import('./features/admin/dashboard').then((m) => m.AdminDashboard) },
      { path: 'reports', loadComponent: () => import('./features/admin/reports').then((m) => m.AdminReports) },
      { path: 'report/:id', loadComponent: () => import('./features/admin/report-detail').then((m) => m.AdminReportDetailPage) },
      { path: 'moderation', loadComponent: () => import('./features/admin/moderation').then((m) => m.AdminModeration) },
      { path: 'catalog', loadComponent: () => import('./features/admin/catalog').then((m) => m.AdminCatalogPage) },
      { path: 'departments', loadComponent: () => import('./features/admin/departments').then((m) => m.AdminDepartments) },
      { path: 'users', loadComponent: () => import('./features/admin/users').then((m) => m.AdminUsers) },
      { path: 'content', loadComponent: () => import('./features/admin/content').then((m) => m.AdminContent) },
      { path: 'contact', loadComponent: () => import('./features/admin/contact-inbox').then((m) => m.AdminContactInbox) },
      { path: 'settings', loadComponent: () => import('./features/admin/settings').then((m) => m.AdminSettingsPage) },
    ],
  },
  { path: '**', redirectTo: '' },
];
