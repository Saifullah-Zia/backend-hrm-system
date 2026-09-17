package com.hrm.system.util;

import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

public class AvatarImageUtil {

    /** Maximum pixel dimension for the output avatar (width or height). */
    private static final int MAX_DIMENSION = 800;

    /** JPEG output quality (0.0 – 1.0). 0.95 = near-lossless, great for faces. */
    private static final float JPEG_QUALITY = 0.95f;

    public record ProcessedImage(byte[] data, String extension, String mimeType) {}

    /**
     * Validates the image file using magic bytes (via ImageIO), resizes it to fit
     * within MAX_DIMENSION × MAX_DIMENSION preserving aspect ratio, and re-encodes
     * it at high quality (JPEG: 95%) to strip all EXIF/GPS metadata.
     */
    public static ProcessedImage validateAndProcessAvatar(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File cannot be empty.");
        }

        byte[] rawBytes = file.getBytes();
        if (rawBytes.length == 0) {
            throw new IllegalArgumentException("File cannot be empty.");
        }

        // 1. Magic bytes & ImageIO validation (rejects non-image payloads even if renamed .png)
        BufferedImage originalImage;
        try (InputStream is = new ByteArrayInputStream(rawBytes)) {
            originalImage = ImageIO.read(is);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse image file: " + e.getMessage());
        }

        if (originalImage == null) {
            throw new IllegalArgumentException("Invalid image file format or corrupted file signature.");
        }

        int origWidth = originalImage.getWidth();
        int origHeight = originalImage.getHeight();

        if (origWidth <= 0 || origHeight <= 0) {
            throw new IllegalArgumentException("Invalid image dimensions.");
        }

        // 2. Determine target dimensions (scale down only if needed)
        int targetWidth = origWidth;
        int targetHeight = origHeight;

        if (origWidth > MAX_DIMENSION || origHeight > MAX_DIMENSION) {
            if (origWidth >= origHeight) {
                targetWidth = MAX_DIMENSION;
                targetHeight = (int) Math.round((double) origHeight * MAX_DIMENSION / origWidth);
            } else {
                targetHeight = MAX_DIMENSION;
                targetWidth = (int) Math.round((double) origWidth * MAX_DIMENSION / origHeight);
            }
        }

        targetWidth = Math.max(1, targetWidth);
        targetHeight = Math.max(1, targetHeight);

        // 3. Determine output format (PNG for transparent images, JPEG for everything else)
        boolean hasAlpha = originalImage.getColorModel().hasAlpha();
        String formatName = hasAlpha ? "png" : "jpg";
        String extension = hasAlpha ? ".png" : ".jpg";
        String mimeType = hasAlpha ? "image/png" : "image/jpeg";
        int imageType = hasAlpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;

        // 4. Re-encode & Resize using BICUBIC interpolation (sharper than BILINEAR for photos)
        BufferedImage resizedImage = new BufferedImage(targetWidth, targetHeight, imageType);
        Graphics2D g2d = resizedImage.createGraphics();

        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING,     RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,  RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);

        if (!hasAlpha) {
            // Fill white background for JPEG (no transparency channel)
            g2d.setColor(java.awt.Color.WHITE);
            g2d.fillRect(0, 0, targetWidth, targetHeight);
        }

        g2d.drawImage(originalImage, 0, 0, targetWidth, targetHeight, null);
        g2d.dispose();

        byte[] imageBytes;

        if (hasAlpha) {
            // PNG: lossless — just write directly
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(resizedImage, "png", baos);
            imageBytes = baos.toByteArray();
        } else {
            // JPEG: use ImageWriter to control compression quality
            imageBytes = writeJpegWithQuality(resizedImage, JPEG_QUALITY);
        }

        return new ProcessedImage(imageBytes, extension, mimeType);
    }

    /**
     * Encodes a BufferedImage as JPEG at the specified quality (0.0–1.0).
     * This avoids the default ~60% quality used by plain ImageIO.write().
     */
    private static byte[] writeJpegWithQuality(BufferedImage image, float quality) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            // Fallback: plain write (should never happen on standard JDK)
            ImageIO.write(image, "jpg", baos);
            return baos.toByteArray();
        }

        ImageWriter writer = writers.next();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
            writer.setOutput(ios);

            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality); // 0.95 = near-lossless

            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }

        return baos.toByteArray();
    }
}
