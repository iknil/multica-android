package ai.multica.android;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.URLUtil;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

/** Hosts the production web application while supplying Android navigation and file handling. */
public final class MainActivity extends Activity {
    private static final int FILE_REQUEST = 41;
    private static final int INDIGO = Color.rgb(55, 60, 162);
    private WebView webView;
    private FrameLayout page;
    private ProgressBar progress;
    private TextView backButton;
    private TextView forwardButton;
    private TextView title;
    private View errorView;
    private DownloadBridge downloadBridge;
    private android.webkit.ValueCallback<Uri[]> fileCallback;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        buildLayout();
        configureWebView();
        if (state != null) {
            webView.restoreState(state);
            updateNavigation();
        } else {
            webView.loadUrl(initialUrl(getIntent()));
        }
    }

    private String initialUrl(Intent intent) {
        Uri uri = intent == null ? null : intent.getData();
        String value = uri == null ? null : uri.toString();
        return UrlPolicy.isAppUrl(value) ? value : UrlPolicy.HOME;
    }

    private int dp(float value) {
        return (int) (getResources().getDisplayMetrics().density * value + 0.5f);
    }

    private TextView button(String label, String description, View.OnClickListener click) {
        TextView button = new TextView(this);
        button.setText(label);
        button.setTextSize(22);
        button.setTextColor(INDIGO);
        button.setGravity(Gravity.CENTER);
        button.setContentDescription(description);
        button.setOnClickListener(click);
        button.setLayoutParams(new LinearLayout.LayoutParams(dp(48), dp(52)));
        return button;
    }

    private void buildLayout() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                return new WindowInsets.Builder(insets)
                        .setInsets(WindowInsets.Type.systemBars(), Insets.NONE).build();
            }
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets.replaceSystemWindowInsets(0, 0, 0, 0);
        });

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setBackgroundColor(Color.rgb(247, 248, 252));
        backButton = button("‹", "Back", v -> navigateBack());
        forwardButton = button("›", "Forward", v -> {
            if (webView.canGoForward()) webView.goForward();
        });
        toolbar.addView(backButton);
        toolbar.addView(forwardButton);
        title = new TextView(this);
        title.setText("Multica");
        title.setTextSize(17);
        title.setTextColor(Color.rgb(28, 31, 60));
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        toolbar.addView(title, new LinearLayout.LayoutParams(0, dp(52), 1));
        toolbar.addView(button("⋮", "More options", this::showMenu));
        root.addView(toolbar);

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgressTintList(android.content.res.ColorStateList.valueOf(INDIGO));
        root.addView(progress, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3)));

        page = new FrameLayout(this);
        webView = new WebView(this);
        page.addView(webView, new FrameLayout.LayoutParams(-1, -1));
        root.addView(page, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true); // Android's document picker returns content:// URIs.
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSupportMultipleWindows(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setMediaPlaybackRequiresUserGesture(true);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false);
        downloadBridge = new DownloadBridge(this, webView);
        webView.addJavascriptInterface(downloadBridge, "MulticaDownloads");

        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return route(request.getUrl().toString());
            }

            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return route(url);
            }

            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                downloadBridge.setTrustedPage(false);
                hideError();
                updateNavigation();
            }

            @Override public void onPageFinished(WebView view, String url) {
                updateNavigation();
                CookieManager.getInstance().flush();
                if (UrlPolicy.isAppUrl(url)) downloadBridge.installCaptureScript();
            }

            @Override public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
                updateNavigation();
                view.post(MainActivity.this::updateNavigation);
            }

            @Override public void onReceivedError(WebView view, WebResourceRequest request,
                                                   WebResourceError error) {
                if (request.isForMainFrame()) showError();
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onCreateWindow(WebView source, boolean isDialog,
                    boolean isUserGesture, android.os.Message resultMsg) {
                if (!isUserGesture) return false;
                WebView popup = new WebView(MainActivity.this);
                popup.setAlpha(0f);
                page.addView(popup, new FrameLayout.LayoutParams(1, 1));
                popup.getSettings().setJavaScriptEnabled(false);
                Runnable closePopup = () -> {
                    if (popup.getParent() != null) {
                        page.removeView(popup);
                        popup.destroy();
                    }
                };
                popup.postDelayed(closePopup, 30_000);
                popup.setDownloadListener((url, agent, disposition, mime, length) -> {
                    download(url, agent, disposition, mime);
                    popup.post(closePopup);
                });
                popup.setWebViewClient(new WebViewClient() {
                    private boolean handled;

                    private void handOff(String url) {
                        if (handled || url == null || "about:blank".equals(url)) return;
                        handled = true;
                        if (UrlPolicy.isAppUrl(url)) webView.loadUrl(url);
                        else route(url);
                        popup.post(closePopup);
                    }

                    @Override public boolean shouldOverrideUrlLoading(WebView view,
                            WebResourceRequest request) {
                        handOff(request.getUrl().toString());
                        return true;
                    }

                    @Override public void onPageStarted(WebView view, String url,
                            android.graphics.Bitmap favicon) {
                        handOff(url);
                    }
                });
                ((WebView.WebViewTransport) resultMsg.obj).setWebView(popup);
                resultMsg.sendToTarget();
                return true;
            }

            @Override public void onCloseWindow(WebView window) {
                if (window != webView && window.getParent() == page) {
                    page.removeView(window);
                    window.destroy();
                }
            }

            @Override public void onProgressChanged(WebView view, int value) {
                progress.setProgress(value);
                progress.setVisibility(value == 100 ? View.INVISIBLE : View.VISIBLE);
            }

            @Override public void onReceivedTitle(WebView view, String pageTitle) {
                if (pageTitle != null && !pageTitle.trim().isEmpty()) title.setText(pageTitle);
            }

            @Override public boolean onShowFileChooser(WebView view,
                    android.webkit.ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                try {
                    startActivityForResult(params.createIntent(), FILE_REQUEST);
                    return true;
                } catch (android.content.ActivityNotFoundException exception) {
                    fileCallback = null;
                    callback.onReceiveValue(null);
                    Toast.makeText(MainActivity.this, "No file picker available", Toast.LENGTH_SHORT).show();
                    return true;
                }
            }
        });
        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) ->
                download(url, userAgent, contentDisposition, mimeType));
    }

    private boolean route(String url) {
        if (UrlPolicy.isAppUrl(url)) return false;
        if (UrlPolicy.isWebUrl(url) || UrlPolicy.isExternalIntentUrl(url)) {
            openExternal(url);
        } else {
            Toast.makeText(this, "Link cannot be opened", Toast.LENGTH_SHORT).show();
        }
        return true;
    }

    private void openExternal(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (android.content.ActivityNotFoundException exception) {
            Toast.makeText(this, "No app can open this link", Toast.LENGTH_SHORT).show();
        }
    }

    private void download(String url, String userAgent, String disposition, String mimeType) {
        if (UrlPolicy.isAppBlobUrl(url) || (url != null && url.startsWith("data:"))) {
            downloadBridge.captureFallback(url, URLUtil.guessFileName(url, disposition, mimeType));
            return;
        }
        if (!UrlPolicy.isWebUrl(url)) {
            Toast.makeText(this, "Download requires HTTPS", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            String name = URLUtil.guessFileName(url, disposition, mimeType);
            request.setTitle(name);
            request.setMimeType(mimeType);
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, name);
            String cookie = CookieManager.getInstance().getCookie(url);
            if (cookie != null) request.addRequestHeader("Cookie", cookie);
            if (userAgent != null) request.addRequestHeader("User-Agent", userAgent);
            ((DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE)).enqueue(request);
            Toast.makeText(this, "Downloading " + name, Toast.LENGTH_SHORT).show();
        } catch (IllegalArgumentException | SecurityException exception) {
            Toast.makeText(this, "Download could not start", Toast.LENGTH_SHORT).show();
        }
    }

    private void showMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add("Home").setOnMenuItemClickListener(item -> {
            webView.loadUrl(UrlPolicy.HOME);
            return true;
        });
        menu.getMenu().add("Reload").setOnMenuItemClickListener(item -> {
            webView.reload();
            return true;
        });
        menu.getMenu().add("Open in browser").setOnMenuItemClickListener(item -> {
            openExternal(UrlPolicy.isAppUrl(webView.getUrl()) ? webView.getUrl() : UrlPolicy.HOME);
            return true;
        });
        menu.getMenu().add("Downloads").setOnMenuItemClickListener(item -> {
            try {
                startActivity(new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS));
            } catch (android.content.ActivityNotFoundException exception) {
                Toast.makeText(this, "Downloads app unavailable", Toast.LENGTH_SHORT).show();
            }
            return true;
        });
        menu.show();
    }

    private void showError() {
        if (errorView != null) return;
        LinearLayout error = new LinearLayout(this);
        error.setOrientation(LinearLayout.VERTICAL);
        error.setGravity(Gravity.CENTER);
        error.setBackgroundColor(Color.WHITE);
        TextView message = new TextView(this);
        message.setText("Unable to load Multica\nCheck your connection and try again.");
        message.setTextColor(Color.DKGRAY);
        message.setTextSize(18);
        message.setGravity(Gravity.CENTER);
        error.addView(message);
        TextView retry = button("Retry", "Retry loading page", v -> retryPage());
        retry.setTextSize(16);
        retry.setLayoutParams(new LinearLayout.LayoutParams(dp(120), dp(56)));
        error.addView(retry);
        errorView = error;
        page.addView(error, new FrameLayout.LayoutParams(-1, -1));
    }

    private void retryPage() {
        String url = webView.getUrl();
        if (UrlPolicy.isAppUrl(url)) webView.loadUrl(url);
        else webView.loadUrl(UrlPolicy.HOME);
    }

    private void hideError() {
        if (errorView != null) {
            page.removeView(errorView);
            errorView = null;
        }
    }

    private void updateNavigation() {
        backButton.setAlpha(webView.canGoBack() ? 1f : 0.35f);
        forwardButton.setAlpha(webView.canGoForward() ? 1f : 0.35f);
    }

    private void navigateBack() {
        if (webView.canGoBack()) webView.goBack();
        else finish();
    }

    @Override public void onBackPressed() {
        navigateBack();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null && intent.getData() != null) {
            String url = intent.getData().toString();
            if (UrlPolicy.isAppUrl(url)) webView.loadUrl(url);
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_REQUEST && fileCallback != null) {
            fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
            fileCallback = null;
        }
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        webView.saveState(state);
        super.onSaveInstanceState(state);
    }

    @Override protected void onDestroy() {
        if (fileCallback != null) fileCallback.onReceiveValue(null);
        page.removeView(webView);
        webView.destroy();
        super.onDestroy();
    }
}
