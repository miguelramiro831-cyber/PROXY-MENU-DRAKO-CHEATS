package com.drako.painel;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.webkit.WebView;
import android.widget.TextView;
import org.json.JSONObject;
import java.io.*;
import java.net.*;

public class DrakoService extends Service {

    public static boolean painelOn = false;
    private static DrakoService inst;
    private WindowManager wm; private WebView web; private TextView bolha;
    private WindowManager.LayoutParams lp, lb;
    private final Handler h = new Handler(Looper.getMainLooper());
    private long ultimo = 0;

    public static DrakoService instancia(){ return inst; }
    public static void iniciar(Context c){
        Intent i = new Intent(c, DrakoService.class); i.setAction("watch");
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
    }
    public static void mover(int dx,int dy){ if (inst != null) inst.moverPainel(dx,dy); }
    public static void avisar(){ if (inst != null) inst.aplicarVisibilidade(); }
    private int tipo(){
        return Build.VERSION.SDK_INT >= 26
            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            : WindowManager.LayoutParams.TYPE_PHONE;
    }
    @Override public void onCreate(){
        super.onCreate(); inst = this;
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (Build.VERSION.SDK_INT >= 26)
                nm.createNotificationChannel(new NotificationChannel("drako_srv","DRAKO",NotificationManager.IMPORTANCE_MIN));
            Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this,"drako_srv") : new Notification.Builder(this);
            b.setSmallIcon(android.R.drawable.ic_menu_compass)
             .setContentTitle("PROXY MENU DRAKO").setContentText("Key ativa");
            startForeground(1, b.build());
        } catch (Exception ignored){}
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        criarBolha();
        h.postDelayed(vigia, 1000);
    }
    @Override public int onStartCommand(Intent it,int f,int s){
        if (it != null && "painel_on".equals(it.getAction())){ painelOn = true; criarPainel(); }
        return START_STICKY;
    }
    private void criarBolha(){
        if (wm == null) return;
        bolha = new TextView(this);
        bolha.setText("D"); bolha.setTextColor(0xFFFFFFFF); bolha.setTextSize(16);
        bolha.setGravity(Gravity.CENTER); bolha.setBackgroundColor(0xFFDC2626);
        lb = new WindowManager.LayoutParams(64,64,tipo(),WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT);
        lb.gravity = Gravity.TOP|Gravity.START; lb.x = 20; lb.y = 320;
        bolha.setOnTouchListener(new View.OnTouchListener(){
            float x0,y0; int px,py; boolean drag;
            public boolean onTouch(View v, MotionEvent e){
                switch(e.getAction()){
                    case MotionEvent.ACTION_DOWN: x0=e.getRawX(); y0=e.getRawY(); px=lb.x; py=lb.y; drag=false; return true;
                    case MotionEvent.ACTION_MOVE:
                        if (Math.abs(e.getRawX()-x0)>12 || Math.abs(e.getRawY()-y0)>12) drag=true;
                        if (drag){
                            lb.x = px + (int)(e.getRawX()-x0);
                            lb.y = py + (int)(e.getRawY()-y0);
                            try { wm.updateViewLayout(bolha,lb); } catch (Exception ignored){}
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!drag){
                            painelOn = !painelOn;
                            if (painelOn && web == null) criarPainel();
                            aplicarVisibilidade();
                        }
                        return true;
                }
                return false;
            }
        });
        try { wm.addView(bolha, lb); } catch (Exception ignored){}
    }
    private void criarPainel(){
        if (web != null || wm == null) return;
        web = new WebView(this);
        web.getSettings().setJavaScriptEnabled(true);
        web.setBackgroundColor(0x00000000);
        web.addJavascriptInterface(new DrakoBridge(this, web), "DrakoNative");
        web.loadUrl("file:///android_asset/overlay.html");
        lp = new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT, tipo(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP|Gravity.START; lp.x = 30; lp.y = 200;
        try { wm.addView(web, lp); } catch (Exception e){ web = null; return; }
        painelOn = true;
        web.postDelayed(() -> {
            try {
                SharedPreferences p = getSharedPreferences("drako",MODE_PRIVATE);
                JSONObject o = new JSONObject();
                for (String k : new String[]{"hsCabeca","espLinha","espBox","espVida","aimbot","exibirFov"})
                    o.put(k, p.getBoolean(k, !k.equals("aimbot")));
                o.put("fov", p.getInt("fov",90));
                DrakoBridge.jsEm(web, "DRAKO.onEstado("+o+")");
            } catch (Exception ignored){}
        }, 500);
    }
    private void moverPainel(int dx,int dy){
        if (lp == null || web == null || wm == null) return;
        lp.x += dx; lp.y += dy;
        try { wm.updateViewLayout(web, lp); } catch (Exception ignored){}
    }
    private void aplicarVisibilidade(){ if (web != null) web.setVisibility(painelOn ? View.VISIBLE : View.GONE); }

    private final Runnable vigia = new Runnable(){
        public void run(){
            try {
                SharedPreferences p = getSharedPreferences("drako",MODE_PRIVATE);
                long exp = p.getLong("keyExpira",0);
                if (exp > 0 && exp <= System.currentTimeMillis()){
                    p.edit().remove("keyExpira").apply();
                    new DrakoBridge.Sh(DrakoService.this).exec("rm -rf \""+DrakoBridge.DIRS[0]+"\"");
                    DrakoBridge.js("DRAKO.onExpirado('KEY EXPIRADA')");
                    ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(2,
                        new Notification.Builder(DrakoService.this)
                            .setSmallIcon(android.R.drawable.ic_dialog_alert)
                            .setContentTitle("DRAKO")
                            .setContentText("Key expirada — arquivos removidos do Free Fire").build());
                } else if (System.currentTimeMillis() - ultimo > 60000){
                    ultimo = System.currentTimeMillis(); conferir();
                }
            } catch (Exception ignored){}
            h.postDelayed(this, 1000);
        }
    };

    private void conferir(){
        final String key = getSharedPreferences("drako",MODE_PRIVATE).getString("keyNome","");
        if (key.isEmpty()) return;
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject(); body.put("key", key);
                String tok = new JSONObject(getSharedPreferences("drako",MODE_PRIVATE).getString("tokens","{}")).optString(key,"");
                if (!tok.isEmpty()) body.put("installationToken", tok);
                HttpURLConnection c = (HttpURLConnection) new URL(DrakoBridge.API).openConnection();
                c.setRequestMethod("POST"); c.setDoOutput(true);
                c.setConnectTimeout(8000); c.setReadTimeout(8000);
                c.setRequestProperty("Content-Type","application/json");
                OutputStream os = c.getOutputStream();
                os.write(body.toString().getBytes("UTF-8")); os.close();
                InputStream is = c.getInputStream(); if (is == null) return;
                BufferedReader br = new BufferedReader(new InputStreamReader(is));
                StringBuilder sb = new StringBuilder(); String l;
                while ((l=br.readLine())!=null) sb.append(l);
                br.close();
                JSONObject d = new JSONObject(sb.toString());
                if (!d.optBoolean("allowed",false)) DrakoBridge.js("DRAKO.onExpirado('KEY REVOGADA OU EXPIRADA')");
                else if (d.has("expiresAt"))
                    getSharedPreferences("drako",MODE_PRIVATE).edit().putLong("keyExpira", d.optLong("expiresAt",0)).apply();
            } catch (Exception ignored){}
        }).start();
    }
    @Override public void onDestroy(){
        try {
            if (wm != null){
                if (web != null) wm.removeView(web);
                if (bolha != null) wm.removeView(bolha);
            }
        } catch (Exception ignored){}
        web = null; bolha = null; inst = null;
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent i){ return null; }
}