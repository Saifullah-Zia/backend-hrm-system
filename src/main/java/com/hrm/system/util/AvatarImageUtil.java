package com.hrm.system.util;

import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class AvatarImageUtil {

    private static final int MAX_DIMENSION = 512; // 512x512 max avatar dimensions

    public record ProcessedImage(byte[] data, String extension, String mimeType) {}

    /**
     * Inspects magic bytes / reads image stream using ImageIO.
     * Resizes image to fit max 512x512 while preserving aspect ratio.
     * Re-encodes to strip EXIF/GPS metadata and compress data.
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

        // 2. Determine target dimensions (max 512x512)
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

        // Ensure minimum 1px dimension
        targetWidth = Math.max(1, targetWidth);
        targetHeight = Math.max(1, targetHeight);

        // Determine output format (PNG if transparent/has alpha, otherwise JPEG)
        boolean hasAlpha = originalImage.getColorModel().hasAlpha();
        String formatName = hasAlpha ? "png" : "jpg";
        String extension = hasAlpha ? ".png" : ".jpg";
        String mimeType = hasAlpha ? "image/png" : "image/jpeg";
        int imageType = hasAlpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;

        // 3. Re-encode & Resize (strips EXIF metadata automatically)
        BufferedImage resizedImage = new BufferedImage(targetWidth, targetHeight, imageType);
        Graphics2D g2d = resizedImage.createGraphics();

        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        if (!hasAlpha) {
            g2d.setColor(java.awt.Color.WHITE);
            g2d.fillRect(0, 0, targetWidth, targetHeight);
        }

        g2d.drawImage(originalImage, 0, 0, targetWidth, targetHeight, null);
        g2d.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(resizedImage, formatName, baos);
        byte[] imageBytes = baos.toByteArray();

        return new ProcessedImage(imageBytes, extension, mimeType);
    }
}
