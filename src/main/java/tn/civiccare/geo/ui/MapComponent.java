package tn.civiccare.geo.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentEvent;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.DomEvent;
import com.vaadin.flow.component.EventData;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.dependency.NpmPackage;
import com.vaadin.flow.component.HasSize;
import com.vaadin.flow.shared.Registration;
import tn.civiccare.shared.AppProperties;

import java.util.List;
import java.util.UUID;

/**
 * Carte MapLibre encapsulée dans un composant Vaadin ; échanges JSON typés.
 * Fournisseur de tuiles et attribution configurés par variables d'environnement.
 */
@Tag("civiccare-map")
@NpmPackage(value = "maplibre-gl", version = "6.7.0")
@JsModule("./civiccare-map.js")
public class MapComponent extends Component implements HasSize {

    public record Marker(UUID id, double longitude, double latitude, String status, String label) {
    }

    public MapComponent(AppProperties props) {
        getElement().setAttribute("tile-url", props.map().tileUrl());
        getElement().setAttribute("attribution", props.map().tileAttribution());
        getElement().setAttribute("center-lon", String.valueOf(props.map().centerLon()));
        getElement().setAttribute("center-lat", String.valueOf(props.map().centerLat()));
        getElement().setAttribute("zoom", String.valueOf(props.map().initialZoom()));
        getElement().getClassList().add("map-container");
    }

    public void setMarkers(List<Marker> markers) {
        // JSON construit explicitement : contenu contrôlé (UUID, nombres, enum, référence).
        StringBuilder json = new StringBuilder("[");
        boolean first = true;
        for (Marker marker : markers) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append("{\"id\":\"").append(marker.id()).append("\",\"lon\":")
                    .append(marker.longitude()).append(",\"lat\":").append(marker.latitude())
                    .append(",\"status\":\"").append(marker.status())
                    .append("\",\"label\":\"").append(escape(marker.label())).append("\"}");
        }
        json.append(']');
        getElement().callJsFunction("setMarkers", json.toString());
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public void showBoundary(String geoJson) {
        if (geoJson != null) {
            getElement().callJsFunction("showBoundary", geoJson);
        }
    }

    public void setPickerEnabled(boolean enabled) {
        getElement().callJsFunction("setPicker", enabled);
    }

    public void setPickedPoint(double longitude, double latitude) {
        getElement().callJsFunction("setPickedPoint", longitude, latitude);
    }

    public void flyTo(double longitude, double latitude, double zoom) {
        getElement().callJsFunction("flyTo", longitude, latitude, zoom);
    }

    public void highlight(UUID id) {
        getElement().callJsFunction("highlight", id == null ? "" : id.toString());
    }

    // ===== Événements =====

    @DomEvent("marker-click")
    public static class MarkerClickEvent extends ComponentEvent<MapComponent> {
        private final String markerId;

        public MarkerClickEvent(MapComponent source, boolean fromClient,
                                @EventData("event.detail.id") String markerId) {
            super(source, fromClient);
            this.markerId = markerId;
        }

        public UUID getMarkerId() {
            return UUID.fromString(markerId);
        }
    }

    @DomEvent("map-pick")
    public static class MapPickEvent extends ComponentEvent<MapComponent> {
        private final double lon;
        private final double lat;

        public MapPickEvent(MapComponent source, boolean fromClient,
                            @EventData("event.detail.lon") double lon,
                            @EventData("event.detail.lat") double lat) {
            super(source, fromClient);
            this.lon = lon;
            this.lat = lat;
        }

        public double getLon() {
            return lon;
        }

        public double getLat() {
            return lat;
        }
    }

    @DomEvent("map-moveend")
    public static class MapMoveEvent extends ComponentEvent<MapComponent> {
        private final double west;
        private final double south;
        private final double east;
        private final double north;

        public MapMoveEvent(MapComponent source, boolean fromClient,
                            @EventData("event.detail.west") double west,
                            @EventData("event.detail.south") double south,
                            @EventData("event.detail.east") double east,
                            @EventData("event.detail.north") double north) {
            super(source, fromClient);
            this.west = west;
            this.south = south;
            this.east = east;
            this.north = north;
        }

        public double getWest() {
            return west;
        }

        public double getSouth() {
            return south;
        }

        public double getEast() {
            return east;
        }

        public double getNorth() {
            return north;
        }
    }

    @DomEvent("map-error")
    public static class MapErrorEvent extends ComponentEvent<MapComponent> {
        public MapErrorEvent(MapComponent source, boolean fromClient) {
            super(source, fromClient);
        }
    }

    public Registration addMarkerClickListener(ComponentEventListener<MarkerClickEvent> listener) {
        return addListener(MarkerClickEvent.class, listener);
    }

    public Registration addPickListener(ComponentEventListener<MapPickEvent> listener) {
        return addListener(MapPickEvent.class, listener);
    }

    public Registration addMoveListener(ComponentEventListener<MapMoveEvent> listener) {
        return addListener(MapMoveEvent.class, listener);
    }

    public Registration addErrorListener(ComponentEventListener<MapErrorEvent> listener) {
        return addListener(MapErrorEvent.class, listener);
    }
}
