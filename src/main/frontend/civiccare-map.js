// Composant cartographique CivicCare : encapsulation MapLibre GL JS.
// Le moteur MapLibre n'est pas un fournisseur de tuiles : l'URL des tuiles et
// l'attribution sont configurées côté serveur et passées en attributs.
// La carte reste en direction LTR même en interface RTL (carte géographique non inversée).
import { Map as MapLibreMap, NavigationControl, Marker, setWorkerUrl } from 'maplibre-gl';
import maplibreWorkerUrl from 'maplibre-gl/dist/maplibre-gl-worker.mjs?worker&url';
import 'maplibre-gl/dist/maplibre-gl.css';

// Après bundling (Vite), MapLibre ne retrouve plus son worker relativement à son module :
// l'URL du worker est fournie explicitement (fonctionne en dev-bundle et en production).
setWorkerUrl(maplibreWorkerUrl);

const STATUS_COLORS = {
  OPEN: '#155e75',
  IN_PROGRESS: '#b8860b',
  DONE_OR_ORDERED: '#1b7a4b',
  OUT_OF_SCOPE: '#6b6257',
};

class CivicCareMap extends HTMLElement {
  constructor() {
    super();
    this._markers = [];
    this._picker = false;
    this._pickMarker = null;
    this._ready = false;
    this._pendingOps = [];
  }

  connectedCallback() {
    if (this._map) return;
    this.style.display = 'block';
    this.style.direction = 'ltr';
    const tileUrl = this.getAttribute('tile-url');
    const attribution = this.getAttribute('attribution') || '';
    const lon = parseFloat(this.getAttribute('center-lon') || '10.1815');
    const lat = parseFloat(this.getAttribute('center-lat') || '36.8065');
    const zoom = parseFloat(this.getAttribute('zoom') || '12.5');

    try {
      this._map = new MapLibreMap({
        container: this,
        style: {
          version: 8,
          sources: {
            tiles: {
              type: 'raster',
              tiles: [tileUrl],
              tileSize: 256,
              attribution,
            },
          },
          layers: [{ id: 'tiles', type: 'raster', source: 'tiles' }],
        },
        center: [lon, lat],
        zoom,
        attributionControl: { compact: false },
      });
    } catch (err) {
      // WebGL indisponible : la liste reste utilisable côté serveur.
      this.dispatchEvent(new CustomEvent('map-error', { detail: { message: String(err) } }));
      return;
    }

    this._map.addControl(new NavigationControl(), 'top-right');

    this._map.on('error', (e) => {
      if (!this._errorSent) {
        this._errorSent = true;
        this.dispatchEvent(new CustomEvent('map-error', { detail: { message: String(e?.error || 'load') } }));
      }
    });

    this._map.on('load', () => {
      this._ready = true;
      this._setupClusters();
      this._pendingOps.forEach((op) => op());
      this._pendingOps = [];
      this.dispatchEvent(new CustomEvent('map-ready'));
    });

    this._map.on('moveend', () => {
      const b = this._map.getBounds();
      this.dispatchEvent(new CustomEvent('map-moveend', {
        detail: { west: b.getWest(), south: b.getSouth(), east: b.getEast(), north: b.getNorth() },
      }));
    });

    this._map.on('click', (e) => {
      if (this._picker) {
        this.setPickedPoint(e.lngLat.lng, e.lngLat.lat);
        this.dispatchEvent(new CustomEvent('map-pick', {
          detail: { lon: e.lngLat.lng, lat: e.lngLat.lat },
        }));
      }
    });
  }

  _whenReady(op) {
    if (this._ready) op();
    else this._pendingOps.push(op);
  }

  _setupClusters() {
    this._map.addSource('reports', {
      type: 'geojson',
      data: { type: 'FeatureCollection', features: [] },
      cluster: true,
      clusterMaxZoom: 15,
      clusterRadius: 45,
    });
    this._map.addLayer({
      id: 'clusters',
      type: 'circle',
      source: 'reports',
      filter: ['has', 'point_count'],
      paint: {
        'circle-color': '#155e75',
        'circle-opacity': 0.85,
        'circle-radius': ['step', ['get', 'point_count'], 16, 10, 22, 50, 28],
      },
    });
    // Pas de couche symbole (texte) : le style raster minimal n'embarque pas de glyphes
    // de police ; le nombre de signalements d'un regroupement est encodé par la taille
    // du cercle et détaillé par zoom au clic.
    this._map.addLayer({
      id: 'points',
      type: 'circle',
      source: 'reports',
      filter: ['!', ['has', 'point_count']],
      paint: {
        'circle-color': ['coalesce', ['get', 'color'], '#155e75'],
        'circle-radius': 9,
        'circle-stroke-width': 2,
        'circle-stroke-color': '#ffffff',
      },
    });
    this._map.on('click', 'points', (e) => {
      const f = e.features && e.features[0];
      if (f) {
        this.dispatchEvent(new CustomEvent('marker-click', { detail: { id: f.properties.id } }));
      }
    });
    this._map.on('click', 'clusters', async (e) => {
      const f = e.features && e.features[0];
      if (!f) return;
      const zoom = await this._map.getSource('reports').getClusterExpansionZoom(f.properties.cluster_id);
      this._map.easeTo({ center: f.geometry.coordinates, zoom });
    });
    this._map.on('mouseenter', 'points', () => { this._map.getCanvas().style.cursor = 'pointer'; });
    this._map.on('mouseleave', 'points', () => { this._map.getCanvas().style.cursor = ''; });
  }

  /** markers: [{id, lon, lat, status, label}] — l'infobulle affiche icône + statut textuel. */
  setMarkers(markersJson) {
    const markers = typeof markersJson === 'string' ? JSON.parse(markersJson) : markersJson;
    this._whenReady(() => {
      const features = markers.map((m) => ({
        type: 'Feature',
        geometry: { type: 'Point', coordinates: [m.lon, m.lat] },
        properties: { id: m.id, color: STATUS_COLORS[m.status] || '#155e75', label: m.label || '' },
      }));
      this._map.getSource('reports').setData({ type: 'FeatureCollection', features });
    });
  }

  showBoundary(geojsonText) {
    this._whenReady(() => {
      const geom = JSON.parse(geojsonText);
      if (this._map.getSource('boundary')) return;
      this._map.addSource('boundary', { type: 'geojson', data: { type: 'Feature', geometry: geom } });
      this._map.addLayer({
        id: 'boundary-line',
        type: 'line',
        source: 'boundary',
        paint: { 'line-color': '#b45336', 'line-width': 1.5, 'line-dasharray': [3, 2] },
      });
    });
  }

  setPicker(enabled) {
    this._picker = !!enabled;
    if (this._map) this._map.getCanvas().style.cursor = this._picker ? 'crosshair' : '';
  }

  setPickedPoint(lon, lat) {
    this._whenReady(() => {
      if (!this._pickMarker) {
        this._pickMarker = new Marker({ color: '#b45336', draggable: true })
          .setLngLat([lon, lat]).addTo(this._map);
        this._pickMarker.on('dragend', () => {
          const p = this._pickMarker.getLngLat();
          this.dispatchEvent(new CustomEvent('map-pick', { detail: { lon: p.lng, lat: p.lat } }));
        });
      } else {
        this._pickMarker.setLngLat([lon, lat]);
      }
    });
  }

  flyTo(lon, lat, zoom) {
    this._whenReady(() => this._map.flyTo({ center: [lon, lat], zoom: zoom || 16 }));
  }

  highlight(id) {
    this._whenReady(() => {
      this._map.setPaintProperty('points', 'circle-stroke-color',
        ['case', ['==', ['get', 'id'], id], '#b45336', '#ffffff']);
      this._map.setPaintProperty('points', 'circle-stroke-width',
        ['case', ['==', ['get', 'id'], id], 4, 2]);
    });
  }

  resizeMap() {
    if (this._map) this._map.resize();
  }
}

customElements.define('civiccare-map', CivicCareMap);
