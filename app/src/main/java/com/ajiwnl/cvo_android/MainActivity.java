package com.ajiwnl.cvo_android;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.webkit.MimeTypeMap;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.provider.MediaStore;
import android.webkit.WebResourceRequest;
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
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.HttpURLConnection;


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
        mywebView.setWebViewClient(new WebViewClient());
        mywebView.setWebChromeClient(new MyWebChromeClient());
        mywebView.loadUrl("https://cvoapp.vercel.app/");

        mywebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                swipeRefreshLayout.setRefreshing(false);
            }
        });

        WebSettings webSettings = mywebView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setDomStorageEnabled(true);

        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                mywebView.reload();
            }
        });

        // Register file chooser launcher
        fileChooserLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<ActivityResult>() {
                    @Override
                    public void onActivityResult(ActivityResult result) {
                        if (filePathCallback != null) {
                            Uri[] results = null;
                            if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                                results = new Uri[]{result.getData().getData()};
                            }
                            filePathCallback.onReceiveValue(results);
                            filePathCallback = null;
                        }
                    }
                }
        );

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

        // Handle file download

        public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimeType, long contentLength) {
            // Check for permission
            if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(MainActivity.this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
                return;
            }

            // Get file name from the URL
            String fileName = URLUtil.guessFileName(url, contentDisposition, mimeType);
            String fileExtension = MimeTypeMap.getFileExtensionFromUrl(url);
            String filePath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + fileName;

            // Start file download in a background thread
            new Thread(() -> {
                try {
                    // Open URL connection and get file input stream
                    URL fileUrl = new URL(url);
                    HttpURLConnection connection = (HttpURLConnection) fileUrl.openConnection();
                    connection.connect();
                    InputStream inputStream = connection.getInputStream();

                    // Open output stream to save file
                    OutputStream outputStream = new FileOutputStream(new File(filePath));

                    byte[] buffer = new byte[1024];
                    int length;
                    while ((length = inputStream.read(buffer)) > 0) {
                        outputStream.write(buffer, 0, length);
                    }

                    outputStream.close();
                    inputStream.close();

                    // Notify user that file has been downloaded
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, "File downloaded to " + filePath, Toast.LENGTH_SHORT).show();
                    });

                } catch (IOException e) {
                    e.printStackTrace();
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, "Download failed!", Toast.LENGTH_SHORT).show();
                    });
                }
            }).start();
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
