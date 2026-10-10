package com.dayan.food.image;

import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Explicit maintenance entry point: no Spring/Flyway/Web/scheduler initialization. */
public final class ImageMaintenance {
    private ImageMaintenance() {}
    public static void main(String[] args) throws Exception {
        Set<String> flags = Set.of(args);
        boolean apply = flags.contains("--apply");
        if (!apply && !flags.contains("--dry-run")) throw new IllegalArgumentException("select --dry-run or --apply");
        if (apply && flags.contains("--dry-run")) throw new IllegalArgumentException("conflicting modes");
        Path root = Path.of(required("UPLOAD_DIRECTORY")).toRealPath();
        if (!Files.isDirectory(root)) throw new IllegalArgumentException("upload directory missing");
        int batch = Integer.parseInt(System.getenv().getOrDefault("IMAGE_BATCH_SIZE", "100"));
        if (batch < 1 || batch > 1000) throw new IllegalArgumentException("batch must be 1..1000");
        String after = System.getenv().getOrDefault("IMAGE_AFTER", "");
        int examined=0, changed=0, failed=0; String last=after;
        try (Connection db = DriverManager.getConnection(required("DB_URL"), required("DB_USERNAME"), required("DB_PASSWORD"))) {
            db.setReadOnly(!apply); db.setAutoCommit(false);
            // A bounded, database-ordered cursor scans references, never the whole uploads directory.
            String sql = "SELECT url FROM (SELECT image_url url FROM food WHERE image_url IS NOT NULL "
                    + "UNION SELECT avatar_url url FROM app_user WHERE avatar_url IS NOT NULL) refs "
                    + "WHERE url>? ORDER BY url LIMIT ?";
            try (PreparedStatement query=db.prepareStatement(sql)) {
                query.setString(1, after); query.setInt(2,batch); query.setQueryTimeout(15);
                try (ResultSet rows=query.executeQuery()) {
                    while (rows.next()) {
                        String url=rows.getString(1); last=url; examined++;
                        if (!url.startsWith("/uploads/") || url.startsWith("/uploads/variants/")) continue;
                        try {
                            Path file=root.resolve(url.substring(9)).normalize().toRealPath();
                            if (!file.startsWith(root) || !Files.isRegularFile(file) || Files.size(file)>5_242_880L)
                                throw new IllegalArgumentException("invalid source");
                            ImageDimensions dimensions=ImageDimensions.read(file);
                            try (PreparedStatement existing=db.prepareStatement("SELECT status FROM image_asset WHERE original_url=?")) {
                                existing.setString(1,url);
                                try (ResultSet found=existing.executeQuery()) {
                                    if (found.next()) {
                                        if (flags.contains("--retry-failed") && "FAILED".equals(found.getString(1))) {
                                            changed++;
                                            if (apply) try (PreparedStatement retry=db.prepareStatement("UPDATE image_asset SET status='PENDING',attempts=0,error_code=NULL WHERE original_url=? AND status='FAILED'")) {
                                                retry.setString(1,url); retry.executeUpdate();
                                            }
                                        }
                                        continue;
                                    }
                                }
                            }
                            changed++;
                            if (apply) try (PreparedStatement insert=db.prepareStatement("INSERT INTO image_asset (original_url,original_width,original_height,status,processor_version,byte_size) VALUES (?,?,?,'PENDING',1,?)")) {
                                insert.setString(1,url); insert.setInt(2,dimensions.width()); insert.setInt(3,dimensions.height()); insert.setLong(4,Files.size(file)); insert.executeUpdate();
                            }
                        } catch (Exception error) { failed++; }
                    }
                }
            }
            // A partly invalid batch is atomic: repair inputs then rerun the same cursor.
            if (apply && failed==0) db.commit(); else db.rollback();
        }
        System.out.printf("mode=%s examined=%d candidates=%d failed=%d committed=%s%n",apply?"apply":"dry-run",examined,changed,failed,apply && failed==0);
        // Cursor is a public asset URL, never a request body or credentials.
        System.out.println("nextAfter="+last);
        if (failed>0) throw new IllegalStateException("image batch failed; no changes committed");
    }
    private static String required(String key) {
        String value=System.getenv(key);
        if (value==null || value.isBlank()) throw new IllegalArgumentException("missing "+key);
        return value;
    }
}
