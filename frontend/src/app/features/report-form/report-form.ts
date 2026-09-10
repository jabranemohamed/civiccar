import { Component, computed, inject, signal, viewChild } from '@angular/core';
import {
  FormField, email as emailValidator, form, maxLength, required, requiredError, validate,
} from '@angular/forms/signals';
import { Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatStepper, MatStepperModule } from '@angular/material/stepper';
import { MatExpansionModule } from '@angular/material/expansion';
import { firstValueFrom } from 'rxjs';

import { ApiService } from '../../core/api.service';
import {
  CatalogGroup, DuplicateCandidate, GeocodeResult, ProblemDetails, ServiceTypeDto,
} from '../../core/api.types';
import { ConfigService } from '../../core/config.service';
import { I18nService } from '../../core/i18n.service';
import { TPipe } from '../../core/t.pipe';
import { CcMap } from '../../shared/map';
import { businessSpan } from '../../core/telemetry';
import { DuplicatesDialog } from './duplicates-dialog';
import { markAllTouched } from '../../core/forms';

interface PendingPhoto {
  file: File;
  previewUrl: string;
}

/**
 * Assistant de dépôt en 4 étapes (MatStepper + Signal Forms) : position
 * (adresse/GPS/repère/coordonnées), type + champs conditionnels, détails + photos,
 * contact + consentement + récapitulatif. Doublons proposés avant envoi ; clé
 * d'idempotence par assistant ; erreurs serveur converties en erreurs de champ.
 */
@Component({
  selector: 'cc-report-form',
  imports: [FormField, RouterLink, MatButtonModule, MatCheckboxModule, MatDialogModule,
    MatFormFieldModule, MatIconModule, MatInputModule, MatSelectModule, MatStepperModule,
    MatExpansionModule, TPipe, CcMap],
  styles: `
    .page { max-inline-size: 920px; margin: 0 auto; padding: 20px 16px; }
    .step { display: flex; flex-direction: column; gap: 12px; padding-block: 12px; }
    .row { display: flex; gap: 10px; flex-wrap: wrap; align-items: baseline; }
    .geo-results { display: flex; flex-direction: column; align-items: flex-start; }
    .types { display: flex; flex-direction: column; gap: 10px; max-block-size: 420px; overflow: auto; }
    .type-group h3 { margin: 8px 0 4px; font-size: 14px; }
    .type-buttons { display: flex; gap: 8px; flex-wrap: wrap; }
    .photos { display: flex; gap: 10px; flex-wrap: wrap; }
    .photo { position: relative; }
    .photo img { inline-size: 100px; block-size: 100px; object-fit: cover; border-radius: 10px; }
    .nav { display: flex; justify-content: space-between; margin-top: 8px; }
    .field-error { color: var(--mat-sys-error, #ba1a1a); }
    .success { text-align: center; display: flex; flex-direction: column; gap: 10px;
               align-items: center; padding: 32px; }
    .success mat-icon { font-size: 56px; inline-size: 56px; block-size: 56px; color: #14805a; }
  `,
  template: `
    <div class="page">
      @if (successReference()) {
        <div class="cc-card success">
          <mat-icon>check_circle</mat-icon>
          <h1>{{ 'wizard.success.title' | t }}</h1>
          <strong id="success-reference">{{ 'wizard.success.reference' | t: successReference() }}</strong>
          <p style="max-inline-size:560px">{{ 'wizard.success.body' | t }}</p>
          <a mat-stroked-button [routerLink]="['/requests', successReference()]">
            {{ 'wizard.success.view' | t }}
          </a>
        </div>
      } @else {
      <h1>{{ 'wizard.title' | t }}</h1>
      <mat-stepper #stepper linear class="cc-card" [orientation]="'horizontal'">

        <!-- Étape 1 : position -->
        <mat-step [completed]="positionValid()">
          <ng-template matStepLabel>{{ 'wizard.step.position' | t }}</ng-template>
          <div class="step">
            <p>{{ 'wizard.position.intro' | t }}</p>
            <div class="row">
              <mat-form-field appearance="outline" style="flex:1" subscriptSizing="dynamic">
                <mat-label>{{ 'wizard.position.search' | t }}</mat-label>
                <input matInput [value]="addressSearch()"
                       (input)="addressSearch.set($any($event.target).value)" id="address-search"
                       (keydown.enter)="$event.preventDefault(); geocode()" />
              </mat-form-field>
              <button mat-stroked-button type="button" (click)="geocode()">
                <mat-icon>search</mat-icon> {{ 'common.search' | t }}
              </button>
              <button mat-stroked-button type="button" id="locate-button" (click)="locate()">
                <mat-icon>my_location</mat-icon> {{ 'wizard.position.locate' | t }}
              </button>
            </div>
            <div class="geo-results">
              @for (result of geoResults(); track result.label) {
                <button mat-button type="button" (click)="pickGeo(result)">
                  <mat-icon>location_on</mat-icon> {{ result.label }}
                </button>
              }
              @if (geoEmpty()) {
                <span class="cc-muted">{{ 'wizard.position.geocoder.none' | t }}</span>
              }
              @if (config.get()?.geocoderDemo && geoResults().length > 0) {
                <span class="cc-muted" style="font-size:12px">
                  Résultats de démonstration — نتائج تجريبية — demo results</span>
              }
            </div>
            <cc-map #pickerMap height="360px" [picker]="true" (picked)="onPicked($event)" />
            <mat-expansion-panel>
              <mat-expansion-panel-header>{{ 'wizard.position.manual' | t }}</mat-expansion-panel-header>
              <div class="row">
                <mat-form-field appearance="outline" subscriptSizing="dynamic">
                  <mat-label>{{ 'wizard.position.lat' | t }}</mat-label>
                  <input matInput type="number" [value]="latInput() ?? ''"
                         (input)="latInput.set(toNumber($any($event.target).value))" step="0.00001" />
                </mat-form-field>
                <mat-form-field appearance="outline" subscriptSizing="dynamic">
                  <mat-label>{{ 'wizard.position.lon' | t }}</mat-label>
                  <input matInput type="number" [value]="lonInput() ?? ''"
                         (input)="lonInput.set(toNumber($any($event.target).value))" step="0.00001" />
                </mat-form-field>
                <button mat-stroked-button type="button" (click)="applyManual()">OK</button>
              </div>
            </mat-expansion-panel>
            <mat-form-field appearance="outline" subscriptSizing="dynamic">
              <mat-label>{{ 'wizard.position.address' | t }}</mat-label>
              <input matInput [formField]="wizard.addressDetails" id="address-details" />
            </mat-form-field>
            @if (positionError()) {
              <div class="cc-banner" role="alert">{{ positionError()! | t }}</div>
            }
            <div class="nav">
              <span></span>
              <button mat-flat-button id="wizard-next-1" (click)="nextFromPosition()">
                {{ 'wizard.next' | t }}
              </button>
            </div>
          </div>
        </mat-step>

        <!-- Étape 2 : type -->
        <mat-step [completed]="!!selectedType()">
          <ng-template matStepLabel>{{ 'wizard.step.type' | t }}</ng-template>
          <div class="step">
            <p>{{ 'wizard.type.intro' | t }}</p>
            <mat-form-field appearance="outline" subscriptSizing="dynamic">
              <mat-label>{{ 'wizard.type.search' | t }}</mat-label>
              <input matInput [value]="typeSearch()"
                     (input)="typeSearch.set($any($event.target).value)" id="type-search" />
              <mat-icon matSuffix>search</mat-icon>
            </mat-form-field>
            <div class="types">
              @for (group of filteredGroups(); track group.id) {
                <div class="type-group">
                  <h3>{{ i18n.label(group.labels) }}</h3>
                  <div class="type-buttons">
                    @for (type of group.types; track type.id) {
                      <button type="button" [attr.data-type-code]="type.code"
                              mat-stroked-button
                              [attr.aria-pressed]="selectedType()?.id === type.id"
                              [style.background]="selectedType()?.id === type.id ? 'var(--cc-primary)' : ''"
                              [style.color]="selectedType()?.id === type.id ? 'white' : ''"
                              (click)="selectType(type)">
                        {{ i18n.label(type.labels) }}
                      </button>
                    }
                  </div>
                </div>
              }
            </div>
            @if (selectedType() && i18n.label(selectedType()!.help)) {
              <div class="cc-banner cc-banner--info">{{ i18n.label(selectedType()!.help) }}</div>
            }
            @if (typeError()) {
              <div class="cc-banner" role="alert">{{ 'wizard.type.required' | t }}</div>
            }
            <div class="nav">
              <button mat-button matStepperPrevious>{{ 'wizard.back' | t }}</button>
              <button mat-flat-button id="wizard-next-2" (click)="nextFromType()">
                {{ 'wizard.next' | t }}
              </button>
            </div>
          </div>
        </mat-step>

        <!-- Étape 3 : détails -->
        <mat-step [completed]="detailsValid()">
          <ng-template matStepLabel>{{ 'wizard.step.details' | t }}</ng-template>
          <div class="step">
            <p>{{ 'wizard.details.intro' | t }}</p>
            @if (selectedType()?.standardDescription) {
              <mat-form-field appearance="outline">
                <mat-label>{{ 'wizard.details.description' | t: descriptionMax }}</mat-label>
                <textarea matInput [formField]="wizard.description" rows="4"
                          id="description-field"></textarea>
                <mat-hint align="end">{{ wizardModel().description.length }}/{{ descriptionMax }}</mat-hint>
                <mat-error>{{ 'wizard.details.description.required' | t: descriptionMax }}</mat-error>
              </mat-form-field>
            }
            @for (field of selectedType()?.fields ?? []; track field.code) {
              @if (field.kind === 'SELECT') {
                <mat-form-field appearance="outline" style="max-inline-size:420px">
                  <mat-label>{{ i18n.label(field.labels) }}</mat-label>
                  <mat-select [value]="fieldValue(field.code)"
                              (valueChange)="setField(field.code, $event)"
                              [required]="field.required"
                              [attr.data-field-code]="field.code">
                    @for (option of field.options; track option.code) {
                      <mat-option [value]="option.code">{{ i18n.label(option.labels) }}</mat-option>
                    }
                  </mat-select>
                  @if (missingFields().has(field.code)) {
                    <mat-hint class="field-error">{{ 'wizard.field.required' | t }}</mat-hint>
                  }
                </mat-form-field>
              } @else {
                <mat-form-field appearance="outline" style="max-inline-size:420px">
                  <mat-label>{{ i18n.label(field.labels) }}</mat-label>
                  <input matInput [value]="fieldValue(field.code)"
                         (input)="setField(field.code, $any($event.target).value)"
                         [attr.maxlength]="field.maxLen" [required]="field.required"
                         [attr.data-field-code]="field.code" />
                  @if (missingFields().has(field.code)) {
                    <mat-hint class="field-error">{{ 'wizard.field.required' | t }}</mat-hint>
                  }
                </mat-form-field>
              }
            }
            <h3>{{ 'wizard.details.photos' | t: maxPhotos }}</h3>
            <p class="cc-muted">{{ 'wizard.details.photos.hint' | t: maxPhotoMb }}</p>
            <input type="file" #fileInput hidden multiple accept="image/jpeg,image/png"
                   (change)="onFiles($event)" />
            <div class="row">
              <button mat-stroked-button type="button" (click)="fileInput.click()"
                      [disabled]="photos().length >= maxPhotos">
                <mat-icon>add_photo_alternate</mat-icon>
                {{ photos().length }}/{{ maxPhotos }}
              </button>
            </div>
            <div class="photos">
              @for (photo of photos(); track photo.previewUrl) {
                <div class="photo">
                  <img [src]="photo.previewUrl" [alt]="photo.file.name" />
                  <button mat-icon-button type="button" (click)="removePhoto(photo)"
                          [attr.aria-label]="'common.delete' | t">
                    <mat-icon>delete</mat-icon>
                  </button>
                </div>
              }
            </div>
            <div class="nav">
              <button mat-button matStepperPrevious>{{ 'wizard.back' | t }}</button>
              <button mat-flat-button id="wizard-next-3" (click)="nextFromDetails()">
                {{ 'wizard.next' | t }}
              </button>
            </div>
          </div>
        </mat-step>

        <!-- Étape 4 : contact + récapitulatif -->
        <mat-step>
          <ng-template matStepLabel>{{ 'wizard.step.contact' | t }}</ng-template>
          <div class="step">
            <p>{{ 'wizard.contact.intro' | t }}</p>
            <mat-form-field appearance="outline" style="max-inline-size:420px">
              <mat-label>{{ 'wizard.contact.email' | t }}</mat-label>
              <input matInput type="email" [formField]="wizard.email" id="contact-email" />
              <mat-error>{{ 'wizard.contact.email.invalid' | t }}</mat-error>
            </mat-form-field>
            <mat-form-field appearance="outline" style="max-inline-size:420px">
              <mat-label>{{ 'wizard.contact.phone' | t }}</mat-label>
              <input matInput [formField]="wizard.phone" id="contact-phone" dir="ltr" />
              <mat-hint>+216 …</mat-hint>
              @if (phoneServerError()) {
                <mat-hint class="field-error">{{ 'wizard.contact.phone.invalid' | t }}</mat-hint>
              }
            </mat-form-field>
            <mat-checkbox [formField]="wizard.consent" id="consent-checkbox">
              {{ 'wizard.consent' | t }}
            </mat-checkbox>
            <h3>{{ 'wizard.summary.title' | t }}</h3>
            <div class="cc-card" style="background:var(--cc-bg)">
              <div>{{ 'detail.category' | t }} :
                {{ selectedType() ? i18n.label(selectedType()!.labels) : '' }}</div>
              <div>{{ 'detail.address' | t }} :
                {{ address() || '' }}
                <span class="cc-bidi">{{ lat()?.toFixed(5) }}, {{ lon()?.toFixed(5) }}</span></div>
              @if (selectedType()?.standardDescription) {
                <div>{{ 'detail.description' | t }} : {{ wizardModel().description }}</div>
              }
              <div>{{ 'wizard.details.photos' | t: maxPhotos }} : {{ photos().length }}</div>
              <button mat-button type="button" (click)="stepper.selectedIndex = 0">
                {{ 'wizard.summary.edit' | t }}
              </button>
            </div>
            <div class="nav">
              <button mat-button matStepperPrevious>{{ 'wizard.back' | t }}</button>
              <button mat-flat-button id="wizard-submit" [disabled]="submitting()"
                      (click)="submit()">
                {{ 'wizard.submit' | t }}
              </button>
            </div>
          </div>
        </mat-step>
      </mat-stepper>
      }
    </div>
  `,
})
export class ReportForm {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  readonly i18n = inject(I18nService);
  readonly config = inject(ConfigService);

  private readonly stepperRef = viewChild.required(MatStepper);
  private readonly pickerMap = viewChild.required<CcMap>('pickerMap');

  // État de l'assistant (clé d'idempotence stable pour toute la session du formulaire)
  private readonly idempotencyKey = crypto.randomUUID();
  readonly lon = signal<number | null>(null);
  readonly lat = signal<number | null>(null);
  readonly address = signal<string | null>(null);
  readonly selectedType = signal<ServiceTypeDto | null>(null);
  readonly photos = signal<PendingPhoto[]>([]);
  readonly successReference = signal<string | null>(null);
  readonly submitting = signal(false);
  readonly positionError = signal<string | null>(null);
  readonly typeError = signal(false);
  readonly geoResults = signal<GeocodeResult[]>([]);
  readonly geoEmpty = signal(false);
  private duplicatesConfirmed = false;

  readonly groups = signal<CatalogGroup[]>([]);
  readonly typeSearch = signal('');
  readonly addressSearch = signal('');
  readonly latInput = signal<number | null>(null);
  readonly lonInput = signal<number | null>(null);
  readonly phoneServerError = signal(false);

  /** Champs saisis validés : Signal Forms (la règle description dépend du type choisi). */
  readonly wizardModel = signal({
    addressDetails: '', description: '', email: '', phone: '', consent: false,
  });
  readonly wizard = form(this.wizardModel, (path) => {
    maxLength(path.addressDetails, 300);
    // Requise uniquement quand le type retient la description standard (règle réactive)
    validate(path.description, ({ value }) =>
      this.selectedType()?.standardDescription && !value().trim() ? requiredError() : null);
    required(path.email);
    emailValidator(path.email);
  });

  /** Valeurs des champs conditionnels du type (dynamiques, hors schéma statique). */
  readonly fieldValues = signal<Record<string, string>>({});
  readonly missingFields = signal<ReadonlySet<string>>(new Set());

  readonly filteredGroups = computed(() => {
    const query = this.normalizedQuery();
    if (!query) return this.groups();
    return this.groups()
      .map((group) => ({
        ...group,
        types: group.types.filter((type) =>
          [type.labels.fr, type.labels.ar, type.labels.en, this.i18n.label(group.labels)]
            .some((label) => this.normalize(label).includes(query))),
      }))
      .filter((group) => group.types.length > 0);
  });

  readonly positionValid = computed(() => this.lon() !== null && this.lat() !== null);
  // Avec Signal Forms, la validité EST un signal : plus besoin du pont statusChanges.
  readonly detailsValid = computed(() =>
    this.wizard.description().valid() && this.missingRequiredFields().length === 0);

  private readonly missingRequiredFields = computed(() => {
    const values = this.fieldValues();
    return (this.selectedType()?.fields ?? [])
      .filter((field) => field.required && !(values[field.code] ?? '').trim())
      .map((field) => field.code);
  });

  get descriptionMax(): number { return this.config.get()?.limits.descriptionMax ?? 300; }
  get maxPhotos(): number { return this.config.get()?.limits.maxPhotos ?? 3; }
  get maxPhotoMb(): number {
    return Math.round((this.config.get()?.limits.maxPhotoBytes ?? 10485760) / 1048576);
  }

  constructor() {
    this.api.catalog().subscribe((groups) => this.groups.set(groups));
  }

  toNumber(raw: string): number | null {
    const value = Number(raw);
    return raw.trim() === '' || Number.isNaN(value) ? null : value;
  }

  fieldValue(code: string): string {
    return this.fieldValues()[code] ?? '';
  }

  setField(code: string, value: string): void {
    this.fieldValues.update((values) => ({ ...values, [code]: value }));
    this.missingFields.update((missing) => {
      if (!missing.has(code)) return missing;
      const next = new Set(missing); next.delete(code); return next;
    });
  }

  private normalize(value: string): string {
    return value.normalize('NFKD').replace(/\p{M}+/gu, '').toLowerCase();
  }

  private normalizedQuery(): string {
    return this.normalize(this.typeSearch());
  }

  // ===== Étape 1 =====

  async geocode(): Promise<void> {
    this.geoEmpty.set(false);
    const query = this.addressSearch().trim();
    if (!query) return;
    const results = await firstValueFrom(this.api.geocode(query, this.i18n.locale()));
    this.geoResults.set(results);
    this.geoEmpty.set(results.length === 0);
  }

  pickGeo(result: GeocodeResult): void {
    this.address.set(result.label);
    this.addressSearch.set(result.label);
    this.geoResults.set([]);
    this.setPosition(result.lon, result.lat, true);
  }

  /** Géolocalisation demandée uniquement au clic de l'utilisateur. */
  locate(): void {
    if (!navigator.geolocation) {
      this.snackBar.open(this.i18n.t('wizard.position.locate.denied'), undefined, { duration: 5000 });
      return;
    }
    navigator.geolocation.getCurrentPosition(
      (pos) => this.setPosition(pos.coords.longitude, pos.coords.latitude, true),
      () => this.snackBar.open(this.i18n.t('wizard.position.locate.denied'), undefined,
        { duration: 5000 }),
    );
  }

  onPicked(point: { lon: number; lat: number }): void {
    this.setPosition(point.lon, point.lat, false);
  }

  applyManual(): void {
    const lat = this.latInput();
    const lon = this.lonInput();
    if (lat !== null && lon !== null) {
      void this.setPosition(lon, lat, true);
    }
  }

  private async setPosition(lon: number, lat: number, fly: boolean): Promise<void> {
    this.lon.set(lon);
    this.lat.set(lat);
    this.latInput.set(Number(lat.toFixed(6)));
    this.lonInput.set(Number(lon.toFixed(6)));
    this.pickerMap().setPickedPoint(lon, lat);
    if (fly) this.pickerMap().flyTo(lon, lat);
    this.positionError.set(null);
    // Retour immédiat si hors périmètre (l'autorité reste le serveur au dépôt)
    const check = await firstValueFrom(this.api.boundaryCheck(lon, lat));
    if (!check.inside) {
      this.positionError.set('wizard.position.outside');
    }
    // Géocodage inverse best-effort : jamais d'adresse inventée si introuvable
    if (!this.address()) {
      const reverse = await firstValueFrom(this.api.reverseGeocode(lon, lat, this.i18n.locale()));
      if (reverse?.label) {
        this.address.set(reverse.label);
        this.addressSearch.set(reverse.label);
      }
    }
  }

  nextFromPosition(): void {
    if (!this.positionValid()) {
      this.positionError.set('wizard.position.required');
      return;
    }
    if (this.positionError() === 'wizard.position.outside') {
      return;
    }
    this.stepperRef().next();
  }

  // ===== Étape 2 =====

  selectType(type: ServiceTypeDto): void {
    this.selectedType.set(type);
    this.typeError.set(false);
    this.fieldValues.set({});
    this.missingFields.set(new Set());
  }

  nextFromType(): void {
    if (!this.selectedType()) {
      this.typeError.set(true);
      return;
    }
    this.stepperRef().next();
  }

  // ===== Étape 3 =====

  onFiles(event: Event): void {
    const input = event.target as HTMLInputElement;
    const maxBytes = this.config.get()?.limits.maxPhotoBytes ?? 10485760;
    for (const file of Array.from(input.files ?? [])) {
      if (this.photos().length >= this.maxPhotos) {
        this.snackBar.open(this.i18n.t('wizard.details.photos.tooMany'), undefined, { duration: 4000 });
        break;
      }
      if (!['image/jpeg', 'image/png'].includes(file.type) || file.size > maxBytes) {
        this.snackBar.open(this.i18n.t('wizard.details.photos.invalid'), undefined, { duration: 5000 });
        continue;
      }
      this.photos.update((photos) => [...photos, { file, previewUrl: URL.createObjectURL(file) }]);
    }
    input.value = '';
  }

  removePhoto(photo: PendingPhoto): void {
    URL.revokeObjectURL(photo.previewUrl); // aperçus révoqués
    this.photos.update((photos) => photos.filter((p) => p !== photo));
  }

  nextFromDetails(): void {
    if (!this.wizard.description().valid()) {
      this.wizard.description().markAsTouched();
      return;
    }
    const missing = this.missingRequiredFields();
    if (missing.length > 0) {
      this.missingFields.set(new Set(missing));
      return;
    }
    this.stepperRef().next();
  }

  // ===== Soumission =====

  async submit(): Promise<void> {
    this.phoneServerError.set(false);
    if (this.wizard.email().invalid()) {
      markAllTouched(this.wizard);
      return;
    }
    if (!this.wizardModel().consent) {
      this.snackBar.open(this.i18n.t('wizard.consent.required'), undefined, { duration: 4000 });
      return;
    }
    // Prévention des doublons : proposer sans jamais bloquer un incident distinct
    if (!this.duplicatesConfirmed) {
      const candidates = await firstValueFrom(
        this.api.duplicates(this.selectedType()!.id, this.lon()!, this.lat()!));
      if (candidates.length > 0) {
        const proceed = await this.showDuplicates(candidates);
        if (!proceed) return;
        this.duplicatesConfirmed = true;
      }
    }
    await this.doSubmit();
  }

  private showDuplicates(candidates: DuplicateCandidate[]): Promise<boolean> {
    const ref = this.dialog.open(DuplicatesDialog, { data: candidates, width: '560px' });
    return firstValueFrom(ref.afterClosed()).then((result) => result === true);
  }

  private async doSubmit(): Promise<void> {
    this.submitting.set(true);
    const fieldValues: Record<string, string> = {};
    for (const [code, value] of Object.entries(this.fieldValues())) {
      if (value) fieldValues[code] = value;
    }
    const contact = this.wizardModel();
    try {
      const created = await businessSpan('report.submit', () => firstValueFrom(
        this.api.createReport({
          serviceTypeId: this.selectedType()!.id,
          longitude: this.lon()!,
          latitude: this.lat()!,
          address: this.address(),
          addressDetails: contact.addressDetails || null,
          description: this.selectedType()!.standardDescription ? contact.description : null,
          fieldValues,
          email: contact.email,
          phone: contact.phone || null,
          consent: true,
          idempotencyKey: this.idempotencyKey,
        }, this.photos().map((p) => p.file)),
      ));
      this.photos().forEach((p) => URL.revokeObjectURL(p.previewUrl));
      this.successReference.set(created.reference);
    } catch (error: unknown) {
      this.applyServerErrors(error);
    } finally {
      this.submitting.set(false);
    }
  }

  /** Convertit les Problem Details serveur en erreurs de champ / messages localisés. */
  private applyServerErrors(error: unknown): void {
    const problem = (error as { error?: ProblemDetails })?.error;
    const code = problem?.code ?? '';
    const fieldErrors = problem?.errors ?? {};
    if (fieldErrors['phone'] || code === 'phone.invalid') {
      this.phoneServerError.set(true);
      this.stepperRef().selectedIndex = 3;
    } else if (fieldErrors['position'] || code === 'position.outside') {
      this.positionError.set('wizard.position.outside');
      this.stepperRef().selectedIndex = 0;
    } else if (fieldErrors['description'] || code === 'description.invalid') {
      this.wizard.description().markAsTouched();
      this.stepperRef().selectedIndex = 2;
    } else if (code.startsWith('media.')) {
      this.snackBar.open(this.i18n.t('wizard.details.photos.invalid'), undefined, { duration: 6000 });
      this.stepperRef().selectedIndex = 2;
    } else {
      this.snackBar.open(this.i18n.t('wizard.error'), undefined, { duration: 6000 });
    }
  }
}
