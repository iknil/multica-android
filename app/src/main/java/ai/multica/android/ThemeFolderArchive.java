package ai.multica.android;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Builds the ZIP format accepted by the web theme importer from a SAF tree. */
final class ThemeFolderArchive {
    static final int MAX_BYTES = 1024 * 1024;
    static final int MAX_FILES = 64;

    static final class FileEntry {
        final String path;
        final byte[] bytes;

        FileEntry(String path, byte[] bytes) {
            this.path = path;
            this.bytes = bytes;
        }
    }

    private ThemeFolderArchive() {}

    static boolean safePart(String value) {
        if (value == null || value.isEmpty() || ".".equals(value) || "..".equals(value)
                || value.indexOf('/') >= 0 || value.indexOf('\\') >= 0) return false;
        for (int i = 0; i < value.length(); i++) {
            if (Character.isISOControl(value.charAt(i))) return false;
        }
        return true;
    }

    static byte[] zip(List<FileEntry> files) throws IOException {
        if (files.isEmpty() || files.size() > MAX_FILES)
            throw new IOException("Select a theme folder with at most 64 files");
        int total = 0;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            for (FileEntry file : files) {
                for (String part : file.path.split("/", -1)) {
                    if (!safePart(part)) throw new IOException("Invalid theme file name");
                }
                total += file.bytes.length;
                if (total > MAX_BYTES) throw new IOException("Theme exceeds 1 MB");
                CRC32 crc = new CRC32();
                crc.update(file.bytes);
                ZipEntry entry = new ZipEntry(file.path);
                entry.setMethod(ZipEntry.STORED);
                entry.setSize(file.bytes.length);
                entry.setCompressedSize(file.bytes.length);
                entry.setCrc(crc.getValue());
                zip.putNextEntry(entry);
                zip.write(file.bytes);
                zip.closeEntry();
            }
        }
        if (output.size() > MAX_BYTES) throw new IOException("Theme ZIP exceeds 1 MB");
        return output.toByteArray();
    }
}
