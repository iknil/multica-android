package ai.multica.android;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Converts a selected SAF directory into the ZIP accepted by the web theme importer. */
final class ThemeFolderBridge {
    static final int REQUEST_CODE = 42;
    private static final int CHUNK = 16 * 1024;
    private final Activity activity;
    private final WebView webView;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private String token;
    private String pendingToken;

    ThemeFolderBridge(Activity activity, WebView webView) {
        this.activity = activity;
        this.webView = webView;
    }

    synchronized void invalidate() {
        token = null;
        pendingToken = null;
    }

    void destroy() {
        invalidate();
        io.shutdownNow();
    }

    void installScript() {
        String script;
        try (InputStream input = activity.getAssets().open("theme-folder-import.js")) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
            script = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            toast("Theme folder import unavailable");
            return;
        }
        synchronized (this) {
            token = UUID.randomUUID().toString();
            script = script.replace("__TOKEN__", token);
        }
        webView.evaluateJavascript(script, null);
    }

    @JavascriptInterface public void choose(String candidate) {
        main.post(() -> {
            synchronized (ThemeFolderBridge.this) {
                if (token == null || !token.equals(candidate) || pendingToken != null
                        || !UrlPolicy.isAppUrl(webView.getUrl())) return;
                pendingToken = candidate;
            }
            try {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                activity.startActivityForResult(intent, REQUEST_CODE);
            } catch (android.content.ActivityNotFoundException exception) {
                clearPending(candidate);
                toast("No folder picker available");
            }
        });
    }

    void onTreePicked(Uri tree) {
        String candidate;
        synchronized (this) {
            candidate = pendingToken;
        }
        if (candidate == null) return;
        if (tree == null) {
            clearPending(candidate);
            webView.evaluateJavascript("window.__multicaThemeFolderCancel && "
                    + "window.__multicaThemeFolderCancel(" + JSONObject.quote(candidate) + ")", null);
            return;
        }
        io.execute(() -> {
            try {
                List<ThemeFolderArchive.FileEntry> files = new ArrayList<>();
                String rootId = DocumentsContract.getTreeDocumentId(tree);
                int[] counts = new int[3]; // files, uncompressed bytes, visited nodes
                collect(tree, rootId, "", 0, counts, files);
                byte[] archive = ThemeFolderArchive.zip(files);
                String encoded = Base64.encodeToString(archive, Base64.NO_WRAP);
                main.post(() -> sendChunk(candidate, encoded, 0));
            } catch (IOException exception) {
                main.post(() -> {
                    clearPending(candidate);
                    toast(exception.getMessage());
                });
            } catch (RuntimeException exception) {
                main.post(() -> {
                    clearPending(candidate);
                    toast("Theme folder import failed");
                });
            }
        });
    }

    private void collect(Uri tree, String documentId, String prefix, int depth, int[] counts,
            List<ThemeFolderArchive.FileEntry> files) throws IOException {
        if (depth > 8) throw new IOException("Theme folder is too deep");
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, documentId);
        String[] columns = {DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE};
        try (Cursor cursor = activity.getContentResolver().query(children, columns,
                null, null, null)) {
            if (cursor == null) throw new IOException("Cannot read selected folder");
            while (cursor.moveToNext()) {
                if (++counts[2] > 128) throw new IOException("Theme folder has too many entries");
                String id = cursor.getString(0);
                String name = cursor.getString(1);
                String mime = cursor.getString(2);
                if (!ThemeFolderArchive.safePart(name)) throw new IOException("Invalid theme file name");
                String path = prefix + name;
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                    collect(tree, id, path + "/", depth + 1, counts, files);
                } else {
                    if (++counts[0] > ThemeFolderArchive.MAX_FILES)
                        throw new IOException("Theme folder has too many files");
                    Uri document = DocumentsContract.buildDocumentUriUsingTree(tree, id);
                    byte[] data = readFile(document, counts);
                    files.add(new ThemeFolderArchive.FileEntry(path, data));
                }
            }
        }
    }

    private byte[] readFile(Uri document, int[] counts) throws IOException {
        try (InputStream input = activity.getContentResolver().openInputStream(document)) {
            if (input == null) throw new IOException("Cannot read theme file");
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int size;
            while ((size = input.read(buffer)) != -1) {
                counts[1] += size;
                if (counts[1] > ThemeFolderArchive.MAX_BYTES)
                    throw new IOException("Theme exceeds 1 MB");
                output.write(buffer, 0, size);
            }
            return output.toByteArray();
        }
    }

    private void sendChunk(String candidate, String encoded, int offset) {
        synchronized (this) {
            if (!candidate.equals(pendingToken) || !candidate.equals(token)
                    || !UrlPolicy.isAppUrl(webView.getUrl())) return;
        }
        int end = Math.min(encoded.length(), offset + CHUNK);
        boolean last = end == encoded.length();
        String call = "window.__multicaThemeFolderChunk && window.__multicaThemeFolderChunk("
                + JSONObject.quote(candidate) + "," + JSONObject.quote(encoded.substring(offset, end))
                + "," + last + ")";
        webView.evaluateJavascript(call, result -> {
            if (!"true".equals(result)) {
                clearPending(candidate);
                toast("Theme folder could not be imported");
            } else if (last) clearPending(candidate);
            else sendChunk(candidate, encoded, end);
        });
    }

    private synchronized void clearPending(String candidate) {
        if (candidate.equals(pendingToken)) pendingToken = null;
    }

    private void toast(String message) {
        main.post(() -> Toast.makeText(activity, message, Toast.LENGTH_SHORT).show());
    }
}
