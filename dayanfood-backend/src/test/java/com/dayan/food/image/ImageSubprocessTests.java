package com.dayan.food.image;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

class ImageSubprocessTests {
    @TempDir Path root;

    @Test void transparentImageProducesReadableVariantsWithoutUpscaling() throws Exception {
        Path original = root.resolve("original.png");
        var input = new BufferedImage(48, 32, BufferedImage.TYPE_INT_ARGB);
        input.setRGB(10, 10, 0x80123456);
        ImageIO.write(input, "png", original.toFile());
        Path work = Files.createDirectory(root.resolve("work"));
        ImageSubprocess.decode(original, work);
        for (int size : new int[]{320,640,1280}) {
            var variant=ImageIO.read(work.resolve(size+".png").toFile());
            assertEquals(48, variant.getWidth()); assertEquals(32, variant.getHeight());
            assertTrue(variant.getColorModel().hasAlpha());
        }
        assertTrue(Files.exists(original));
    }

    @Test void corruptImageFailsInChildAndLeavesOriginalIntact() throws Exception {
        Path original=root.resolve("broken.webp"); Files.writeString(original,"RIFFbrokenWEBP");
        Path work=Files.createDirectory(root.resolve("bad-work"));
        assertThrows(IllegalStateException.class,()->ImageSubprocess.decode(original,work));
        assertEquals("RIFFbrokenWEBP",Files.readString(original));
        try (var files=Files.list(work)) { assertEquals(0,files.count()); }
    }

    @Test void timeoutTerminatesChild() throws Exception {
        Process child=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),
                "-cp",System.getProperty("java.class.path"),SlowWorker.class.getName())
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        var failure=assertThrows(IllegalStateException.class,()->ImageSubprocess.await(child,1));
        assertEquals("DECODE_TIMEOUT",failure.getMessage());
        assertFalse(child.isAlive());
    }

    public static class SlowWorker {
        public static void main(String[] args) throws Exception { Thread.sleep(60_000); }
    }
}
