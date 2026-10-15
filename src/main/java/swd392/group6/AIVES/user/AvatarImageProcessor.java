package swd392.group6.AIVES.user;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import swd392.group6.AIVES.common.ApiException;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * Normalises any accepted picture into the stored avatar format (D37): centre-cropped square ("cover"),
 * at most {@value #SIZE}×{@value #SIZE} px, JPEG quality {@value #QUALITY}, no metadata (EXIF/GPS is dropped
 * because only pixels are re-encoded). Transparent areas become white.
 */
@Component
class AvatarImageProcessor {

    static final int SIZE = 512;
    static final float QUALITY = 0.85f;
    /** Refuse decompression bombs before decoding pixels. */
    static final int MAX_DIMENSION = 8000;

    AvatarImageProcessor() {
        // In the packaged Spring Boot jar, ImageIO does not see plugin jars (WebP) until it rescans the classpath.
        ImageIO.scanForPlugins();
    }

    byte[] toAvatarJpeg(byte[] input) {
        BufferedImage source = decode(input);
        int side = Math.min(source.getWidth(), source.getHeight());
        int x = (source.getWidth() - side) / 2;
        int y = (source.getHeight() - side) / 2;
        BufferedImage square = source.getSubimage(x, y, side, side);
        int target = Math.min(side, SIZE);
        return encodeJpeg(downscale(square, target));
    }

    private static BufferedImage decode(byte[] input) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(input))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (in == null || !readers.hasNext()) {
                throw unsupported();
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                if (reader.getWidth(0) > MAX_DIMENSION || reader.getHeight(0) > MAX_DIMENSION) {
                    throw ApiException.unprocessable("AVATAR_DIMENSIONS",
                            "The picture is too large (max " + MAX_DIMENSION + " px per side)");
                }
                BufferedImage image = reader.read(0);
                if (image == null || image.getWidth() < 1 || image.getHeight() < 1) {
                    throw unsupported();
                }
                return image;
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof ApiException api) {
                throw api;
            }
            throw unsupported();
        }
    }

    /** Halves the size step by step, then a final bicubic pass: sharp and free of aliasing. */
    private static BufferedImage downscale(BufferedImage image, int target) {
        BufferedImage current = toRgb(image, image.getWidth());
        int size = current.getWidth();
        while (size / 2 >= target) {
            size /= 2;
            current = resize(current, size);
        }
        return size == target ? current : resize(current, target);
    }

    private static BufferedImage toRgb(BufferedImage image, int size) {
        BufferedImage rgb = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, size, size);
        g.drawImage(image, 0, 0, size, size, null);
        g.dispose();
        return rgb;
    }

    private static BufferedImage resize(BufferedImage image, int size) {
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(image, 0, 0, size, size, null);
        g.dispose();
        return out;
    }

    private static byte[] encodeJpeg(BufferedImage image) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ImageOutputStream out = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(out);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(QUALITY);
            writer.write(null, new IIOImage(image, null, null), param);
        } catch (IOException e) {
            throw new IllegalStateException("Could not encode avatar", e);
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }

    private static ApiException unsupported() {
        return new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "AVATAR_UNSUPPORTED_TYPE",
                "Use a JPEG, PNG, WebP, GIF or BMP image");
    }
}
