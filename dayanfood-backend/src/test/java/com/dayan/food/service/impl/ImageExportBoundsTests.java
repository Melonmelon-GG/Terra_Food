package com.dayan.food.service.impl;

import com.dayan.food.service.UploadTooLargeException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;

class ImageExportBoundsTests {
    @TempDir Path root;
    private ImageStorageServiceImpl service(Path uploads) {
        return new ImageStorageServiceImpl(uploads.toString(),5_242_880,Duration.ofHours(24),
                null,null,null,null,524_288_000,50,0);
    }
    @Test void oversizedHistoricalFileRejectedBeforeDecode() throws Exception {
        Path uploads=Files.createDirectory(root.resolve("uploads"));
        try (var file=new java.io.RandomAccessFile(uploads.resolve("large.jpg").toFile(),"rw")) { file.setLength(6_000_000); }
        assertThrows(UploadTooLargeException.class,()->service(uploads).readForExport("/uploads/large.jpg"));
    }
    @Test void escapedSymlinkRejected() throws Exception {
        Path uploads=Files.createDirectory(root.resolve("uploads"));
        Path secret=root.resolve("outside.png"); Files.writeString(secret,"private");
        Files.createSymbolicLink(uploads.resolve("alias.png"),secret);
        assertThrows(IllegalArgumentException.class,()->service(uploads).readForExport("/uploads/alias.png"));
    }
}
