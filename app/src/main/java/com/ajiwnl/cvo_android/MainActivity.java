package com.ajiwnl.cvo_android;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.webkit.MimeTypeMap;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.DownloadListener;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import android.content.Context;
import android.os.Environment;
import android.webkit.URLUtil;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class MainActivity extends AppCompatActivity {

    private WebView mywebView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private ValueCallback<Uri[]> filePathCallback;
    private ActivityResultLauncher<Intent> fileChooserLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        mywebView = findViewById(R.id.webview1);
        mywebView.setWebViewClient(new MyWebViewClient());
        mywebView.setWebChromeClient(new MyWebChromeClient());
        mywebView.loadUrl("https://cvoapp.vercel.app/");

        WebSettings webSettings = mywebView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setDomStorageEnabled(true);

        swipeRefreshLayout.setOnRefreshListener(() -> mywebView.reload());

        // Handle file selection
        fileChooserLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (filePathCallback != null) {
                        Uri[] results = null;
                        if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                            results = new Uri[]{result.getData().getData()};
                        }
                        filePathCallback.onReceiveValue(results);
                        filePathCallback = null;
                    }
                }
        );

        // Handle file downloads (including blob URLs)
        mywebView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            if (url.startsWith("blob:")) {
                mywebView.evaluateJavascript(
                        "(async function() {" +
                                "    let blob = await fetch('" + url + "').then(r => r.blob());" +
                                "    let reader = new FileReader();" +
                                "    reader.readAsDataURL(blob);" +
                                "    reader.onloadend = function() {" +
                                "        window.android.downloadBlob(reader.result, 'cvo_certificate_1.pdf');" +
                                "    };" +
                                "})();",
                        null
                );
            } else {
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                request.setMimeType(mimeType);
                request.addRequestHeader("User-Agent", userAgent);
                request.setDescription("Downloading file...");
                request.setTitle("CVO Pet Travel Certificate.pdf");
                request.allowScanningByMediaScanner();
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                String fileName = "cvo_certificate_" + System.currentTimeMillis() + ".pdf";
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);


                DownloadManager downloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                if (downloadManager != null) {
                    downloadManager.enqueue(request);
                }
                Toast.makeText(getApplicationContext(), "Downloading File...", Toast.LENGTH_LONG).show();
            }
        });

        // Add JavaScript interface for handling blob downloads
        mywebView.addJavascriptInterface(new Object() {
            @android.webkit.JavascriptInterface
            public void downloadBlob(String base64Data, String filename) {
                byte[] fileAsBytes = android.util.Base64.decode(base64Data.split(",")[1], android.util.Base64.DEFAULT);
                try {
                    String fileName = "cvo_certificate_" + System.currentTimeMillis() + ".pdf";
                    File filePath = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName);
                    FileOutputStream os = new FileOutputStream(filePath);
                    os.write(fileAsBytes);
                    os.flush();
                    os.close();
                    Toast.makeText(MainActivity.this, "Download completed: " + filename, Toast.LENGTH_SHORT).show();
                } catch (IOException e) {
                    e.printStackTrace();
                    Toast.makeText(MainActivity.this, "Download failed", Toast.LENGTH_SHORT).show();
                }
            }
        }, "android");

        // Request storage permission if needed
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) { // Android 12 and below
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 2);
            }
        }
    }

    private class MyWebViewClient extends WebViewClient {
        @Override
        public void onPageFinished(WebView view, String url) {
            swipeRefreshLayout.setRefreshing(false);
        }
    }

    private class MyWebChromeClient extends WebChromeClient {
        @Override
        public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback,
                                         FileChooserParams fileChooserParams) {
            if (MainActivity.this.filePathCallback != null) {
                MainActivity.this.filePathCallback.onReceiveValue(null);
            }
            MainActivity.this.filePathCallback = filePathCallback;

            Intent intent = new Intent(Intent.ACTION_PICK);
            intent.setType("image/*");
            fileChooserLauncher.launch(intent);
            return true;
        }
    }

    @Override
    public void onBackPressed() {
        if (mywebView.canGoBack()) {
            mywebView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
