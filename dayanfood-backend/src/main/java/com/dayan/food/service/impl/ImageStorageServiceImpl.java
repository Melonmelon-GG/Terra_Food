package com.dayan.food.service.impl;

import com.dayan.food.service.ImageStorageService;
import com.dayan.food.mapper.FoodMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class ImageStorageServiceImpl implements ImageStorageService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");

    private final Path uploadDirectory;
    private final long maxImageBytes;
    private final Duration orphanRetention;
    private final FoodMapper foodMapper;

    public ImageStorageServiceImpl(
            @Value("${app.upload-directory:uploads}") String uploadDirectory,
            @Value("${app.upload.max-image-bytes:5242880}") long maxImageBytes,
            @Value("${app.upload.orphan-retention:24h}") Duration orphanRetention,
            FoodMapper foodMapper
    ) {
        this.uploadDirectory = Path.of(uploadDirectory).toAbsolutePath().normalize();
        this.maxImageBytes = maxImageBytes;
        this.orphanRetention = orphanRetention;
        this.foodMapper = foodMapper;
    }

    @Override
    public int cleanupOrphans() {
        if (!Files.isDirectory(uploadDirectory)) {
            return 0;
        }

        Instant cutoff = Instant.now().minus(orphanRetention);
        int deleted = 0;
        try (var files = Files.list(uploadDirectory)) {
            for (Path path : files.filter(Files::isRegularFile).toList()) {
                if (Files.getLastModifiedTime(path).toInstant().isAfter(cutoff)) {
                    continue;
                }
                String extension = getExtension(path.getFileName().toString());
                if (!ALLOWED_EXTENSIONS.contains(extension)) {
                    continue;
                }
                String imageUrl = "/uploads/" + path.getFileName();
                if (foodMapper.countByImageUrl(imageUrl) == 0 && Files.deleteIfExists(path)) {
                    deleted++;
                }
            }
            return deleted;
        } catch (IOException exception) {
            throw new IllegalStateException("清理未引用图片失败", exception);
        }
    }

    @Override
    public String store(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("上传图片不能为空");
        }
        if (file.getSize() > maxImageBytes) {
            throw new IllegalArgumentException("上传图片不能超过 5MB");
        }

        String extension = getExtension(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("仅支持 jpg、jpeg、png 和 webp 图片");
        }

        try {
            byte[] content = file.getBytes();
            String detectedExtension = detectImageExtension(content);
            String normalizedExtension = extension.equals("jpeg") ? "jpg" : extension;
            if (!detectedExtension.equals(normalizedExtension)) {
                throw new IllegalArgumentException("图片内容与文件扩展名不一致");
            }

            Files.createDirectories(uploadDirectory);
            String storedName = UUID.randomUUID() + "." + detectedExtension;
            Path target = uploadDirectory.resolve(storedName).normalize();
            if (!target.startsWith(uploadDirectory)) {
                throw new IllegalArgumentException("无效的文件名");
            }

            Files.write(target, content);
            return "/uploads/" + storedName;
        } catch (IOException exception) {
            throw new IllegalStateException("图片保存失败", exception);
        }
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    private String detectImageExtension(byte[] content) {
        if (content.length >= 3
                && unsigned(content[0]) == 0xff
                && unsigned(content[1]) == 0xd8
                && unsigned(content[2]) == 0xff) {
            return "jpg";
        }
        if (content.length >= 8
                && unsigned(content[0]) == 0x89
                && content[1] == 'P'
                && content[2] == 'N'
                && content[3] == 'G'
                && unsigned(content[4]) == 0x0d
                && unsigned(content[5]) == 0x0a
                && unsigned(content[6]) == 0x1a
                && unsigned(content[7]) == 0x0a) {
            return "png";
        }
        if (content.length >= 12
                && content[0] == 'R'
                && content[1] == 'I'
                && content[2] == 'F'
                && content[3] == 'F'
                && content[8] == 'W'
                && content[9] == 'E'
                && content[10] == 'B'
                && content[11] == 'P') {
            return "webp";
        }
        throw new IllegalArgumentException("文件内容不是受支持的图片格式");
    }

    private int unsigned(byte value) {
        return Byte.toUnsignedInt(value);
    }
}
