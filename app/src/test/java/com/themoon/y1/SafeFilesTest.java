package com.themoon.y1.io;

import com.themoon.y1.io.SafeFiles;
import java.io.*;
import java.nio.file.*;

public final class SafeFilesTest {
    interface Checked { void run() throws Exception; }
    static void rejects(Checked operation) throws Exception {
        try { operation.run(); } catch (IOException expected) { return; }
        throw new AssertionError("Unsafe operation accepted");
    }
    @org.junit.Test
    public void protectsPathsAndPreservesInterruptedUploads() throws Exception {
        File root = Files.createTempDirectory("inni-files-test").toFile();
        File target = new File(root, "song.mp3");
        try {
            rejects(() -> SafeFiles.resolve(root, "../outside"));
            rejects(() -> SafeFiles.resolve(root, "../" + root.getName() + "-sibling/file"));
            rejects(() -> SafeFiles.resolve(root, "/etc/passwd"));
            rejects(() -> SafeFiles.child(root, "../song.mp3"));
            rejects(() -> SafeFiles.child(root, ""));
            Files.createSymbolicLink(new File(root, "escape").toPath(), root.getParentFile().toPath());
            rejects(() -> SafeFiles.resolve(root, "escape/outside"));
            if (!SafeFiles.resolve(root, "album/../song.mp3").equals(target)) throw new AssertionError();
            SafeFiles.replace(target, new ByteArrayInputStream(new byte[]{1,2,3}), 3);
            rejects(() -> SafeFiles.replace(target, new ByteArrayInputStream(new byte[]{9}), 2));
            if (!java.util.Arrays.equals(Files.readAllBytes(target.toPath()), new byte[]{1,2,3})) throw new AssertionError("Original lost");
            rejects(() -> SafeFiles.replace(target, new ByteArrayInputStream(new byte[0]), -1));
            SafeFiles.replace(target, new ByteArrayInputStream(new byte[]{4,5}), 2);
            if (!java.util.Arrays.equals(Files.readAllBytes(target.toPath()), new byte[]{4,5})) throw new AssertionError("Replacement failed");
            SafeFiles.replace(target, new ByteArrayInputStream(new byte[0]), 0);
            if (target.length() != 0) throw new AssertionError("Empty save failed");
            for (File file : root.listFiles()) if (file.getName().startsWith(".y1-upload-")) throw new AssertionError("Temporary file leaked");
            System.out.println("SafeFiles: all regression checks passed");
        } finally {
            for (File file : root.listFiles()) Files.delete(file.toPath());
            Files.delete(root.toPath());
        }
    }
}
