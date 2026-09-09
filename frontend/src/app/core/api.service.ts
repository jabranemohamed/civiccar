import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  AdminCatalog, AdminContentPage, AdminReportDetail, AdminSettings, AppConfig, AuditEntry,
  CatalogGroup, ContactMessage, ContentPage, DashboardStats, Department, DuplicateCandidate,
  GeocodeResult, MapPoint, PublicationStatus, ReportDetail, ReportSummary, SearchFilters,
  SearchResult, WorkQueueRow, WorkflowStatus,
} from './api.types';

/**
 * Client HTTP typé du contrat /api/v1 (chemins relatifs même origine : le cookie de
 * session HttpOnly et l'intercepteur XSRF d'Angular s'appliquent automatiquement).
 */
@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);

  // ===== Public =====

  config(): Observable<AppConfig> {
    return this.http.get<AppConfig>('/api/v1/config');
  }

  catalog(): Observable<CatalogGroup[]> {
    return this.http.get<CatalogGroup[]>('/api/v1/catalog');
  }

  content(slug: string): Observable<ContentPage> {
    return this.http.get<ContentPage>(`/api/v1/content/${slug}`);
  }

  private filterParams(filters: SearchFilters): HttpParams {
    let params = new HttpParams();
    if (filters.text) params = params.set('text', filters.text);
    if (filters.groupId) params = params.set('groupId', filters.groupId);
    if (filters.status) params = params.set('status', filters.status);
    if (filters.from) params = params.set('from', filters.from);
    if (filters.to) params = params.set('to', filters.to);
    if (filters.includeArchived) params = params.set('includeArchived', 'true');
    return params;
  }

  searchReports(filters: SearchFilters, page: number, size: number): Observable<SearchResult> {
    const params = this.filterParams(filters).set('page', page).set('size', size);
    return this.http.get<SearchResult>('/api/v1/reports', { params });
  }

  mapPoints(bbox: [number, number, number, number], filters: SearchFilters): Observable<MapPoint[]> {
    const params = this.filterParams(filters)
      .set('west', bbox[0]).set('south', bbox[1]).set('east', bbox[2]).set('north', bbox[3]);
    return this.http.get<MapPoint[]>('/api/v1/reports/map', { params });
  }

  reportDetail(reference: string, lang: string): Observable<ReportDetail> {
    return this.http.get<ReportDetail>(`/api/v1/reports/${reference}`,
      { params: new HttpParams().set('lang', lang) });
  }

  duplicates(typeId: string, lon: number, lat: number): Observable<DuplicateCandidate[]> {
    const params = new HttpParams().set('typeId', typeId).set('lon', lon).set('lat', lat);
    return this.http.get<DuplicateCandidate[]>('/api/v1/reports/duplicates', { params });
  }

  boundaryCheck(lon: number, lat: number): Observable<{ inside: boolean }> {
    return this.http.get<{ inside: boolean }>('/api/v1/boundary/check',
      { params: new HttpParams().set('lon', lon).set('lat', lat) });
  }

  geocode(query: string, lang: string): Observable<GeocodeResult[]> {
    return this.http.get<GeocodeResult[]>('/api/v1/geocode',
      { params: new HttpParams().set('q', query).set('lang', lang) });
  }

  reverseGeocode(lon: number, lat: number, lang: string): Observable<GeocodeResult | null> {
    return this.http.get<GeocodeResult | null>('/api/v1/geocode/reverse',
      { params: new HttpParams().set('lon', lon).set('lat', lat).set('lang', lang) });
  }

  createReport(data: object, photos: File[]): Observable<{ reference: string }> {
    const form = new FormData();
    form.append('data', new Blob([JSON.stringify(data)], { type: 'application/json' }));
    photos.forEach((photo) => form.append('photos', photo, photo.name));
    return this.http.post<{ reference: string }>('/api/v1/reports', form);
  }

  bookmarks(): Observable<{ items: ReportSummary[] }> {
    return this.http.get<{ items: ReportSummary[] }>('/api/v1/bookmarks');
  }

  isBookmarked(reportId: string): Observable<{ bookmarked: boolean }> {
    return this.http.get<{ bookmarked: boolean }>(`/api/v1/bookmarks/${reportId}`);
  }

  toggleBookmark(reportId: string): Observable<{ bookmarked: boolean }> {
    return this.http.post<{ bookmarked: boolean }>(`/api/v1/bookmarks/${reportId}/toggle`, {});
  }

  subscribe(reportId: string, reference: string, email: string): Observable<void> {
    return this.http.post<void>('/api/v1/subscriptions', { reportId, reference, email, consent: true });
  }

  confirmSubscription(token: string): Observable<{ ok: boolean }> {
    return this.http.post<{ ok: boolean }>(`/api/v1/subscriptions/confirm/${token}`, {});
  }

  unsubscribe(token: string): Observable<{ ok: boolean }> {
    return this.http.post<{ ok: boolean }>(`/api/v1/subscriptions/unsubscribe/${token}`, {});
  }

  sendContact(payload: {
    name: string; email: string; message: string;
    copyRequested: boolean; consent: boolean; website: string;
  }): Observable<void> {
    return this.http.post<void>('/api/v1/contact', payload);
  }

  // ===== Administration =====

  adminDashboard(): Observable<DashboardStats> {
    return this.http.get<DashboardStats>('/api/v1/admin/dashboard');
  }

  adminWorkQueue(filter: {
    status?: WorkflowStatus; publication?: PublicationStatus; departmentId?: string;
    onlyMine?: boolean; onlyUnassigned?: boolean;
  }, page: number, size: number): Observable<{ total: number; items: WorkQueueRow[] }> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (filter.status) params = params.set('status', filter.status);
    if (filter.publication) params = params.set('publication', filter.publication);
    if (filter.departmentId) params = params.set('departmentId', filter.departmentId);
    if (filter.onlyMine) params = params.set('onlyMine', 'true');
    if (filter.onlyUnassigned) params = params.set('onlyUnassigned', 'true');
    return this.http.get<{ total: number; items: WorkQueueRow[] }>('/api/v1/admin/reports', { params });
  }

  adminReport(id: string): Observable<AdminReportDetail> {
    return this.http.get<AdminReportDetail>(`/api/v1/admin/reports/${id}`);
  }

  adminAssign(id: string, departmentId: string | null, assigneeId: string | null,
              expectedVersion: number): Observable<void> {
    return this.http.post<void>(`/api/v1/admin/reports/${id}/assign`,
      { departmentId, assigneeId, expectedVersion });
  }

  adminChangeStatus(id: string, target: WorkflowStatus, reason: string | null,
                    expectedVersion: number): Observable<void> {
    return this.http.post<void>(`/api/v1/admin/reports/${id}/status`,
      { target, reason, expectedVersion });
  }

  adminAddPublicUpdate(id: string, body: string): Observable<void> {
    return this.http.post<void>(`/api/v1/admin/reports/${id}/public-update`, { body });
  }

  adminAddNote(id: string, body: string): Observable<void> {
    return this.http.post<void>(`/api/v1/admin/reports/${id}/note`, { body });
  }

  adminSetPublication(id: string, status: PublicationStatus, reason: string | null): Observable<void> {
    return this.http.post<void>(`/api/v1/admin/reports/${id}/publication`, { status, reason });
  }

  adminEditPublicText(id: string, text: string): Observable<void> {
    return this.http.post<void>(`/api/v1/admin/reports/${id}/public-text`, { text });
  }

  adminChangeCategory(id: string, typeId: string): Observable<void> {
    return this.http.post<void>(`/api/v1/admin/reports/${id}/category`, { typeId });
  }

  adminMarkDuplicate(id: string, canonicalReference: string): Observable<void> {
    return this.http.post<void>(`/api/v1/admin/reports/${id}/duplicate`, { canonicalReference });
  }

  adminModerateMedia(id: string, mediaId: string, approve: boolean, reason: string | null): Observable<void> {
    return this.http.post<void>(`/api/v1/admin/reports/${id}/media/${mediaId}`, { approve, reason });
  }

  adminDepartments(): Observable<Department[]> {
    return this.http.get<Department[]>('/api/v1/admin/departments');
  }

  adminAgentsOf(departmentId: string): Observable<{ id: string; displayName: string }[]> {
    return this.http.get<{ id: string; displayName: string }[]>(
      `/api/v1/admin/departments/${departmentId}/agents`);
  }

  adminCatalog(): Observable<AdminCatalog> {
    return this.http.get<AdminCatalog>('/api/v1/admin/catalog');
  }

  adminSetTypeActive(typeId: string, active: boolean): Observable<void> {
    return this.http.patch<void>(`/api/v1/admin/catalog/types/${typeId}`, { active });
  }

  adminUpdateRouting(ruleId: string, departmentId: string, active: boolean): Observable<void> {
    return this.http.patch<void>(`/api/v1/admin/catalog/rules/${ruleId}`, { departmentId, active });
  }

  adminUsers(): Observable<import('./api.types').AdminUser[]> {
    return this.http.get<import('./api.types').AdminUser[]>('/api/v1/admin/users');
  }

  adminCreateUser(payload: {
    username: string; password: string; displayName: string;
    roles: string[]; departmentIds: string[];
  }): Observable<{ id: string }> {
    return this.http.post<{ id: string }>('/api/v1/admin/users', payload);
  }

  adminSetUserEnabled(id: string, enabled: boolean): Observable<void> {
    return this.http.patch<void>(`/api/v1/admin/users/${id}`, { enabled });
  }

  adminContent(): Observable<AdminContentPage[]> {
    return this.http.get<AdminContentPage[]>('/api/v1/admin/content');
  }

  adminUpdateContent(slug: string, page: Omit<AdminContentPage, 'slug'>): Observable<void> {
    return this.http.put<void>(`/api/v1/admin/content/${slug}`, page);
  }

  adminContactMessages(): Observable<ContactMessage[]> {
    return this.http.get<ContactMessage[]>('/api/v1/admin/contact-messages');
  }

  adminMarkContactProcessed(id: string): Observable<void> {
    return this.http.post<void>(`/api/v1/admin/contact-messages/${id}/processed`, {});
  }

  adminSettings(): Observable<AdminSettings> {
    return this.http.get<AdminSettings>('/api/v1/admin/settings');
  }

  adminAudit(): Observable<AuditEntry[]> {
    return this.http.get<AuditEntry[]>('/api/v1/admin/audit');
  }
}
