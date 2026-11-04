package com.drako.painel;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {
    private WebView web;
    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        web = new WebView(this);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.setWebViewClient(new WebViewClient());
        web.addJavascriptInterface(new DrakoBridge(this, web), "DrakoNative");
        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");
    }
    @Override public void onBackPressed(){
        if (DrakoService.instancia() != null){
            DrakoService.painelOn = !DrakoService.painelOn;
            DrakoService.avisar();
        } else super.onBackPressed();
    }
    @Override protected void onDestroy(){
        if (web != null){ web.destroy(); web = null; }
        super.onDestroy();
    }
}