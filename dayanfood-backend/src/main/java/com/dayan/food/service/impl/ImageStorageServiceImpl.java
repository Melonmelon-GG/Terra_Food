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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.dayan.food.security.AuthenticatedActor;

@Service
public class ImageStorageServiceImpl implements ImageStorageService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");

    private final Path uploadDirectory;
    private final long maxImageBytes;
    private final Duration orphanRetention;
    private final FoodMapper foodMapper;
    private final AppUserMapper appUserMapper;
    private final ImageAssetMapper imageAssetMapper;
    private final JdbcTemplate jdbc;
    private final long userBudgetBytes;
    private final int dailyFiles;
    private final long minimumFreeBytes;
    private final java.util.concurrent.Semaphore uploadSlots = new java.util.concurrent.Semaphore(4);

    public ImageStorageServiceImpl(
            @Value("${app.upload-directory:uploads}") String uploadDirectory,
            @Value("${app.upload.max-image-bytes:5242880}") long maxImageBytes,
            @Value("${app.upload.orphan-retention:24h}") Duration orphanRetention,
            FoodMapper foodMapper,
            AppUserMapper appUserMapper,
            ImageAssetMapper imageAssetMapper,
            JdbcTemplate jdbc,
            @Value("${app.upload.user-budget-bytes:524288000}") long userBudgetBytes,
            @Value("${app.upload.daily-files:50}") int dailyFiles,
            @Value("${app.upload.minimum-free-bytes:2147483648}") long minimumFreeBytes
    ) {
        this.uploadDirectory = Path.of(uploadDirectory).toAbsolutePath().normalize();
        this.maxImageBytes = maxImageBytes;
        this.orphanRetention = orphanRetention;
        this.foodMapper = foodMapper;
        this.appUserMapper = appUserMapper;
        this.imageAssetMapper = imageAssetMapper;
        this.jdbc=jdbc; this.userBudgetBytes=userBudgetBytes; this.dailyFiles=dailyFiles; this.minimumFreeBytes=minimumFreeBytes;
    }

    @Override
    public int cleanupOrphans() {
        // Fail closed even if an old deployment accidentally enables the former URL-only cleaner.
        // The maintenance inventory reports candidates; deletion needs verified asset references.
        throw new IllegalStateException("Automatic image deletion is isolated until asset references are verified");
    }

    @Override
    @Transactional
    public String store(MultipartFile file) {
        if (!uploadSlots.tryAcquire()) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "图片上传繁忙，请稍后重试");
        try { return storeReserved(file); }
        finally { uploadSlots.release(); }
    }

    private String storeReserved(MultipartFile file) {
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

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        var actor = AuthenticatedActor.resolve(appUserMapper, authentication == null ? null : authentication.getName());
        // This current-read lock precedes aggregate reads, including under MySQL REPEATABLE READ.
        jdbc.queryForObject("SELECT id FROM image_upload_budget_guard WHERE id=1 FOR UPDATE", Integer.class);
        Long used = jdbc.queryForObject("SELECT COALESCE(SUM(byte_size),0) FROM image_asset WHERE owner_user_id=?", Long.class, actor.getId());
        Long today = jdbc.queryForObject("SELECT COUNT(*) FROM image_asset WHERE owner_user_id=? AND created_at>=CURRENT_DATE()", Long.class, actor.getId());
        if (used + file.getSize() > userBudgetBytes || today >= dailyFiles)
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "图片上传额度已用完，请稍后重试或联系管理员");
        Path temporary = null;
        Path stored = null;
        boolean registered = false;
        try {
            Files.createDirectories(uploadDirectory);
            if (Files.getFileStore(uploadDirectory).getUsableSpace() - file.getSize() < minimumFreeBytes)
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "图片存储空间暂时不足");
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

            temporary = Files.createTempFile(uploadDirectory, ".upload-", ".tmp");
            Files.write(temporary, content);
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            stored = target;
            Path rollbackFile = target;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) try { Files.deleteIfExists(rollbackFile); }
                    catch (IOException ignored) { org.slf4j.LoggerFactory.getLogger(ImageStorageServiceImpl.class).warn("upload_rollback_cleanup_failed"); }
                }
            });
            String url = "/uploads/" + storedName;
            ImageAsset asset = new ImageAsset(url, dimensions.width(), dimensions.height());
            imageAssetMapper.insert(asset);
            jdbc.update("UPDATE image_asset SET owner_user_id=?, byte_size=? WHERE id=?", actor.getId(), file.getSize(), asset.getId());
            if (dimensions.pixels() > 24_000_000L) {
                imageAssetMapper.markFailed(asset.getId(), "PIXEL_LIMIT");
            }
            registered = true;
            return url;
        } catch (IOException exception) {
            throw new IllegalStateException("图片保存失败", exception);
        } finally {
            try {
                if (temporary != null) Files.deleteIfExists(temporary);
                if (!registered && stored != null) Files.deleteIfExists(stored);
            } catch (IOException ignored) {
                org.slf4j.LoggerFactory.getLogger(ImageStorageServiceImpl.class).warn("upload_cleanup_failed");
            }
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
            Path realRoot = uploadDirectory.toRealPath();
            Path realTarget = target.toRealPath();
            if (!realTarget.startsWith(realRoot) || !Files.isRegularFile(realTarget)) {
                throw new IllegalArgumentException("无效的站内图片路径");
            }
            // Reject historical oversized files before allocating. The bounded second read
            // also handles a file growing after the metadata check.
            if (Files.size(realTarget) > maxImageBytes) {
                throw new UploadTooLargeException("图片不能超过配置的大小限制");
            }
            byte[] content;
            try (var input = Files.newInputStream(realTarget)) {
                content = input.readNBytes(Math.toIntExact(Math.min(maxImageBytes, 5_242_880L)) + 1);
            }
            if (content.length > Math.min(maxImageBytes, 5_242_880L)) {
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
