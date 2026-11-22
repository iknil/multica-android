package ai.multica.android;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ThemeFolderArchiveTest {
    @Test public void folderBecomesStoredZipWithRelativePaths() throws Exception {
        byte[] manifest = "{\"apiVersion\":1}".getBytes(StandardCharsets.UTF_8);
        byte[] css = ".theme-light {}".getBytes(StandardCharsets.UTF_8);
        byte[] archive = ThemeFolderArchive.zip(Arrays.asList(
                new ThemeFolderArchive.FileEntry("manifest.json", manifest),
                new ThemeFolderArchive.FileEntry("assets/theme.css", css)));
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry first = input.getNextEntry();
            assertEquals("manifest.json", first.getName());
            assertEquals(ZipEntry.STORED, first.getMethod());
            assertArrayEquals(manifest, input.readAllBytes());
            ZipEntry second = input.getNextEntry();
            assertEquals("assets/theme.css", second.getName());
            assertArrayEquals(css, input.readAllBytes());
        }
    }

    @Test public void rejectsUnsafeNames() {
        assertTrue(ThemeFolderArchive.safePart("manifest.json"));
        assertFalse(ThemeFolderArchive.safePart(".."));
        assertFalse(ThemeFolderArchive.safePart("a/b"));
        assertFalse(ThemeFolderArchive.safePart("a\\b"));
    }
}
