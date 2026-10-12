package com.dayan.food.image;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** The container memory budget also includes this child; no shell or user-controlled options. */
public final class ImageSubprocess {
    private ImageSubprocess() {}
    public static void decode(Path original, Path output) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        Collections.addAll(command, "-Xmx128m", "-XX:MaxMetaspaceSize=48m", "-XX:MaxDirectMemorySize=8m",
                "-XX:ReservedCodeCacheSize=16m", "-XX:ActiveProcessorCount=1", "-Djava.awt.headless=true");
        String classpath = System.getProperty("java.class.path");
        Collections.addAll(command, "-cp", classpath);
        if (isBootArchive(classpath)) {
            command.add("-Dloader.main=" + ImageWorker.class.getName());
            command.add("org.springframework.boot.loader.launch.PropertiesLauncher");
        } else command.add(ImageWorker.class.getName());
        command.add(original.toString()); command.add(output.toString());
        Process child = new ProcessBuilder(command).redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD).start();
        await(child, 20);
    }

    static void await(Process child, long seconds) throws Exception {
        try {
            if (!child.waitFor(seconds, TimeUnit.SECONDS)) throw new IllegalStateException("DECODE_TIMEOUT");
            if (child.exitValue() != 0) throw new IllegalStateException("DECODE_FAILED");
        } finally {
            if (child.isAlive()) { child.destroyForcibly(); child.waitFor(5, TimeUnit.SECONDS); }
        }
    }

    private static boolean isBootArchive(String classpath) throws java.io.IOException {
        if (!classpath.endsWith(".jar") || classpath.contains(java.io.File.pathSeparator)) return false;
        try (var archive = new java.util.jar.JarFile(classpath)) {
            var manifest = archive.getManifest();
            String main = manifest == null ? null : manifest.getMainAttributes().getValue("Main-Class");
            return main != null && main.startsWith("org.springframework.boot.loader.launch.");
        }
    }
}
