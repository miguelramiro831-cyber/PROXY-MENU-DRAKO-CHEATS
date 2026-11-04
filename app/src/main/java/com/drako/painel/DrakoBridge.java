package com.drako.painel;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.util.*;

public class DrakoBridge {

    public static final String API = "https://keyvault-5qbp3hgj.manus.space/api/venice/v1/validate";
    public static final String FF = "com.dts.freefireth";
    public static final String FFMAX = "com.dts.freefiremax";
    public static final String[] DIRS = {
        "/sdcard/Android/data/com.dts.freefireth/files/DRAKO/",
        "/sdcard/Android/data/com.dts.freefireth/files/",
        "/sdcard/Documents/DRAKO/"
    };
    private static final String[] OPS = {"hsCabeca","espLinha","espBox","espVida","aimbot","exibirFov"};
    private static final String[] CONFIGS = {"localConfig.json","config.json","drako.json","menu_config.txt","drako.ini"};

    /* ---------- shell (Shizuku -> ADB -> arquivos) ---------- */
    static class Sh {
        static final String ARQ="arquivos", ADB="adb", SHZ="shizuku";
        private final Context ctx; private File adbBin; private String alvo="";
        Sh(Context c){ ctx=c; }
        String detectar(){
            if (shizuku() && permShizuku()) return SHZ;
            if (adbConectado()) return ADB;
            return ARQ;
        }
        boolean shizuku(){
            try { return (Boolean) Class.forName("moe.shizuku.api.Shizuku").getMethod("pingBinder").invoke(null); }
            catch (Throwable t){ return false; }
        }
        boolean permShizuku(){
            try { return (Boolean) Class.forName("moe.shizuku.api.Shizuku").getMethod("checkSelfPermission").invoke(null); }
            catch (Throwable t){ return false; }
        }
        void pedirShizuku(){
            try { Class.forName("moe.shizuku.api.Shizuku").getMethod("requestPermission", int.class).invoke(null, 4001); }
            catch (Throwable t){}
        }
        String exec(String cmd){
            if (shizuku() && permShizuku()){
                try {
                    Process p = (Process) Class.forName("moe.shizuku.api.Shizuku")
                        .getMethod("newProcess", String[].class, String[].class, String.class)
                        .invoke(null, new String[]{"sh","-c",cmd}, null, null);
                    return ler(p);
                } catch (Throwable t){}
            }
            if (adbConectado()) return adb("shell", cmd);
            return "";
        }
        private String ler(Process p) throws Exception {
            StringBuilder sb = new StringBuilder();
            try {
                BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
                String l; while ((l=r.readLine())!=null) sb.append(l).append('\n');
                r.close();
            } catch (Exception ignored){}
            p.waitFor();
            return sb.toString();
        }
        File adbBin() throws Exception {
            if (adbBin != null && adbBin.exists()) return adbBin;
            for (String p : new String[]{"/system/bin/adb","/system/xbin/adb","/sbin/adb"}){
                File f = new File(p); if (f.exists()){ adbBin=f; return f; }
            }
            File out = new File(ctx.getFilesDir(),"adb");
            if (!out.exists() || out.length()<1000){
                File lib = new File(ctx.getApplicationInfo().nativeLibraryDir,"libadb.so");
                if (!lib.exists()) throw new Exception("sem adb no APK");
                copiar(lib,out); out.setExecutable(true,false);
                adb("chmod","755",out.getAbsolutePath());
            }
            adbBin = out; return out;
        }
        String adb(String... args){
            try {
                File a = adbBin();
                String[] cmd = new String[args.length+1];
                cmd[0]=a.getAbsolutePath();
                System.arraycopy(args,0,cmd,1,args.length);
                return ler(new ProcessBuilder(cmd).redirectErrorStream(true).start());
            } catch (Exception e){ return "ERRO: "+e.getMessage(); }
        }
        boolean adbConectado(){
            if (alvo.isEmpty()) return false;
            try { return adb("devices").contains(alvo); } catch (Exception e){ return false; }
        }
        String alvo(){ return alvo; }
        String parear(String ip,String porta,String codigo){
            StringBuilder s = new StringBuilder();
            if (codigo!=null && !codigo.isEmpty() && porta!=null && !porta.isEmpty())
                s.append(adb("pair", ip+":"+porta, codigo));
            s.append(adb("start-server"));
            s.append(adb("connect", ip+":5555"));
            String d = adb("devices");
            if (d.contains(ip+":5555") && d.contains("device")) alvo = ip+":5555";
            return s.append(d).toString();
        }
        static void copiar(File src, File dst) throws Exception {
            dst.getParentFile().mkdirs();
            InputStream in = new FileInputStream(src);
            OutputStream os = new FileOutputStream(dst);
            byte[] b = new byte[8192]; int n;
            while ((n=in.read(b))>0) os.write(b,0,n);
            in.close(); os.close();
        }
    }

    public static WebView principal;
    private final Context ctx; private final WebView web; private final Sh sh; private final SharedPreferences p;

    public DrakoBridge(Context c, WebView w){
        ctx=c; web=w; sh=new Sh(c);
        p = c.getSharedPreferences("drako", Context.MODE_PRIVATE);
        if (principal == null) principal = w;
    }
    public static void js(String c){
        final WebView w = principal; if (w == null) return;
        w.post(() -> { try { w.evaluateJavascript(c, null); } catch (Exception e){} });
    }
    public static void jsEm(WebView w,String c){
        if (w == null) return;
        w.post(() -> { try { w.evaluateJavascript(c, null); } catch (Exception e){} });
    }
    private void log(String s){ js("DRAKO.onLog("+JSONObject.quote(s)+")"); }
    private boolean notifOk(){ return Build.VERSION.SDK_INT < 33 || ctx.checkSelfPermission("android.permission.POST_NOTIFICATIONS")==0; }
    private boolean filesOk(){ return Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager(); }

    @JavascriptInterface public void pedirEstado(){
        try {
            JSONObject o = new JSONObject();
            for (String k : OPS) o.put(k, p.getBoolean(k, !k.equals("aimbot")));
            o.put("fov", p.getInt("fov",90));
            o.put("modo", sh.detectar());
            o.put("conectado", sh.shizuku() || sh.adbConectado());
            o.put("pOver", Settings.canDrawOverlays(ctx));
            o.put("pNotif", notifOk());
            o.put("pFiles", filesOk());
            js("DRAKO.onEstado("+o+")");
        } catch (Exception e){ log(e.getMessage()); }
    }
    @JavascriptInterface public void pedirPermissoes(){
        try {
            if (!Settings.canDrawOverlays(ctx)){
                Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:"+ctx.getPackageName()));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); ctx.startActivity(i);
            }
            if (Build.VERSION.SDK_INT >= 33 && !notifOk() && ctx instanceof Activity)
                ((Activity)ctx).requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},101);
            if (Build.VERSION.SDK_INT >= 30 && !filesOk()){
                Intent i = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); ctx.startActivity(i);
            }
            if (sh.shizuku() && !sh.permShizuku()) sh.pedirShizuku();
        } catch (Exception e){ log("perm: "+e.getMessage()); }
        new Handler(Looper.getMainLooper()).postDelayed(this::pedirEstado, 1500);
    }
    @JavascriptInterface public void conectar(final String ip, final String porta, final String codigo){
        new Thread(() -> {
            if (sh.shizuku()){
                if (!sh.permShizuku()){
                    sh.pedirShizuku();
                    js("DRAKO.onShell('Autorize o Shizuku e aperte CONECTAR de novo.','shizuku',false)");
                    return;
                }
                js("DRAKO.onShell('Shizuku autorizado. Shell ativo.','shizuku',true)");
                return;
            }
            if (ip == null || ip.isEmpty()){
                js("DRAKO.onShell('Shizuku nao encontrado. Ative a depuracao por wi-fi e preencha IP, porta e codigo.','offline',false)");
                return;
            }
            js("DRAKO.onShell('Pareando com "+ip+"...','adb',false)");
            String out = sh.parear(ip, porta, codigo);
            boolean ok = sh.adbConectado();
            js("DRAKO.onShell("+JSONObject.quote(ok ? "Conectado por ADB. Aperte INJETAR ARQUIVOS."
                : "Falha ao conectar. Confira a depuracao. "+out.replace("\n"," | "))
                +", '"+(ok?"adb":"offline")+"', "+ok+")");
        }).start();
    }
    @JavascriptInterface public void notificar(String t, String m){
        try {
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (Build.VERSION.SDK_INT >= 26)
                nm.createNotificationChannel(new NotificationChannel("drako","DRAKO",NotificationManager.IMPORTANCE_LOW));
            Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(ctx,"drako") : new Notification.Builder(ctx);
            b.setSmallIcon(android.R.drawable.stat_sys_download_done)
             .setContentTitle(t).setContentText(m).setAutoCancel(true);
            nm.notify((int)(System.currentTimeMillis()%10000), b.build());
        } catch (Exception e){ log("notif: "+e.getMessage()); }
    }
    @JavascriptInterface public void setOpt(String k, boolean v){
        p.edit().putBoolean(k,v).apply(); escreverConfig(); log("opcao "+k+" = "+v);
    }
    @JavascriptInterface public void setFov(int v){
        p.edit().putInt("fov", Math.max(50, Math.min(360, v))).apply(); escreverConfig();
    }
    private void escreverConfig(){
        new Thread(() -> {
            try {
                boolean box=p.getBoolean("espBox",true), linha=p.getBoolean("espLinha",true),
                        vida=p.getBoolean("espVida",true), nome=p.getBoolean("vov",true),
                        hs=p.getBoolean("hsCabeca",true), aim=p.getBoolean("aimbot",false),
                        fv=p.getBoolean("exibirFov",true);
                int fov=p.getInt("fov",90);
                JSONObject flat = new JSONObject();
                flat.put("ESPLine",linha); flat.put("ESPBox",box); flat.put("ESPHealth",vida);
                flat.put("ESPName",nome); flat.put("HSCabeca",hs); flat.put("Aimbot",aim);
                flat.put("ExibirFov",fv); flat.put("ShowFOV",fv); flat.put("FOV",fov);
                JSONObject aninhado = new JSONObject();
                aninhado.put("esp", new JSONObject().put("line",linha).put("box",box).put("health",vida).put("name",nome));
                aninhado.put("aim", new JSONObject().put("aimbot",aim));
                aninhado.put("hs", new JSONObject().put("cabeca",hs));
                aninhado.put("fov", new JSONObject().put("show",fv).put("size",fov));
                StringBuilder txt = new StringBuilder();
                StringBuilder ini = new StringBuilder("[DRAKO]\n");
                for (Iterator<String> it = flat.keys(); it.hasNext();){
                    String k = it.next(); Object o = flat.get(k);
                    String v = (o instanceof Boolean) ? (((Boolean)o) ? "1" : "0") : String.valueOf(o);
                    txt.append(k).append('=').append(v).append('\n');
                    ini.append(k).append('=').append(v).append('\n');
                }
                String[][] arqs = {
                    {"localConfig.json", flat.toString()}, {"config.json", aninhado.toString()},
                    {"drako.json", flat.toString()}, {"menu_config.txt", txt.toString()},
                    {"drako.ini", ini.toString()}
                };
                for (String[] a : arqs){
                    File local = new File(ctx.getFilesDir(), a[0]);
                    FileOutputStream fo = new FileOutputStream(local);
                    fo.write(a[1].getBytes()); fo.close();
                    for (String d : DIRS){
                        File pasta = new File(d); pasta.mkdirs();
                        sh.exec("mkdir -p \""+d+"\"");
                        String r = sh.exec("cp -f \""+local.getAbsolutePath()+"\" \""+d+a[0]+"\"");
                        if (r.startsWith("ERRO") || sh.detectar().equals(Sh.ARQ)){
                            try { Sh.copiar(local, new File(pasta, a[0])); } catch (Exception ignored){}
                        }
                        sh.exec("chmod 666 \""+d+a[0]+"\" 2>/dev/null");
                    }
                }
            } catch (Exception e){ log("config: "+e.getMessage()); }
        }).start();
    }
    private void backup(File alvo, String rel){
        try {
            if (!alvo.exists()) return;
            File b = new File(ctx.getFilesDir(), "bkp/"+rel);
            if (b.exists()) return;
            Sh.copiar(alvo, b);
        } catch (Exception ignored){}
    }
    private void restaurar(){ restaurarRec(new File(ctx.getFilesDir(),"bkp"), ""); }
    private void restaurarRec(File dir, String rel){
        File[] ls = dir.listFiles(); if (ls == null) return;
        for (File f : ls){
            String r = rel.isEmpty() ? f.getName() : rel+"/"+f.getName();
            if (f.isDirectory()){ restaurarRec(f, r); continue; }
            for (String d : DIRS){
                String dest = d + r;
                sh.exec("cp -f \""+f.getAbsolutePath()+"\" \""+dest+"\"");
                try { Sh.copiar(f, new File(dest)); } catch (Exception ignored){}
                sh.exec("chmod 666 \""+dest+"\" 2>/dev/null");
            }
        }
    }
    @JavascriptInterface public void injetar(){
        new Thread(() -> {
            try {
                List<String> arqs = new ArrayList<>();
                varrer("xits", "", arqs);
                sh.exec("am force-stop "+FF);
                List<String> reais = new ArrayList<>();
                for (String par : arqs) if (!par.endsWith("|inject")) reais.add(par);
                int total = Math.max(1, reais.size()), i = 0, ok = 0;
                for (String par : reais){
                    String asset = par.substring(0, par.indexOf('|'));
                    String rel = par.substring(par.indexOf('|')+1);
                    File tmp = new File(ctx.getCacheDir(), new File(rel).getName());
                    extrair(asset, tmp); i++;
                    boolean copiou = false;
                    for (String d : DIRS){
                        File alvo = new File(d+rel);
                        backup(alvo, rel);
                        sh.exec("mkdir -p \""+alvo.getParent()+"\"");
                        String r = sh.exec("cp -f \""+tmp.getAbsolutePath()+"\" \""+alvo.getAbsolutePath()+"\"");
                        if (!r.startsWith("ERRO")) copiou = true;
                        if (sh.detectar().equals(Sh.ARQ)){
                            try { Sh.copiar(tmp, alvo); copiou = true; } catch (Exception ignored){}
                        }
                        sh.exec("chmod 666 \""+alvo.getAbsolutePath()+"\" 2>/dev/null");
                    }
                    if (copiou) ok++;
                    js("DRAKO.onProgress("+(int)(i*100.0/total)+", 'Injetando "+rel+"')");
                }
                escreverConfig();
                js("DRAKO.onProgress(100, '"+ok+" arquivos injetados · backup feito')");
                notificar("DRAKO", ok+" arquivos injetados no Free Fire");
            } catch (Exception e){
                js("DRAKO.onProgress(0, "+JSONObject.quote("Erro: "+e.getMessage())+")");
            }
        }).start();
    }
    private void varrer(String dir, String rel, List<String> out) throws Exception {
        String[] ls = ctx.getAssets().list(dir);
        if (ls == null || ls.length == 0){ out.add(dir+"|"+rel); return; }
        for (String f : ls) varrer(dir+"/"+f, rel.isEmpty()? f : rel+"/"+f, out);
    }
    private void extrair(String a, File d) throws Exception {
        InputStream in = ctx.getAssets().open(a);
        OutputStream os = new FileOutputStream(d);
        byte[] b = new byte[8192]; int n;
        while ((n=in.read(b))>0) os.write(b,0,n);
        in.close(); os.close();
    }
    private boolean existeAsset(String a){
        try { String[] ls = ctx.getAssets().list(a); return ls != null && ls.length > 0; }
        catch (Exception e){ return false; }
    }
    private List<String> assetsDe(String dir){
        List<String> out = new ArrayList<>();
        try { String[] ls = ctx.getAssets().list(dir); if (ls != null) for (String f : ls) out.add(dir+"/"+f); }
        catch (Exception ignored){}
        return out;
    }
    @JavascriptInterface public void injetarNoProcesso(){
        new Thread(() -> {
            try {
                if (!existeAsset("xits/inject")){ log("o xit nao trouxe o binario 'inject'"); return; }
                if (!sh.shizuku() && !sh.adbConectado()){ log("precisa de Shizuku ou ADB"); return; }
                String pid = "";
                for (int i=0; i<60 && pid.isEmpty(); i++){
                    pid = sh.exec("pidof "+FF).trim();
                    if (pid.isEmpty()) Thread.sleep(1000);
                }
                if (pid.isEmpty()){ log("o Free Fire nao abriu"); return; }
                pid = pid.split("\\s+")[0];
                File inj = new File(ctx.getCacheDir(), "drako_inject");
                extrair("xits/inject", inj);
                sh.exec("cp -f \""+inj.getAbsolutePath()+"\" /data/local/tmp/drako_inject");
                sh.exec("chmod 755 /data/local/tmp/drako_inject");
                String lib = "";
                for (String a : assetsDe("xits")){
                    if (!a.endsWith(".so")) continue;
                    File so = new File(ctx.getCacheDir(), new File(a).getName());
                    extrair(a, so);
                    sh.exec("cp -f \""+so.getAbsolutePath()+"\" /data/local/tmp/"+new File(a).getName());
                    sh.exec("chmod 644 /data/local/tmp/"+new File(a).getName());
                    if (lib.isEmpty()) lib = "/data/local/tmp/"+new File(a).getName();
                }
                String r = sh.exec("/data/local/tmp/drako_inject "+pid+" "+lib);
                log("injetor rodado no pid "+pid);
                js("DRAKO.onDiag("+JSONObject.quote("INJETOR pid "+pid+":<br>"+r.replace("\n","<br>"))+")");
                notificar("DRAKO","Injetor rodado no Free Fire");
            } catch (Exception e){ log("injetor: "+e.getMessage()); }
        }).start();
    }
    @JavascriptInterface public void removerInjecao(){
        new Thread(() -> {
            restaurar();
            for (String c : CONFIGS) for (String d : DIRS) sh.exec("rm -f \""+d+c+"\"");
            sh.exec("rm -rf \""+DIRS[0]+"\"");
            log("arquivos removidos e originais restaurados");
        }).start();
    }
    @JavascriptInterface public void diagnostico(){
        new Thread(() -> {
            StringBuilder o = new StringBuilder();
            o.append("modo: ").append(sh.detectar()).append("<br>");
            o.append("shizuku: ").append(sh.shizuku() ? (sh.permShizuku()?"autorizado":"sem permissao") : "nao instalado").append("<br>");
            o.append("adb: ").append(sh.adbConectado() ? "conectado "+sh.alvo() : "off").append("<br>");
            String path = sh.exec("pm path "+FF).trim();
            o.append("ff instalado: ").append(path.isEmpty()?"NAO":path).append("<br>");
          