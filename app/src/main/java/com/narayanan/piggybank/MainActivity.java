package com.narayanan.piggybank;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.OutputStream;

public class MainActivity extends Activity {

    private static final int FILE_REQUEST = 41;
    private WebView web;
    private ValueCallback<Uri[]> fileCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);      // keeps the kid's data (localStorage)
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setTextZoom(100);

        web.addJavascriptInterface(new Bridge(), "Android");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return openOutside(request.getUrl());
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                Intent pick = new Intent(Intent.ACTION_GET_CONTENT);
                pick.addCategory(Intent.CATEGORY_OPENABLE);
                pick.setType("*/*");
                try {
                    startActivityForResult(Intent.createChooser(pick, "Choose backup file"), FILE_REQUEST);
                } catch (Exception e) {
                    fileCallback = null;
                    return false;
                }
                return true;
            }
        });

        if (savedInstanceState != null) {
            web.restoreState(savedInstanceState);
        } else {
            web.loadUrl("file:///android_asset/index.html");
        }
        setContentView(web);
    }

    /** mailto: and web links open in the phone's own apps; the dashboard stays inside. */
    private boolean openOutside(Uri uri) {
        if ("file".equals(uri.getScheme())) return false;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (Exception e) {
            toast("No app found to open this");
        }
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_REQUEST && fileCallback != null) {
            Uri[] result = null;
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                result = new Uri[]{data.getData()};
            }
            fileCallback.onReceiveValue(result);
            fileCallback = null;
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        web.saveState(outState);
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show();
    }

    /** Saves a file into Downloads/PiggyBank and returns its content Uri. */
    private Uri saveToDownloads(String name, String mime, byte[] bytes) throws Exception {
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        values.put(MediaStore.MediaColumns.MIME_TYPE, mime);
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/PiggyBank");
        Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) throw new Exception("could not create file");
        try (OutputStream out = getContentResolver().openOutputStream(uri)) {
            if (out == null) throw new Exception("could not open file");
            out.write(bytes);
        }
        return uri;
    }

    /** Called from the dashboard JavaScript as window.Android.* */
    private class Bridge {

        @JavascriptInterface
        public String saveFile(String name, String mime, String base64) {
            try {
                Uri uri = saveToDownloads(name, mime, Base64.decode(base64, Base64.DEFAULT));
                runOnUiThread(() -> toast("Saved to Downloads/PiggyBank: " + name));
                return uri.toString();
            } catch (Exception e) {
                runOnUiThread(() -> toast("Couldn't save the file: " + e.getMessage()));
                return "";
            }
        }

        @JavascriptInterface
        public void shareFile(String name, String mime, String base64, String text) {
            try {
                Uri uri = saveToDownloads(name, mime, Base64.decode(base64, Base64.DEFAULT));
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType(mime);
                send.putExtra(Intent.EXTRA_STREAM, uri);
                send.putExtra(Intent.EXTRA_SUBJECT, "Piggy bank backup");
                send.putExtra(Intent.EXTRA_TEXT, text);
                send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                runOnUiThread(() -> startActivity(Intent.createChooser(send, "Send backup")));
            } catch (Exception e) {
                runOnUiThread(() -> toast("Couldn't share the backup: " + e.getMessage()));
            }
        }

        @JavascriptInterface
        public void print() {
            runOnUiThread(() -> {
                PrintManager pm = (PrintManager) getSystemService(PRINT_SERVICE);
                PrintDocumentAdapter adapter = web.createPrintDocumentAdapter("Piggy Bank");
                pm.print("Piggy Bank", adapter, new PrintAttributes.Builder().build());
            });
        }
    }
}
