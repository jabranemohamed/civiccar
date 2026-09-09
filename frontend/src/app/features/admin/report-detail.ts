import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { firstValueFrom } from 'rxjs';

import { ApiService } from '../../core/api.service';
import { AdminReportDetail, Department, ProblemDetails, WorkflowStatus } from '../../core/api.types';
import { AuthService } from '../../core/auth.service';
import { I18nService } from '../../core/i18n.service';
import { TPipe } from '../../core/t.pipe';
import { StatusChip } from '../../shared/status-chip';

/**
 * Dossier interne : contact privé, textes source/public séparés, médias, affectation,
 * transitions (motif requis en réouverture), modération (MODERATOR+), doublon, notes.
 * Les mutations portent expectedVersion : un conflit concurrent affiche un 409 propre
 * et recharge sans écraser l'autre agent.
 */
@Component({
  selector: 'cc-admin-report-detail',
  imports: [DatePipe, ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatIconModule,
    MatInputModule, MatSelectModule, MatProgressSpinnerModule, TPipe, StatusChip],
  styles: `
    .page { display: flex; flex-direction: column; gap: 14px; max-inline-size: 1000px; }
    // Rangées d'actions : centrage vertical + espacement entre rangées (les labels
    // flottants Material dépassent du champ : sans marge, ils chevauchent la rangée
    // précédente lors du retour à la ligne).
    .row {
      display: flex; gap: 12px 16px; flex-wrap: wrap; align-items: center;
    }
    .row + .row { margin-block-start: 16px; }
    .row mat-form-field { min-inline-size: 220px; }
    .row button { flex-shrink: 0; }
    .gallery { display: flex; gap: 12px; flex-wrap: wrap; }
    .gallery img { inline-size: 130px; border-radius: 10px; display: block; }
    .private { background: var(--cc-bg); }
    h2 { font-size: 16px; margin: 0 0 8px; }
  `,
  template: `
    <div class="page">
      @if (loading()) {
        <mat-progress-spinner mode="indeterminate" diameter="36" />
      } @else if (!detail()) {
        <h1>{{ 'detail.notFound' | t }}</h1>
      } @else {
        @let d = detail()!;
        <div class="cc-card">
          <h1 style="margin:0">
            <span class="cc-bidi">{{ d.reference }}</span> — {{ d.type.labelFr }}</h1>
          <div class="row" style="margin-top:8px">
            <cc-status-chip [status]="d.workflowStatus" [archived]="!!d.archivedAt" />
            <cc-status-chip [status]="d.publicationStatus" kind="publication" />
            <span class="cc-muted">{{ d.createdAt | date: 'medium' : undefined : i18n.locale() }}</span>
            <span class="cc-muted cc-bidi">{{ d.position.lat.toFixed(5) }}, {{ d.position.lon.toFixed(5) }}</span>
            @if (d.address) { <span class="cc-muted">· {{ d.address }}</span> }
            @if (d.duplicateOf) {
              <span class="cc-chip cc-chip--out_of_scope">
                {{ 'admin.report.markDuplicate' | t }} → {{ d.duplicateOf }}</span>
            }
          </div>
        </div>

        <!-- Contact privé -->
        <div class="cc-card private">
          <h2>{{ 'admin.report.contact' | t }}</h2>
          @if (d.contact?.purged) {
            <div>{{ 'admin.report.contact.purged' | t }}</div>
          } @else if (d.contact) {
            <div>E-mail : <span class="cc-bidi">{{ d.contact.email || '—' }}</span></div>
            <div>{{ 'wizard.contact.phone' | t }} :
              <span class="cc-bidi">{{ d.contact.phone || '—' }}</span></div>
            @if (d.contact.vehiclePlate) {
              <div>{{ 'admin.report.plate' | t }} :
                <span class="cc-bidi">{{ d.contact.vehiclePlate }}</span></div>
            }
          }
        </div>

        <!-- Textes -->
        <div class="cc-card">
          <h2>{{ 'admin.report.privateDescription' | t }}</h2>
          <p style="white-space:pre-wrap">{{ d.descriptionPrivate || '—' }}</p>
          <h2>{{ 'admin.report.publicDescription' | t }}</h2>
          @if (auth.isModerator()) {
            <mat-form-field appearance="outline" style="inline-size:100%">
              <textarea matInput [formControl]="publicText" rows="3" maxlength="400"></textarea>
            </mat-form-field>
            <button mat-stroked-button (click)="savePublicText()">
              {{ 'admin.moderation.editText' | t }}</button>
          } @else {
            <p style="white-space:pre-wrap">{{ d.descriptionPublic || '—' }}</p>
          }
          @for (entry of fieldValues(); track entry[0]) {
            <div class="cc-muted">{{ entry[0] }} : {{ entry[1] }}</div>
          }
        </div>

        <!-- Médias -->
        @if (d.media.length > 0) {
          <div class="cc-card">
            <h2>{{ 'detail.photos' | t }}</h2>
            <div class="gallery">
              @for (m of d.media; track m.id) {
                <div>
                  <a [href]="m.full" target="_blank" rel="noopener"><img [src]="m.thumb" alt="" /></a>
                  <cc-status-chip [status]="m.moderationStatus" kind="publication" />
                  @if (auth.isModerator()) {
                    <div class="row">
                      @if (m.moderationStatus !== 'APPROVED') {
                        <button mat-button (click)="moderateMedia(m.id, true)">
                          {{ 'admin.moderation.approveMedia' | t }}</button>
                      }
                      @if (m.moderationStatus !== 'REMOVED') {
                        <button mat-button color="warn" (click)="moderateMedia(m.id, false)">
                          {{ 'admin.moderation.removeMedia' | t }}</button>
                      }
                    </div>
                  }
                </div>
              }
            </div>
          </div>
        }

        <!-- Modération de publication (MODERATOR+) -->
        @if (auth.isModerator()) {
          <div class="cc-card">
            <h2>{{ 'admin.moderation.title' | t }}</h2>
            <div class="row">
              <mat-form-field appearance="outline" subscriptSizing="dynamic">
                <mat-label>{{ 'admin.moderation.reason' | t }}</mat-label>
                <input matInput [formControl]="moderationReason" />
              </mat-form-field>
              <button mat-flat-button id="publish-button" (click)="setPublication('PUBLISHED')">
                {{ 'admin.moderation.publish' | t }}</button>
              <button mat-stroked-button color="warn" (click)="setPublication('HIDDEN')">
                {{ 'admin.moderation.hide' | t }}</button>
            </div>
            <div class="row">
              <mat-form-field appearance="outline" subscriptSizing="dynamic" style="inline-size:340px">
                <mat-label>{{ 'admin.report.changeCategory' | t }}</mat-label>
                <mat-select [formControl]="newTypeId">
                  @for (type of catalogTypes(); track type.id) {
                    <mat-option [value]="type.id">{{ type.groupLabelFr }} — {{ type.labelFr }}</mat-option>
                  }
                </mat-select>
              </mat-form-field>
              <button mat-stroked-button (click)="changeCategory()">{{ 'common.confirm' | t }}</button>
            </div>
          </div>
        }

        <!-- Affectation -->
        <div class="cc-card">
          <h2>{{ 'admin.report.assign' | t }}</h2>
          <div class="row">
            <mat-form-field appearance="outline" subscriptSizing="dynamic" style="inline-size:260px">
              <mat-label>{{ 'admin.report.assignDepartment' | t }}</mat-label>
              <mat-select [formControl]="departmentControl"
                          (valueChange)="loadAgents($event)">
                @for (dept of departments(); track dept.id) {
                  <mat-option [value]="dept.id">{{ dept.nameFr }}</mat-option>
                }
              </mat-select>
            </mat-form-field>
            <mat-form-field appearance="outline" subscriptSizing="dynamic" style="inline-size:240px">
              <mat-label>{{ 'admin.report.assignAgent' | t }}</mat-label>
              <mat-select [formControl]="assigneeControl">
                <mat-option [value]="null">{{ 'admin.reports.unassigned' | t }}</mat-option>
                @for (agent of agents(); track agent.id) {
                  <mat-option [value]="agent.id">{{ agent.displayName }}</mat-option>
                }
              </mat-select>
            </mat-form-field>
            <button mat-flat-button id="assign-button" (click)="assign()">
              {{ 'admin.report.assign' | t }}</button>
          </div>
        </div>

        <!-- Changement de statut + doublon -->
        <div class="cc-card">
          <h2>{{ 'admin.report.changeStatus' | t }}</h2>
          <div class="row">
            <mat-form-field appearance="outline" subscriptSizing="dynamic" style="inline-size:280px">
              <mat-label>{{ 'admin.reports.status' | t }}</mat-label>
              <mat-select [formControl]="targetStatus" id="status-select">
                @for (target of d.allowedTransitions; track target) {
                  <mat-option [value]="target">{{ 'status.' + target | t }}</mat-option>
                }
              </mat-select>
            </mat-form-field>
            <mat-form-field appearance="outline" subscriptSizing="dynamic" style="inline-size:280px">
              <mat-label>{{ 'admin.report.statusReason' | t }}</mat-label>
              <input matInput [formControl]="statusReason" />
            </mat-form-field>
            <button mat-flat-button id="apply-status" (click)="changeStatus()">
              {{ 'common.confirm' | t }}</button>
          </div>
          @if (!d.duplicateOf) {
            <div class="row">
              <mat-form-field appearance="outline" subscriptSizing="dynamic">
                <mat-label>{{ 'admin.report.duplicateOf' | t }}</mat-label>
                <input matInput [formControl]="canonicalRef" />
                @if (canonicalRef.hasError('server')) {
                  <mat-error>{{ 'common.error' | t }}</mat-error>
                }
              </mat-form-field>
              <button mat-stroked-button (click)="markDuplicate()">
                {{ 'admin.report.markDuplicate' | t }}</button>
            </div>
          }
        </div>

        <!-- Messages publics -->
        <div class="cc-card">
          <h2>{{ 'admin.report.publicUpdates' | t }}</h2>
          @for (update of d.publicUpdates; track update.at) {
            <div class="cc-timeline-entry">
              <div class="cc-timeline-date">
                {{ update.at | date: 'medium' : undefined : i18n.locale() }}</div>
              <div style="white-space:pre-wrap">{{ update.body }}</div>
            </div>
          }
          <mat-form-field appearance="outline" style="inline-size:100%">
            <mat-label>{{ 'admin.report.addUpdate' | t }}</mat-label>
            <textarea matInput [formControl]="newUpdate" rows="2" maxlength="1000"></textarea>
          </mat-form-field>
          <button mat-stroked-button (click)="addUpdate()">{{ 'admin.report.addUpdate' | t }}</button>
        </div>

        <!-- Notes internes -->
        <div class="cc-card private">
          <h2>{{ 'admin.report.internalNotes' | t }}</h2>
          @for (note of d.internalNotes; track note.at) {
            <div class="cc-timeline-entry">
              <div class="cc-timeline-date">
                {{ note.at | date: 'medium' : undefined : i18n.locale() }}</div>
              <div style="white-space:pre-wrap">{{ note.body }}</div>
            </div>
          }
          <mat-form-field appearance="outline" style="inline-size:100%">
            <mat-label>{{ 'admin.report.addNote' | t }}</mat-label>
            <textarea matInput [formControl]="newNote" rows="2" maxlength="2000"></textarea>
          </mat-form-field>
          <button mat-stroked-button (click)="addNote()">{{ 'admin.report.addNote' | t }}</button>
        </div>

        <!-- Historique -->
        <div class="cc-card">
          <h2>{{ 'detail.timeline' | t }}</h2>
          @for (event of d.statusHistory; track event.at) {
            <div class="cc-timeline-entry">
              <div class="cc-timeline-date">
                {{ event.at | date: 'medium' : undefined : i18n.locale() }}</div>
              <strong>
                @if (event.from) { {{ 'status.' + event.from | t }} → }
                {{ 'status.' + event.to | t }}
              </strong>
              @if (event.reason) { <span class="cc-muted"> ({{ event.reason }})</span> }
            </div>
          }
        </div>
      }
    </div>
  `,
})
export class AdminReportDetailPage implements OnInit {
  private readonly api = inject(ApiService);
  private readonly snackBar = inject(MatSnackBar);
  readonly auth = inject(AuthService);
  readonly i18n = inject(I18nService);

  readonly id = input.required<string>();

  readonly detail = signal<AdminReportDetail | null>(null);
  readonly loading = signal(true);
  readonly departments = signal<Department[]>([]);
  readonly agents = signal<{ id: string; displayName: string }[]>([]);
  readonly catalogTypes = signal<
    { id: string; code: string; groupLabelFr: string; labelFr: string }[]>([]);

  readonly publicText = new FormControl('', { nonNullable: true });
  readonly moderationReason = new FormControl('', { nonNullable: true });
  readonly newTypeId = new FormControl<string | null>(null);
  readonly departmentControl = new FormControl<string | null>(null);
  readonly assigneeControl = new FormControl<string | null>(null);
  readonly targetStatus = new FormControl<WorkflowStatus | null>(null);
  readonly statusReason = new FormControl('', { nonNullable: true });
  readonly canonicalRef = new FormControl('', { nonNullable: true });
  readonly newUpdate = new FormControl('', { nonNullable: true });
  readonly newNote = new FormControl('', { nonNullable: true });

  readonly fieldValues = computed(() => Object.entries(this.detail()?.fieldValues ?? {}));

  // L'input requis `id` (liaison de route) n'est disponible qu'à partir de ngOnInit,
  // pas dans le constructeur.
  ngOnInit(): void {
    void this.init();
  }

  private async init(): Promise<void> {
    const [departments] = await Promise.all([
      firstValueFrom(this.api.adminDepartments()),
      this.reload(),
    ]);
    this.departments.set(departments);
    if (this.auth.isModerator()) {
      const catalog = await firstValueFrom(this.api.adminCatalog());
      this.catalogTypes.set(catalog.types);
    }
    const dept = this.detail()?.department?.id ?? null;
    this.departmentControl.setValue(dept);
    if (dept) await this.loadAgents(dept);
    this.assigneeControl.setValue(this.detail()?.assignee?.id ?? null);
  }

  private async reload(): Promise<void> {
    this.loading.set(true);
    try {
      const detail = await firstValueFrom(this.api.adminReport(this.id()));
      this.detail.set(detail);
      this.publicText.setValue(detail.descriptionPublic ?? '');
      this.targetStatus.setValue(null);
    } catch {
      this.detail.set(null);
    } finally {
      this.loading.set(false);
    }
  }

  async loadAgents(departmentId: string | null): Promise<void> {
    this.agents.set(departmentId
      ? await firstValueFrom(this.api.adminAgentsOf(departmentId)) : []);
  }

  /** Exécute une mutation ; 409 -> message + rechargement sans écraser l'autre agent. */
  private async run(action: () => Promise<unknown>, errorTarget?: FormControl): Promise<void> {
    try {
      await action();
      await this.reload();
    } catch (error: unknown) {
      const problem = (error as { status?: number; error?: ProblemDetails });
      if (problem.status === 409) {
        this.snackBar.open(this.i18n.t('common.error') + ' — conflit de modification', undefined,
          { duration: 6000 });
        await this.reload();
      } else if (problem.error?.code === 'reopen.reasonRequired') {
        this.snackBar.open(this.i18n.t('admin.report.reopenReasonRequired'), undefined,
          { duration: 5000 });
      } else if (errorTarget) {
        errorTarget.setErrors({ server: true });
      } else {
        this.snackBar.open(this.i18n.t('common.error'), undefined, { duration: 5000 });
      }
    }
  }

  assign(): void {
    void this.run(() => firstValueFrom(this.api.adminAssign(this.id(),
      this.departmentControl.value, this.assigneeControl.value, this.detail()!.version)));
  }

  changeStatus(): void {
    if (!this.targetStatus.value) return;
    void this.run(() => firstValueFrom(this.api.adminChangeStatus(this.id(),
      this.targetStatus.value!, this.statusReason.value || null, this.detail()!.version)));
  }

  setPublication(status: 'PUBLISHED' | 'HIDDEN'): void {
    void this.run(() => firstValueFrom(this.api.adminSetPublication(this.id(), status,
      this.moderationReason.value || null)));
  }

  savePublicText(): void {
    void this.run(() => firstValueFrom(this.api.adminEditPublicText(this.id(), this.publicText.value)));
  }

  changeCategory(): void {
    if (!this.newTypeId.value) return;
    void this.run(() => firstValueFrom(this.api.adminChangeCategory(this.id(), this.newTypeId.value!)));
  }

  markDuplicate(): void {
    if (!this.canonicalRef.value) return;
    void this.run(() => firstValueFrom(this.api.adminMarkDuplicate(this.id(),
      this.canonicalRef.value)), this.canonicalRef);
  }

  moderateMedia(mediaId: string, approve: boolean): void {
    void this.run(() => firstValueFrom(this.api.adminModerateMedia(this.id(), mediaId, approve, null)));
  }

  addUpdate(): void {
    if (!this.newUpdate.value.trim()) return;
    void this.run(async () => {
      await firstValueFrom(this.api.adminAddPublicUpdate(this.id(), this.newUpdate.value));
      this.newUpdate.reset();
    });
  }

  addNote(): void {
    if (!this.newNote.value.trim()) return;
    void this.run(async () => {
      await firstValueFrom(this.api.adminAddNote(this.id(), this.newNote.value));
      this.newNote.reset();
    });
  }
}
