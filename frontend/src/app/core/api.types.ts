// Modèles de présentation du contrat /api/v1 (projections publiques uniquement).

export interface Labels {
  fr: string;
  ar: string;
  en: string;
}

export interface AppConfig {
  appName: string;
  timezone: string;
  map: {
    tileUrl: string;
    attribution: string;
    centerLat: number;
    centerLon: number;
    initialZoom: number;
    boundaryGeoJson: string | null;
    boundaryDemo: boolean;
  };
  emergency: { verified: boolean; numbers: { labelKey: string; value: string }[] };
  limits: {
    maxPhotos: number;
    maxPhotoBytes: number;
    descriptionMax: number;
    duplicateRadiusMeters: number;
  };
  geocoderDemo: boolean;
  locales: string[];
}

export interface FieldOption {
  code: string;
  labels: Labels;
}

export interface FieldDef {
  code: string;
  kind: 'TEXT' | 'SELECT';
  required: boolean;
  publicField: boolean;
  maxLen: number;
  labels: Labels;
  options: FieldOption[];
}

export interface ServiceTypeDto {
  id: string;
  code: string;
  labels: Labels;
  help: Labels;
  standardDescription: boolean;
  fields: FieldDef[];
}

export interface CatalogGroup {
  id: string;
  code: string;
  labels: Labels;
  types: ServiceTypeDto[];
}

export type WorkflowStatus = 'OPEN' | 'IN_PROGRESS' | 'DONE_OR_ORDERED' | 'OUT_OF_SCOPE';
export type PublicationStatus = 'PENDING_REVIEW' | 'PUBLISHED' | 'HIDDEN';

export interface ReportSummary {
  id: string;
  reference: string;
  typeLabels: Labels;
  groupCode: string;
  status: WorkflowStatus;
  archived: boolean;
  address: string | null;
  longitude: number;
  latitude: number;
  createdAt: string;
  thumbKey: string | null;
}

export interface SearchResult {
  total: number;
  items: ReportSummary[];
}

export interface MapPoint {
  id: string;
  reference: string;
  group: string;
  status: WorkflowStatus;
  lon: number;
  lat: number;
}

export interface TimelineEntry {
  at: string;
  status: WorkflowStatus | null;
  message: string | null;
}

export interface ReportDetail {
  id: string;
  reference: string;
  typeLabel: string;
  groupLabel: string;
  status: WorkflowStatus;
  archived: boolean;
  address: string;
  addressDetails: string;
  description: string;
  position: { lon: number; lat: number };
  createdAt: string;
  publicFields: Record<string, string>;
  media: { full: string; thumb: string }[];
  timeline: TimelineEntry[];
}

export interface DuplicateCandidate {
  summary: ReportSummary;
  distanceMeters: number;
}

export interface GeocodeResult {
  label: string;
  lon: number;
  lat: number;
}

export interface ContentPage {
  slug: string;
  title: Labels;
  body: Labels;
}

export interface Me {
  authenticated: boolean;
  username?: string;
  displayName?: string;
  roles?: string[];
  departments?: { id: string; code: string; nameFr: string }[];
}

export interface SearchFilters {
  text?: string;
  groupId?: string;
  status?: WorkflowStatus;
  from?: string;
  to?: string;
  includeArchived?: boolean;
}

// ===== Administration =====

export interface DashboardStats {
  total: number;
  open: number;
  inProgress: number;
  pendingReview: number;
  byDepartment: Record<string, number>;
  avgCloseDays: number | null;
  outboxBacklog: number;
  oldestOpenDays: number;
}

export interface WorkQueueRow {
  id: string;
  reference: string;
  typeLabelFr: string;
  typeLabelAr: string;
  typeLabelEn: string;
  status: WorkflowStatus;
  publication: PublicationStatus;
  departmentName: string | null;
  departmentNameAr: string | null;
  assigneeName: string | null;
  createdAt: string;
  longitude: number;
  latitude: number;
}

export interface AdminReportDetail {
  id: string;
  reference: string;
  version: number;
  type: { id: string; code: string; labelFr: string; labels: Labels };
  workflowStatus: WorkflowStatus;
  publicationStatus: PublicationStatus;
  allowedTransitions: WorkflowStatus[];
  department: { id: string; nameFr: string; nameAr: string } | null;
  assignee: { id: string; displayName: string } | null;
  address: string | null;
  position: { lon: number; lat: number };
  descriptionPrivate: string | null;
  descriptionPublic: string | null;
  fieldValues: Record<string, string>;
  createdAt: string;
  closedAt: string | null;
  archivedAt: string | null;
  personalDataPurged: boolean;
  contact?: { purged: boolean; email: string; phone: string; vehiclePlate: string };
  media: { id: string; thumb: string; full: string; moderationStatus: string }[];
  statusHistory: { at: string; from: string | null; to: string; reason: string | null }[];
  publicUpdates: { at: string; body: string }[];
  internalNotes: { at: string; body: string }[];
  duplicateOf?: string;
}

export interface Department {
  id: string;
  code: string;
  nameFr: string;
  nameAr: string;
  demo: boolean;
  active: boolean;
}

export interface AdminUser {
  id: string;
  username: string;
  displayName: string;
  enabled: boolean;
  roles: string[];
  departments: string[];
}

export interface AdminCatalog {
  types: {
    id: string;
    code: string;
    groupLabelFr: string;
    groupLabels: Labels;
    labelFr: string;
    labelAr: string;
    labelEn: string;
    active: boolean;
  }[];
  routingRules: { id: string; serviceTypeId: string; departmentId: string; active: boolean }[];
}

export interface AdminContentPage {
  slug: string;
  titleFr: string;
  titleAr: string;
  titleEn: string;
  bodyFr: string;
  bodyAr: string;
  bodyEn: string;
}

export interface ContactMessage {
  id: string;
  name: string;
  email: string;
  body: string;
  status: 'NEW' | 'PROCESSED';
  createdAt: string;
  copyRequested: boolean;
}

export interface AdminSettings {
  boundary: { code: string; nameFr: string; source: string; license: string; demo: boolean };
  timezone: string;
  countryCode: string;
  duplicateRadiusMeters: number;
  archiveAfterDays: number;
  purgeAfterDays: number;
  geocoderMode: string;
  tileUrl: string;
  open311Jurisdiction: string;
}

export interface AuditEntry {
  at: string;
  actor: string;
  action: string;
  targetType: string;
  targetId: string;
}

/** Corps d'erreur RFC 9457 renvoyé par l'API. */
export interface ProblemDetails {
  status: number;
  title?: string;
  code?: string;
  errors?: Record<string, string>;
}
