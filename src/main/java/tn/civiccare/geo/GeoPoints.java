package tn.civiccare.geo;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

/** Fabrique JTS partagée. WGS84 (SRID 4326), ordre x=longitude, y=latitude. */
public final class GeoPoints {

    public static final GeometryFactory FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    private GeoPoints() {
    }

    public static Point of(double longitude, double latitude) {
        if (longitude < -180 || longitude > 180 || latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException("Coordonnées hors plage WGS84");
        }
        return FACTORY.createPoint(new Coordinate(longitude, latitude));
    }
}
