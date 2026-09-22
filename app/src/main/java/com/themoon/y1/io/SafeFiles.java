package com.themoon.y1.io;

import java.io.*;

/** File operations shared by the upload server and bundled theme installer. */
public final class SafeFiles {
    private SafeFiles() {}

    public static File resolve(File root, String path) throws IOException {
        if (path == null || new File(path).isAbsolute() || path.indexOf('\0') >= 0)
            throw new IOException("Invalid path");
        File base = root.getCanonicalFile();
        File file = new File(base, path).getCanonicalFile();
        if (!file.equals(base) && !file.getPath().startsWith(base.getPath() + File.separator))
            throw new IOException("Path outside shared folder");
        return file;
    }

    public static File child(File root, String name) throws IOException {
        if (name == null || name.isEmpty() || name.equals(".") || name.equals("..")
                || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0)
            throw new IOException("Invalid file name");
        return resolve(root, name);
    }

    /** Keep the old file intact until the complete request body is durable. */
    public static void replace(File target, InputStream input, int length) throws IOException {
        if (length < 0) throw new IOException("Invalid content length");
        File temporary = File.createTempFile(".y1-upload-", ".tmp", target.getParentFile());
        try {
            try (FileOutputStream output = new FileOutputStream(temporary)) {
                byte[] buffer = new byte[8192];
                int remaining = length;
                while (remaining > 0) {
                    int count = input.read(buffer, 0, Math.min(buffer.length, remaining));
                    if (count < 0) throw new EOFException("Incomplete upload");
                    if (count == 0) continue;
                    output.write(buffer, 0, count);
                    remaining -= count;
                }
                output.getFD().sync();
            }
            if (!temporary.renameTo(target)) throw new IOException("Could not replace file");
        } finally {
            if (temporary.exists()) temporary.delete();
        }
    }
}
