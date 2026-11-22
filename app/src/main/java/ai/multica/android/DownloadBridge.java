package ai.multica.android;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Streams same-origin browser-generated exports into Android Downloads. */
final class DownloadBridge {
    private static final long MAX_BYTES = 64L * 1024 * 1024;
    private final Context context;
    private final WebView webView;
    private final Handler main = new Handler(Looper.getMainLooper());
    private String token;
    private boolean trustedPage;
    private OutputStream output;
    private Uri pendingUri;
    private File pendingFile;
    private long written;

    DownloadBridge(Context context, WebView webView) {
        this.context = context;
        this.webView = webView;
    }

    synchronized void setTrustedPage(boolean trusted) {
        trustedPage = trusted;
        if (!trusted) {
            token = null;
            abort();
        }
    }

    void installCaptureScript() {
        String script;
        try (InputStream input = context.getAssets().open("download-capture.js")) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int length;
            while ((length = input.read(buffer)) != -1) bytes.write(buffer, 0, length);
            script = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            toast("Export support unavailable");
            return;
        }
        synchronized (this) {
            token = UUID.randomUUID().toString();
            trustedPage = true;
            script = script.replace("__TOKEN__", token);
        }
        webView.evaluateJavascript(script, null);
    }

    void captureFallback(String url, String name) {
        if (url == null || url.length() > 1_000_000 ||
                !(UrlPolicy.isAppBlobUrl(url) || url.startsWith("data:"))) return;
        synchronized (this) {
            if (!trustedPage) return;
        }
        String call = "window.__multicaDownloadSave && window.__multicaDownloadSave("
                + JSONObject.quote(url) + "," + JSONObject.quote(name) + ")";
        webView.evaluateJavascript(call, result -> {
            if ("null".equals(result) || "false".equals(result))
                toast("Export unavailable; retry from the page");
        });
    }

    @JavascriptInterface public synchronized boolean begin(String candidate, String name,
            String mimeType, long size) {
        if (!accept(candidate) || output != null || size < 0 || size > MAX_BYTES) return false;
        String safeName = safeName(name);
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, safeName);
                values.put(MediaStore.MediaColumns.MIME_TYPE,
                        mimeType == null || mimeType.isEmpty() ? "application/octet-stream" : mimeType);
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                values.put(MediaStore.MediaColumns.IS_PENDING, 1);
                pendingUri = context.getContentResolver().insert(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (pendingUri == null) throw new IOException("Downloads provider unavailable");
                output = context.getContentResolver().openOutputStream(pendingUri);
            } else {
                File directory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                if (directory == null) throw new IOException("Downloads directory unavailable");
                pendingFile = new File(directory, safeName);
                output = new FileOutputStream(pendingFile);
            }
            if (output == null) throw new IOException("Cannot open download");
            written = 0;
            return true;
        } catch (IOException | SecurityException exception) {
            abort();
            toast("Export could not start");
            return false;
        }
    }

    @JavascriptInterface public synchronized boolean append(String candidate, String base64) {
        if (!accept(candidate) || output == null) return false;
        try {
            byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
            if (written + bytes.length > MAX_BYTES) throw new IOException("Export too large");
            output.write(bytes);
            written += bytes.length;
            return true;
        } catch (IOException | IllegalArgumentException exception) {
            abort();
            toast("Export failed");
            return false;
        }
    }

    @JavascriptInterface public synchronized boolean finish(String candidate) {
        if (!accept(candidate) || output == null) return false;
        try {
            output.close();
            output = null;
            if (pendingUri != null && Build.VERSION.SDK_INT >= 29) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.IS_PENDING, 0);
                context.getContentResolver().update(pendingUri, values, null, null);
            }
            pendingUri = null;
            pendingFile = null;
            toast(Build.VERSION.SDK_INT >= 29
                    ? "Export saved to Downloads" : "Export saved in app files");
            return true;
        } catch (IOException | SecurityException exception) {
            abort();
            toast("Export failed");
            return false;
        }
    }

    @JavascriptInterface public synchronized void fail(String candidate) {
        if (!accept(candidate)) return;
        abort();
        toast("Export failed");
    }

    private boolean accept(String candidate) {
        return trustedPage && token != null && token.equals(candidate);
    }

    private void abort() {
        try {
            if (output != null) output.close();
        } catch (IOException ignored) {
        }
        output = null;
        if (pendingUri != null) context.getContentResolver().delete(pendingUri, null, null);
        if (pendingFile != null) pendingFile.delete();
        pendingUri = null;
        pendingFile = null;
        written = 0;
    }

    private static String safeName(String name) {
        if (name == null) return "multica-export";
        String safe = name.replaceAll("[\\\\/\\p{Cntrl}]", "_").trim();
        while (safe.startsWith(".")) safe = safe.substring(1);
        if (safe.isEmpty()) return "multica-export";
        return safe.substring(0, Math.min(safe.length(), 120));
    }

    private void toast(String message) {
        main.post(() -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show());
    }
}
