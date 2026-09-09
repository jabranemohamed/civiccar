package tn.civiccare.media;

import tn.civiccare.observability.Telemetry.ValidationException;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * Traitement des images côté serveur :
 * - vérification de la signature réelle (JPEG/PNG uniquement ; WebP refusé car le décodage
 *   n'est pas pris en charge par ImageIO standard — limite documentée) ;
 * - contrôle des dimensions AVANT décodage complet (budget mémoire) ;
 * - normalisation de l'orientation EXIF puis réencodage JPEG : toutes les métadonnées
 *   (EXIF, GPS) sont supprimées par le réencodage ;
 * - production d'une miniature. SVG et contenus actifs rejetés par la liste de signatures.
 */
public final class ImageProcessor {

    public static final int MAX_DIMENSION_INPUT = 12000;
    public static final long MAX_PIXELS = 40_000_000L;
    public static final int MAX_OUTPUT_DIMENSION = 2000;
    public static final int THUMB_DIMENSION = 400;

    private ImageProcessor() {
    }

    public record Processed(byte[] full, byte[] thumbnail, int width, int height) {
    }

    public static Processed process(byte[] input) {
        String format = detectFormat(input);
        try {
            checkDimensions(input);
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(input));
            if (image == null) {
                throw new ValidationException("media.unreadable");
            }
            if ("jpeg".equals(format)) {
                image = applyExifOrientation(image, readExifOrientation(input));
            }
            BufferedImage full = scaleDown(image, MAX_OUTPUT_DIMENSION);
            BufferedImage thumb = scaleDown(image, THUMB_DIMENSION);
            return new Processed(toJpeg(full), toJpeg(thumb), full.getWidth(), full.getHeight());
        } catch (IOException e) {
            throw new ValidationException("media.unreadable");
        }
    }

    /** Signature réelle du fichier, indépendante de l'extension et du type déclaré. */
    static String detectFormat(byte[] input) {
        if (input.length > 3 && (input[0] & 0xFF) == 0xFF && (input[1] & 0xFF) == 0xD8
                && (input[2] & 0xFF) == 0xFF) {
            return "jpeg";
        }
        if (input.length > 8 && (input[0] & 0xFF) == 0x89 && input[1] == 'P' && input[2] == 'N'
                && input[3] == 'G') {
            return "png";
        }
        throw new ValidationException("media.format");
    }

    /** Dimensions lues via l'en-tête, sans décodage complet. */
    private static void checkDimensions(byte[] input) throws IOException {
        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(input))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                throw new ValidationException("media.unreadable");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(iis);
                long w = reader.getWidth(0);
                long h = reader.getHeight(0);
                if (w > MAX_DIMENSION_INPUT || h > MAX_DIMENSION_INPUT || w * h > MAX_PIXELS) {
                    throw new ValidationException("media.dimensions");
                }
            } finally {
                reader.dispose();
            }
        }
    }

    /**
     * Lecture minimale de l'orientation EXIF (tag 0x0112) dans le segment APP1 d'un JPEG.
     * Retourne 1 (normale) si absente ou illisible.
     */
    static int readExifOrientation(byte[] jpeg) {
        try {
            int i = 2;
            while (i + 4 < jpeg.length) {
                if ((jpeg[i] & 0xFF) != 0xFF) {
                    return 1;
                }
                int marker = jpeg[i + 1] & 0xFF;
                int length = ((jpeg[i + 2] & 0xFF) << 8) | (jpeg[i + 3] & 0xFF);
                if (marker == 0xE1 && i + 4 + 6 <= jpeg.length
                        && jpeg[i + 4] == 'E' && jpeg[i + 5] == 'x' && jpeg[i + 6] == 'i' && jpeg[i + 7] == 'f') {
                    return parseTiffOrientation(jpeg, i + 10, length - 8);
                }
                if (marker == 0xDA) {
                    return 1;
                }
                i += 2 + length;
            }
        } catch (Exception ignored) {
            // Orientation par défaut si EXIF corrompu
        }
        return 1;
    }

    private static int parseTiffOrientation(byte[] data, int tiffStart, int available) {
        if (available < 8) {
            return 1;
        }
        boolean littleEndian = data[tiffStart] == 'I';
        int ifdOffset = readInt(data, tiffStart + 4, 4, littleEndian);
        int ifd = tiffStart + ifdOffset;
        int count = readInt(data, ifd, 2, littleEndian);
        for (int e = 0; e < count; e++) {
            int entry = ifd + 2 + e * 12;
            if (entry + 12 > data.length) {
                return 1;
            }
            int tag = readInt(data, entry, 2, littleEndian);
            if (tag == 0x0112) {
                return readInt(data, entry + 8, 2, littleEndian);
            }
        }
        return 1;
    }

    private static int readInt(byte[] data, int offset, int len, boolean littleEndian) {
        int value = 0;
        for (int i = 0; i < len; i++) {
            int b = data[offset + (littleEndian ? len - 1 - i : i)] & 0xFF;
            value = (value << 8) | b;
        }
        return value;
    }

    private static BufferedImage applyExifOrientation(BufferedImage image, int orientation) {
        if (orientation <= 1 || orientation > 8) {
            return image;
        }
        int w = image.getWidth();
        int h = image.getHeight();
        AffineTransform t = new AffineTransform();
        boolean swap = orientation >= 5;
        switch (orientation) {
            case 2 -> { t.scale(-1, 1); t.translate(-w, 0); }
            case 3 -> { t.translate(w, h); t.rotate(Math.PI); }
            case 4 -> { t.scale(1, -1); t.translate(0, -h); }
            case 5 -> { t.rotate(Math.PI / 2); t.scale(1, -1); }
            case 6 -> { t.translate(h, 0); t.rotate(Math.PI / 2); }
            case 7 -> { t.scale(-1, 1); t.translate(-h, 0); t.translate(0, w); t.rotate(3 * Math.PI / 2); }
            case 8 -> { t.translate(0, w); t.rotate(3 * Math.PI / 2); }
        }
        BufferedImage out = new BufferedImage(swap ? h : w, swap ? w : h, BufferedImage.TYPE_INT_RGB);
        AffineTransformOp op = new AffineTransformOp(t, AffineTransformOp.TYPE_BILINEAR);
        op.filter(image, out);
        return out;
    }

    private static BufferedImage scaleDown(BufferedImage image, int maxDim) {
        int w = image.getWidth();
        int h = image.getHeight();
        if (w <= maxDim && h <= maxDim && image.getType() == BufferedImage.TYPE_INT_RGB) {
            return image;
        }
        double scale = Math.min(1.0, (double) maxDim / Math.max(w, h));
        int nw = Math.max(1, (int) Math.round(w * scale));
        int nh = Math.max(1, (int) Math.round(h * scale));
        BufferedImage out = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
        var g = out.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, nw, nh);
        g.drawImage(image, 0, 0, nw, nh, null);
        g.dispose();
        return out;
    }

    private static byte[] toJpeg(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "jpg", out)) {
            throw new ValidationException("media.encode");
        }
        return out.toByteArray();
    }
}
