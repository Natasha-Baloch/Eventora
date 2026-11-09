package com.eventora.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.*;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** Validates (type, magic bytes, size, dimensions) and stores uploaded event images. */
@Service
public class ImageStorage {
    @Value("${eventora.upload-dir}") private String uploadDir;

    /** @return web path like /uploads/events/abc.jpg */
    public String store(MultipartFile file) throws Exception {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Please choose an image.");
        if (file.getSize() > 5 * 1024 * 1024) throw new IllegalArgumentException("Image must be 5 MB or smaller.");
        byte[] b = file.getBytes();
        String ext;
        if (b.length > 4 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8) ext = "jpg";
        else if (b.length > 4 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') ext = "png";
        else throw new IllegalArgumentException("Only JPG or PNG images are allowed.");
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(b));
        if (img == null) throw new IllegalArgumentException("The file is not a valid image.");
        if (img.getWidth() < 400 || img.getHeight() < 250) throw new IllegalArgumentException("Image is too small (minimum 400x250).");
        Path dir = Path.of(uploadDir, "events");
        Files.createDirectories(dir);
        String name = UUID.randomUUID() + "." + ext;
        Files.write(dir.resolve(name), b);
        return "/uploads/events/" + name;
    }
}
