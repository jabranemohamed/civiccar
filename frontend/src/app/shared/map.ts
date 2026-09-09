import {
  AfterViewInit, Component, ElementRef, OnDestroy, effect, inject, input, output, viewChild,
} from '@angular/core';
import { Map as MapLibreMap, Marker, NavigationControl, setWorkerUrl } from 'maplibre-gl';
import type { Point as GeoJsonPoint } from 'geojson';
import { ConfigService } from '../core/config.service';
import { MapPoint } from '../core/api.types';

// Worker MapLibre servi en asset statique : l'URL par défaut (relative au module bundlé)
// ne se résout ni sous Vite dev ni après build esbuild. Copié via angular.json (assets)
// avec maplibre-gl-shared.mjs, importé relativement par le worker.
setWorkerUrl('/maplibre/maplibre-gl-worker.mjs');

const STATUS_COLORS: Record<string, string> = {
  OPEN: '#1d5fae',
  IN_PROGRESS: '#b8860b',
  DONE_OR_ORDERED: '#14805a',
  OUT_OF_SCOPE: '#5d5378',
};

/**
 * Carte MapLibre encapsulée : instanciation/destruction propres, redimensionnement,
 * conteneur toujours LTR (la carte géographique n'est jamais inversée en RTL).
 * Le fournisseur de tuiles vient de la configuration serveur ; MapLibre n'est ni un
 * fournisseur de tuiles ni d'adresses.
 */
@Component({
  selector: 'cc-map',
  template: `<div #host class="cc-map-host" [style.height]="height()"
                  style="width:100%;border-radius:var(--cc-radius);overflow:hidden"></div>`,
})
export class CcMap implements AfterViewInit, OnDestroy {
  private readonly config = inject(ConfigService);
  private readonly host = viewChild.required<ElementRef<HTMLDivElement>>('host');

  readonly height = input('420px');
  readonly picker = input(false);
  readonly points = input<MapPoint[]>([]);
  readonly showBoundary = input(true);

  readonly mapError = output<void>();
  readonly picked = output<{ lon: number; lat: number }>();
  readonly moved = output<[number, number, number, number]>();
  readonly markerClick = output<string>();

  private map?: MapLibreMap;
  private pickMarker?: Marker;
  private ready = false;
  private pendingPoints: MapPoint[] | null = null;
  private resizeObserver?: ResizeObserver;

  constructor() {
    effect(() => {
      const pts = this.points();
      if (this.ready) {
        this.renderPoints(pts);
      } else {
        this.pendingPoints = pts;
      }
    });
  }

  ngAfterViewInit(): void {
    const cfg = this.config.get();
    const tileUrl = cfg?.map.tileUrl ?? 'https://tile.openstreetmap.org/{z}/{x}/{y}.png';
    try {
      this.map = new MapLibreMap({
        container: this.host().nativeElement,
        style: {
          version: 8,
          sources: {
            tiles: { type: 'raster', tiles: [tileUrl], tileSize: 256,
              attribution: cfg?.map.attribution ?? '© OpenStreetMap contributors (ODbL)' },
          },
          layers: [{ id: 'tiles', type: 'raster', source: 'tiles' }],
        },
        center: [cfg?.map.centerLon ?? 10.1815, cfg?.map.centerLat ?? 36.8065],
        zoom: cfg?.map.initialZoom ?? 12.5,
        attributionControl: { compact: false },
      });
    } catch {
      this.mapError.emit(); // WebGL indisponible : la liste reste utilisable
      return;
    }

    this.map.addControl(new NavigationControl(), 'top-right');
    this.map.on('error', () => this.mapError.emit());
    this.map.on('load', () => {
      this.ready = true;
      this.setupLayers();
      if (this.pendingPoints) {
        this.renderPoints(this.pendingPoints);
      }
      const boundary = this.config.get()?.map.boundaryGeoJson;
      if (this.showBoundary() && boundary) {
        this.map!.addSource('boundary', {
          type: 'geojson', data: { type: 'Feature', geometry: JSON.parse(boundary), properties: {} },
        });
        this.map!.addLayer({
          id: 'boundary-line', type: 'line', source: 'boundary',
          paint: { 'line-color': '#b45336', 'line-width': 1.5, 'line-dasharray': [3, 2] },
        });
      }
    });
    this.map.on('moveend', () => {
      const b = this.map!.getBounds();
      this.moved.emit([b.getWest(), b.getSouth(), b.getEast(), b.getNorth()]);
    });
    this.map.on('click', (e) => {
      if (this.picker()) {
        this.setPickedPoint(e.lngLat.lng, e.lngLat.lat);
        this.picked.emit({ lon: e.lngLat.lng, lat: e.lngLat.lat });
      }
    });
    // Redimensionnement (ouverture de panneau, bascule RTL) sans inverser le repère.
    // resize() est différé : un appel synchrone dans le callback modifie la mise en page
    // et déclenche « ResizeObserver loop completed with undelivered notifications ».
    this.resizeObserver = new ResizeObserver(() =>
      requestAnimationFrame(() => this.map?.resize()));
    this.resizeObserver.observe(this.host().nativeElement);
  }

  private setupLayers(): void {
    this.map!.addSource('reports', {
      type: 'geojson',
      data: { type: 'FeatureCollection', features: [] },
      cluster: true, clusterMaxZoom: 15, clusterRadius: 45,
    });
    this.map!.addLayer({
      id: 'clusters', type: 'circle', source: 'reports', filter: ['has', 'point_count'],
      paint: {
        'circle-color': '#155e75', 'circle-opacity': 0.85,
        'circle-radius': ['step', ['get', 'point_count'], 16, 10, 22, 50, 28],
      },
    });
    this.map!.addLayer({
      id: 'points', type: 'circle', source: 'reports', filter: ['!', ['has', 'point_count']],
      paint: {
        'circle-color': ['coalesce', ['get', 'color'], '#155e75'],
        'circle-radius': 9, 'circle-stroke-width': 2, 'circle-stroke-color': '#ffffff',
      },
    });
    this.map!.on('click', 'points', (e) => {
      const feature = e.features?.[0];
      if (feature) {
        this.markerClick.emit(String(feature.properties?.['id']));
      }
    });
    this.map!.on('click', 'clusters', async (e) => {
      const feature = e.features?.[0];
      if (!feature) return;
      const source = this.map!.getSource('reports') as maplibregl.GeoJSONSource;
      const zoom = await source.getClusterExpansionZoom(feature.properties!['cluster_id']);
      this.map!.easeTo({ center: (feature.geometry as GeoJsonPoint).coordinates as [number, number], zoom });
    });
    this.map!.on('mouseenter', 'points', () => { this.map!.getCanvas().style.cursor = 'pointer'; });
    this.map!.on('mouseleave', 'points', () => { this.map!.getCanvas().style.cursor = ''; });
    if (this.picker()) {
      this.map!.getCanvas().style.cursor = 'crosshair';
    }
  }

  private renderPoints(points: MapPoint[]): void {
    const source = this.map?.getSource('reports') as maplibregl.GeoJSONSource | undefined;
    source?.setData({
      type: 'FeatureCollection',
      features: points.map((p) => ({
        type: 'Feature',
        geometry: { type: 'Point', coordinates: [p.lon, p.lat] },
        properties: { id: p.id, color: STATUS_COLORS[p.status] ?? '#155e75' },
      })),
    });
  }

  setPickedPoint(lon: number, lat: number): void {
    if (!this.map) return;
    if (!this.pickMarker) {
      this.pickMarker = new Marker({ color: '#b45336', draggable: true })
        .setLngLat([lon, lat]).addTo(this.map);
      this.pickMarker.on('dragend', () => {
        const pos = this.pickMarker!.getLngLat();
        this.picked.emit({ lon: pos.lng, lat: pos.lat });
      });
    } else {
      this.pickMarker.setLngLat([lon, lat]);
    }
  }

  flyTo(lon: number, lat: number, zoom = 16): void {
    this.map?.flyTo({ center: [lon, lat], zoom });
  }

  ngOnDestroy(): void {
    this.resizeObserver?.disconnect();
    this.pickMarker?.remove();
    this.map?.remove(); // libère listeners, workers et WebGL au changement de route
  }
}

// Types maplibre non exportés par défaut
declare namespace maplibregl {
  type GeoJSONSource = import('maplibre-gl').GeoJSONSource;
}
