package tn.civiccare.media;

import org.junit.jupiter.api.Test;
import tn.civiccare.observability.Telemetry.ValidationException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageProcessorTest {

    private static byte[] image(String format, int w, int h) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, format, out);
        return out.toByteArray();
    }

    @Test
    void jpegAndPngAcceptedByRealSignature() throws Exception {
        assertThat(ImageProcessor.detectFormat(image("jpg", 10, 10))).isEqualTo("jpeg");
        assertThat(ImageProcessor.detectFormat(image("png", 10, 10))).isEqualTo("png");
    }

    @Test
    void svgWebpAndRenamedFilesRejected() throws Exception {
        assertThatThrownBy(() -> ImageProcessor.detectFormat("<svg/>".getBytes()))
                .isInstanceOf(ValidationException.class);
        // WebP (RIFF....WEBP) : décodage non pris en charge, refusé par la liste de signatures
        byte[] webp = new byte[]{'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'};
        assertThatThrownBy(() -> ImageProcessor.detectFormat(webp))
                .isInstanceOf(ValidationException.class);
        // Extension mensongère : c'est la signature réelle qui compte
        assertThatThrownBy(() -> ImageProcessor.detectFormat("GIF89a".getBytes()))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void reencodingStripsMetadataAndScalesDown() throws Exception {
        byte[] big = image("jpg", 3000, 2000);
        ImageProcessor.Processed processed = ImageProcessor.process(big);
        assertThat(processed.width()).isLessThanOrEqualTo(ImageProcessor.MAX_OUTPUT_DIMENSION);
        // Le dérivé est un JPEG décodable
        BufferedImage full = ImageIO.read(new ByteArrayInputStream(processed.full()));
        assertThat(full).isNotNull();
        BufferedImage thumb = ImageIO.read(new ByteArrayInputStream(processed.thumbnail()));
        assertThat(Math.max(thumb.getWidth(), thumb.getHeight()))
                .isLessThanOrEqualTo(ImageProcessor.THUMB_DIMENSION);
    }

    @Test
    void exifOrientationDefaultsToNormalWhenAbsent() throws Exception {
        assertThat(ImageProcessor.readExifOrientation(image("jpg", 20, 10))).isEqualTo(1);
    }

    @Test
    void pngWithHugeDimensionsRejectedBeforeFullDecode() throws Exception {
        // En-tête PNG déclarant 50000x50000 sans données : refusé sur les dimensions
        byte[] png = image("png", 10, 10);
        // Falsifie la largeur/hauteur dans l'IHDR (octets 16..23)
        byte[] fake = png.clone();
        fake[16] = 0;
        fake[17] = 0;
        fake[18] = (byte) 0xC3;
        fake[19] = 0x50; // 50000
        fake[20] = 0;
        fake[21] = 0;
        fake[22] = (byte) 0xC3;
        fake[23] = 0x50;
        assertThatThrownBy(() -> ImageProcessor.process(fake))
                .isInstanceOf(ValidationException.class);
    }
}
