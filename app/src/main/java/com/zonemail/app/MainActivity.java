package com.zonemail.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.MimeTypeMap;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {

    private static final String HOME_URL = "https://cloud-mail.liziran254.workers.dev/";
    private static final String HOME_HOST = "cloud-mail.liziran254.workers.dev";
    private static final int REQ_FILE_CHOOSER = 1001;
    private static final int REQ_STORAGE = 1002;

    private WebView webView;
    private ProgressBar progressBar;
    private ValueCallback<Uri[]> filePathCallback;
    private PendingDownload pendingDownload;

    private static class PendingDownload {
        String url, userAgent, contentDisposition, mimeType;
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.parseColor("#0F172A"));
        webView = new WebView(this);
        webView.setBackgroundColor(Color.parseColor("#0F172A"));
        root.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        FrameLayout.LayoutParams pbParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(3));
        root.addView(progressBar, pbParams);
        setContentView(root);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(false);
        s.setMediaPlaybackRequiresUserGesture(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setSupportMultipleWindows(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setUserAgentString(s.getUserAgentString() + " ZoneMailApp/1.0");

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(webView, true);

        webView.addJavascriptInterface(new BlobBridge(), "ZoneMailBridge");
        webView.setWebViewClient(new ZoneClient());
        webView.setWebChromeClient(new ZoneChrome());
        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) ->
                handleDownload(url, userAgent, contentDisposition, mimeType));

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
        } else {
            Uri data = getIntent() != null ? getIntent().getData() : null;
            webView.loadUrl(data != null && isInternal(data) ? data.toString() : HOME_URL);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Uri data = intent.getData();
        if (data != null && isInternal(data)) webView.loadUrl(data.toString());
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static boolean isInternal(Uri uri) {
        if (uri == null) return false;
        String scheme = uri.getScheme();
        String host = uri.getHost();
        return ("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))
                && host != null && host.equalsIgnoreCase(HOME_HOST);
    }

    private boolean handleUrl(Uri uri) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        if (isInternal(uri)) return false; // stay in app
        if (scheme.equals("blob") || scheme.equals("data") || scheme.equals("about")
                || scheme.equals("javascript")) return false;
        openExternal(uri);
        return true;
    }

    private void openExternal(Uri uri) {
        try {
            Intent i;
            if ("intent".equalsIgnoreCase(uri.getScheme())) {
                i = Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME);
                i.addCategory(Intent.CATEGORY_BROWSABLE);
                i.setComponent(null);
                i.setSelector(null);
            } else {
                i = new Intent(Intent.ACTION_VIEW, uri);
            }
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "没有可以打开此链接的应用", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "无法打开链接", Toast.LENGTH_SHORT).show();
        }
    }

    private class ZoneClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            return handleUrl(request.getUrl());
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            progressBar.setVisibility(View.VISIBLE);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            progressBar.setVisibility(View.GONE);
            CookieManager.getInstance().flush();
        }
    }

    private class ZoneChrome extends WebChromeClient {
        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            progressBar.setProgress(newProgress);
            progressBar.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
        }

        @Override
        public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                         FileChooserParams params) {
            if (filePathCallback != null) filePathCallback.onReceiveValue(null);
            filePathCallback = callback;
            Intent intent;
            try {
                intent = params.createIntent();
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                if (params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE) {
                    intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                }
                String[] types = params.getAcceptTypes();
                if (types == null || types.length == 0 || (types.length == 1 && types[0].isEmpty())) {
                    intent.setType("*/*");
                }
                startActivityForResult(Intent.createChooser(intent, "选择文件"), REQ_FILE_CHOOSER);
            } catch (Exception e) {
                filePathCallback = null;
                Toast.makeText(MainActivity.this, "无法打开文件选择器", Toast.LENGTH_SHORT).show();
                return false;
            }
            return true;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_FILE_CHOOSER && filePathCallback != null) {
            Uri[] results = null;
            if (resultCode == RESULT_OK && data != null) {
                if (data.getClipData() != null) {
                    int n = data.getClipData().getItemCount();
                    results = new Uri[n];
                    for (int i = 0; i < n; i++) results[i] = data.getClipData().getItemAt(i).getUri();
                } else if (data.getData() != null) {
                    results = new Uri[]{data.getData()};
                }
            }
            filePathCallback.onReceiveValue(results);
            filePathCallback = null;
        }
    }

    // ---------- Downloads ----------

    private void handleDownload(String url, String userAgent, String contentDisposition, String mimeType) {
        if (url.startsWith("blob:")) {
            String fileName = URLUtil.guessFileName(url, contentDisposition, mimeType);
            String js = "(function(){var x=new XMLHttpRequest();x.open('GET','" + url + "',true);"
                    + "x.responseType='blob';x.onload=function(){var r=new FileReader();"
                    + "r.onloadend=function(){ZoneMailBridge.saveBase64(r.result,'" + fileName.replace("'", "") + "','"
                    + (mimeType == null ? "" : mimeType) + "');};r.readAsDataURL(x.response);};x.send();})();";
            webView.evaluateJavascript(js, null);
            return;
        }
        if (url.startsWith("data:")) {
            saveDataUrl(url, URLUtil.guessFileName(url, contentDisposition, mimeType), mimeType);
            return;
        }
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            pendingDownload = new PendingDownload();
            pendingDownload.url = url;
            pendingDownload.userAgent = userAgent;
            pendingDownload.contentDisposition = contentDisposition;
            pendingDownload.mimeType = mimeType;
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_STORAGE);
            return;
        }
        enqueueDownload(url, userAgent, contentDisposition, mimeType);
    }

    private void enqueueDownload(String url, String userAgent, String contentDisposition, String mimeType) {
        try {
            String fileName = URLUtil.guessFileName(url, contentDisposition, mimeType);
            DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
            String cookies = CookieManager.getInstance().getCookie(url);
            if (cookies != null) req.addRequestHeader("Cookie", cookies);
            if (userAgent != null) req.addRequestHeader("User-Agent", userAgent);
            if (mimeType != null && !mimeType.isEmpty()) req.setMimeType(mimeType);
            req.setTitle(fileName);
            req.setDescription("Zone Mail 附件下载");
            req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
            DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            dm.enqueue(req);
            Toast.makeText(this, "开始下载：" + fileName, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "下载失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_STORAGE && pendingDownload != null) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                enqueueDownload(pendingDownload.url, pendingDownload.userAgent,
                        pendingDownload.contentDisposition, pendingDownload.mimeType);
            } else {
                Toast.makeText(this, "需要存储权限才能下载附件", Toast.LENGTH_SHORT).show();
            }
            pendingDownload = null;
        }
    }

    private void saveDataUrl(String dataUrl, String fileName, String mimeType) {
        try {
            int comma = dataUrl.indexOf(',');
            String meta = dataUrl.substring(5, comma);
            String mime = meta.split(";")[0];
            if (mime.isEmpty()) mime = mimeType;
            byte[] bytes = meta.contains("base64")
                    ? Base64.decode(dataUrl.substring(comma + 1), Base64.DEFAULT)
                    : Uri.decode(dataUrl.substring(comma + 1)).getBytes("UTF-8");
            if (mime == null || mime.isEmpty()) {
                String ext = MimeTypeMap.getFileExtensionFromUrl(fileName);
                mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
                if (mime == null) mime = "application/octet-stream";
            }
            writeToDownloads(bytes, fileName, mime);
            Toast.makeText(this, "已保存到下载：" + fileName, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "保存失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void writeToDownloads(byte[] bytes, String fileName, String mime) throws Exception {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues v = new ContentValues();
            v.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
            v.put(MediaStore.Downloads.MIME_TYPE, mime);
            v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (uri == null) throw new Exception("无法创建文件");
            try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                os.write(bytes);
            }
        } else {
            File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (!dir.exists()) dir.mkdirs();
            try (FileOutputStream fos = new FileOutputStream(new File(dir, fileName))) {
                fos.write(bytes);
            }
        }
    }

    private class BlobBridge {
        @JavascriptInterface
        public void saveBase64(String dataUrl, String fileName, String mimeType) {
            runOnUiThread(() -> saveDataUrl(dataUrl, fileName, mimeType));
        }
    }

    // ---------- Lifecycle ----------

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        webView.saveState(outState);
    }

    @Override
    protected void onPause() {
        super.onPause();
        CookieManager.getInstance().flush();
        webView.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            CookieManager.getInstance().flush();
            webView.destroy();
        }
        super.onDestroy();
    }
}
