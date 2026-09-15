package com.dayan.food.service.impl;

import com.dayan.food.mapper.AppUserMapper;
import com.dayan.food.service.ImageStorageService;
import com.dayan.food.service.UploadTooLargeException;
import com.dayan.food.mapper.FoodMapper;
import com.dayan.food.mapper.ImageAssetMapper;
import com.dayan.food.entity.po.ImageAsset;
import com.dayan.food.entity.vo.ImageExportVO;
import com.dayan.food.image.ImageDimensions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
    private final AppUserMapper appUserMapper;
    private final ImageAssetMapper imageAssetMapper;

    public ImageStorageServiceImpl(
            @Value("${app.upload-directory:uploads}") String uploadDirectory,
            @Value("${app.upload.max-image-bytes:5242880}") long maxImageBytes,
            @Value("${app.upload.orphan-retention:24h}") Duration orphanRetention,
            FoodMapper foodMapper,
            AppUserMapper appUserMapper,
            ImageAssetMapper imageAssetMapper
    ) {
        this.uploadDirectory = Path.of(uploadDirectory).toAbsolutePath().normalize();
        this.maxImageBytes = maxImageBytes;
        this.orphanRetention = orphanRetention;
        this.foodMapper = foodMapper;
        this.appUserMapper = appUserMapper;
        this.imageAssetMapper = imageAssetMapper;
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
                boolean referenced = foodMapper.countByImageUrl(imageUrl) > 0
                        || appUserMapper.countByAvatarUrl(imageUrl) > 0;
                if (!referenced && Files.deleteIfExists(path)) {
                    ImageAsset asset = imageAssetMapper.findByOriginalUrl(imageUrl);
                    deleteVariant(asset == null ? null : asset.getVariant320Url());
                    deleteVariant(asset == null ? null : asset.getVariant640Url());
                    deleteVariant(asset == null ? null : asset.getVariant1280Url());
                    imageAssetMapper.deleteByOriginalUrl(imageUrl);
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
            throw new UploadTooLargeException("上传图片不能超过 5MB");
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
            ImageDimensions dimensions = ImageDimensions.read(content);

            Files.createDirectories(uploadDirectory);
            String storedName = UUID.randomUUID() + "." + detectedExtension;
            Path target = uploadDirectory.resolve(storedName).normalize();
            if (!target.startsWith(uploadDirectory)) {
                throw new IllegalArgumentException("无效的文件名");
            }

            Path temporary = Files.createTempFile(uploadDirectory, ".upload-", ".tmp");
            Files.write(temporary, content);
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            String url = "/uploads/" + storedName;
            ImageAsset asset = new ImageAsset(url, dimensions.width(), dimensions.height());
            imageAssetMapper.insert(asset);
            if (dimensions.pixels() > 24_000_000L) {
                imageAssetMapper.markFailed(asset.getId(), "PIXEL_LIMIT");
            }
            return url;
        } catch (IOException exception) {
            throw new IllegalStateException("图片保存失败", exception);
        }
    }

    @Override
    public ImageExportVO readForExport(String imageUrl) {
        if (imageUrl == null || !imageUrl.startsWith("/uploads/")) {
            throw new IllegalArgumentException("无效的站内图片地址");
        }
        Path target = uploadDirectory.resolve(imageUrl.substring("/uploads/".length())).normalize();
        if (!target.startsWith(uploadDirectory) || !Files.isRegularFile(target)) {
            throw new IllegalArgumentException("站内图片不存在");
        }
        try {
            byte[] content = Files.readAllBytes(target);
            if (content.length > maxImageBytes) {
                throw new UploadTooLargeException("图片不能超过配置的大小限制");
            }
            String extension = detectImageExtension(content);
            ImageDimensions dimensions = ImageDimensions.read(content);
            if (dimensions.pixels() > 24_000_000L) {
                throw new IllegalArgumentException("图片像素过多");
            }
            String contentType = extension.equals("jpg") ? "image/jpeg" : "image/" + extension;
            return new ImageExportVO(content, contentType);
        } catch (IOException exception) {
            throw new IllegalStateException("站内图片读取失败", exception);
        }
    }

    private void deleteVariant(String url) throws IOException {
        if (url == null || !url.startsWith("/uploads/")) return;
        Path target = uploadDirectory.resolve(url.substring("/uploads/".length())).normalize();
        if (target.startsWith(uploadDirectory)) Files.deleteIfExists(target);
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
