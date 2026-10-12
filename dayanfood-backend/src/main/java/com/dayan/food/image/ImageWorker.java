package com.dayan.food.image;

import net.coobird.thumbnailator.Thumbnails;
import javax.imageio.ImageIO;
import java.nio.file.*;

/** Standalone decoder. Never initializes Spring, connections, migrations or schedulers. */
public final class ImageWorker {
    private ImageWorker() {}
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("expected original and private output directory");
        Path original = Path.of(args[0]).toRealPath();
        Path output = Path.of(args[1]).toRealPath();
        ImageDimensions dimensions = ImageDimensions.read(original);
        if (dimensions.pixels() > 24_000_000L) throw new IllegalArgumentException("PIXEL_LIMIT");
        var image = ImageIO.read(original.toFile());
        if (image == null) throw new IllegalArgumentException("DECODE_FAILED");
        String format = image.getColorModel().hasAlpha() ? "png" : "jpg";
        for (int size : new int[]{320, 640, 1280}) {
            var builder = Thumbnails.of(image)
                    .scale(Math.min(1d, size / (double)Math.max(image.getWidth(), image.getHeight())))
                    .outputFormat(format);
            if (format.equals("jpg")) builder.outputQuality(.82d);
            builder.toFile(output.resolve(size + "." + format).toFile());
        }
        image.flush();
    }
}
