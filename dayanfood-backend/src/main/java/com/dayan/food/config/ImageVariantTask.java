package com.dayan.food.config;

import com.dayan.food.entity.po.ImageAsset;
import com.dayan.food.mapper.ImageAssetMapper;
import com.dayan.food.image.ImageDimensions;
import com.dayan.food.image.ImageSubprocess;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@ConditionalOnProperty(name = "app.upload.variant-processing-enabled", havingValue = "true")
public class ImageVariantTask {
    private static final Logger LOGGER = LoggerFactory.getLogger(ImageVariantTask.class);
    private static final int[] SIZES = {320, 640, 1280};
    private final ImageAssetMapper mapper;
    private final Path uploadDirectory;
    private final AtomicBoolean ready = new AtomicBoolean();
    private final AtomicBoolean processing = new AtomicBoolean();
    private final String owner = UUID.randomUUID().toString();

    public ImageVariantTask(ImageAssetMapper mapper,
                            @Value("${app.upload-directory:uploads}") String uploadDirectory) {
        this.mapper = mapper;
        this.uploadDirectory = Path.of(uploadDirectory).toAbsolutePath().normalize();
    }

    @EventListener(ApplicationReadyEvent.class)
    void recoverInterruptedWork() {
        mapper.recoverExpired();
        ready.set(true);
    }

    @Scheduled(fixedDelayString = "${app.upload.variant-delay:1000}")
    public void processNext() {
        if (!ready.get() || !processing.compareAndSet(false, true)) return;
        try {
            mapper.recoverExpired();
            for (ImageAsset asset : mapper.findPending(1)) {
                if (mapper.claim(asset.getId(), owner) != 1) continue;
                try { process(asset); }
                catch (Exception exception) {
                    mapper.failOwned(asset.getId(), owner, exception instanceof IllegalStateException
                            && "DECODE_TIMEOUT".equals(exception.getMessage()) ? "DECODE_TIMEOUT" : "PROCESSING_FAILED");
                    LOGGER.warn("image_variant_failed assetId={} category={}", asset.getId(), exception.getClass().getSimpleName());
                }
            }
        } finally { processing.set(false); }
    }

    private void process(ImageAsset asset) throws Exception {
        Path original = resolveUrl(asset.getOriginalUrl());
        if (ImageDimensions.read(original).pixels() > 24_000_000L) {
            mapper.failOwned(asset.getId(), owner, "PIXEL_LIMIT"); return;
        }
        Path variants = uploadDirectory.resolve("variants");
        Files.createDirectories(variants);
        Path work = Files.createTempDirectory(variants, ".worker-");
        String[] urls = new String[3];
        try {
            ImageSubprocess.decode(original, work);
            String format = Files.exists(work.resolve("320.png")) ? "png" : "jpg";
            for (int i=0; i<SIZES.length; i++) {
                // Unique worker-owned names prevent a late worker overwriting a replacement task.
                String filename = asset.getId()+"-"+owner+"-"+SIZES[i]+"."+format;
                Files.move(work.resolve(SIZES[i]+"."+format), variants.resolve(filename), StandardCopyOption.ATOMIC_MOVE);
                urls[i] = "/uploads/variants/"+filename;
            }
            if (mapper.readyOwned(asset.getId(), owner, urls[0], urls[1], urls[2]) != 1)
                throw new IllegalStateException("LEASE_LOST");
            urls = null; // The committed asset owns the files now.
        } finally {
            if (urls != null) for (String url : urls) if (url != null) Files.deleteIfExists(resolveUrl(url));
            try (var files=Files.list(work)) { for (Path path : files.toList()) Files.deleteIfExists(path); }
            Files.deleteIfExists(work);
        }
    }

    private Path resolveUrl(String url) {
        if (url == null || !url.startsWith("/uploads/")) throw new IllegalArgumentException("invalid image url");
        Path path = uploadDirectory.resolve(url.substring("/uploads/".length())).normalize();
        if (!path.startsWith(uploadDirectory)) throw new IllegalArgumentException("invalid image path");
        return path;
    }
}
