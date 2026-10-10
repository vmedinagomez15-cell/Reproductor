package com.victor.flacprueba;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.graphics.*;
import android.media.*;
import android.net.Uri;
import android.os.*;
import android.provider.*;
import android.util.*;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.nio.charset.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Offline local UI. The bridge is exposed only to the packaged document. */
public class MainActivity extends Activity {
    private static final int TREE=10, LETTER=11;
    private WebView web;
    private FrameLayout safeRoot;
    private final ExecutorService artworkWorker=Executors.newSingleThreadExecutor();
    private SharedPreferences prefs;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final ExecutorService scanWorker=Executors.newSingleThreadExecutor();
    private final ExecutorService mediaWorker=Executors.newSingleThreadExecutor();
    private final AtomicInteger scanGeneration=new AtomicInteger(), songGeneration=new AtomicInteger();
    private JSONArray library=new JSONArray();
    private JSONArray playQueue=null;
    private JSONObject selected;
    private Uri pendingLyric;
    private MediaPlayer player;
    private AudioManager audio;
    private AudioFocusRequest focus;
    private boolean ready, pageReady, destroyed, scanning, visible;
    private String scanStatus="Elige una carpeta para encontrar tu música.";
    private Lrc lyrics=Lrc.empty();
    private final BroadcastReceiver noisy=new BroadcastReceiver(){public void onReceive(Context c,Intent i){pause();}};
    private final Runnable tick=new Runnable(){public void run(){if(destroyed)return;sendPosition();ui.postDelayed(this,200);}};

    @Override public void onCreate(Bundle state){
        super.onCreate(state);prefs=getSharedPreferences("player",MODE_PRIVATE);
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=android.content.pm.PackageManager.PERMISSION_GRANTED){new AlertDialog.Builder(this).setTitle("Controles de reproducción").setMessage("Permite las notificaciones para mostrar los controles de música. La reproducción en segundo plano usa un servicio multimedia de Android.").setPositiveButton("Permitir acceso",(d,w)->requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},54)).setNegativeButton("Ahora no",null).show();}
        audio=(AudioManager)getSystemService(AUDIO_SERVICE);
        PlaybackService.control=direction->runOnUiThread(()->{if(direction==0){if(player!=null&&ready){if(player.isPlaying())pause();else start();}}else skipTrack(direction);});
        focus=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(attributes())
            .setOnAudioFocusChangeListener(n->{if(n<0)pause();},ui).build();
        if(Build.VERSION.SDK_INT>=33)registerReceiver(noisy,new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(noisy,new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
        web=new WebView(this);web.setBackgroundColor(Color.rgb(12,11,18));
        WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);
        settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);
        settings.setBlockNetworkLoads(true);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.addJavascriptInterface(new Bridge(),"Native");
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){return true;}
            @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest r){
                return new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0]));
            }
            @Override public void onPageFinished(WebView v,String url){
                if(pageReady)return;pageReady=true;loadLibrary();sendSettings();sendLibrary();sendPlaylists();sendScan();
                String last=prefs.getString("track","");
                if(PlaybackService.player!=null && last.equals(PlaybackService.trackUri)){
                    player=PlaybackService.player;ready=PlaybackService.prepared;
                    for(int i=0;i<library.length();i++){JSONObject t=library.optJSONObject(i);if(t!=null&&last.equals(t.optString("uri"))){try{selected=new JSONObject(t.toString());put(selected,"index",i);emit("onTrack",selected);}catch(Exception ignored){}break;}}
                    sendPosition();return;
                }
                for(int i=0;i<library.length();i++){JSONObject t=library.optJSONObject(i);if(t!=null&&last.equals(t.optString("uri"))){openTrack(i,false);break;}}
            }
        });
        safeRoot=new FrameLayout(this);
        safeRoot.setBackgroundColor(prefs.getBoolean("dark",true)?0xff0c0b12:0xfff1eef7);
        safeRoot.addView(web,new FrameLayout.LayoutParams(-1,-1));
        if(Build.VERSION.SDK_INT>=30)getWindow().setDecorFitsSystemWindows(false);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        safeRoot.setOnApplyWindowInsetsListener((v,insets)->{
            int left,top,right,bottom;
            if(Build.VERSION.SDK_INT>=30){
                Insets bars=insets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
                Insets gestures=insets.getInsets(WindowInsets.Type.mandatorySystemGestures());
                left=bars.left;top=bars.top;right=bars.right;bottom=Math.max(bars.bottom,gestures.bottom);
            }else{
                left=insets.getSystemWindowInsetLeft();top=insets.getSystemWindowInsetTop();right=insets.getSystemWindowInsetRight();bottom=insets.getSystemWindowInsetBottom();
                if(Build.VERSION.SDK_INT>=28&&insets.getDisplayCutout()!=null){DisplayCutout cut=insets.getDisplayCutout();left=Math.max(left,cut.getSafeInsetLeft());top=Math.max(top,cut.getSafeInsetTop());right=Math.max(right,cut.getSafeInsetRight());bottom=Math.max(bottom,cut.getSafeInsetBottom());}
                if(Build.VERSION.SDK_INT>=29)bottom=Math.max(bottom,insets.getMandatorySystemGestureInsets().bottom);
            }
            // Padding on the parent changes the real WebView viewport, unlike WebView.setPadding.
            if(v.getPaddingLeft()!=left||v.getPaddingTop()!=top||v.getPaddingRight()!=right||v.getPaddingBottom()!=bottom)v.setPadding(left,top,right,bottom);
            return Build.VERSION.SDK_INT>=30?WindowInsets.CONSUMED:insets.consumeSystemWindowInsets();
        });
        setContentView(safeRoot);safeRoot.post(()->safeRoot.requestApplyInsets());
        try(InputStream in=getAssets().open("index.html")){
            String html=new String(readLimit(in,4*1024*1024),StandardCharsets.UTF_8);
            web.loadDataWithBaseURL("https://flac.local/",html,"text/html","UTF-8",null);
        }catch(Exception e){toast("No se pudo abrir la interfaz.");}
    }
    private AudioAttributes attributes(){return new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build();}
    private void emit(String fn,Object data){if(pageReady&&!destroyed)web.evaluateJavascript("window."+fn+"&&window."+fn+"("+String.valueOf(data)+");",null);}
    private void notice(String message){emit("onNotice",JSONObject.quote(message));}
    private void toast(String text){Toast.makeText(this,text,Toast.LENGTH_LONG).show();}
    private void put(JSONObject j,String key,Object value){try{j.put(key,value);}catch(JSONException ignored){}}
    private JSONObject settings(){JSONObject j=new JSONObject();put(j,"dark",prefs.getBoolean("dark",true));put(j,"glass",prefs.getBoolean("glass",true));put(j,"hue",prefs.getInt("hue",1));return j;}
    private void sendSettings(){emit("onSettings",settings());systemColors(prefs.getBoolean("dark",true));}
    private void systemColors(boolean dark){int c=dark?0xff0c0b12:0xfff1eef7;getWindow().setStatusBarColor(c);getWindow().setNavigationBarColor(c);web.setBackgroundColor(c);if(safeRoot!=null)safeRoot.setBackgroundColor(c);getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|(dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR));}
    private void sendPlaylists(){try{emit("onPlaylists",new JSONArray(prefs.getString("playlists","[]")));}catch(Exception ignored){emit("onPlaylists",new JSONArray());}}
    private void sendLibrary(){emit("onLibrary",library);emit("onFavorites",new JSONArray(prefs.getStringSet("favorites",new HashSet<>())));}
    private void sendScan(){JSONObject j=new JSONObject();put(j,"folder",prefs.getString("folderName","Ninguna carpeta seleccionada"));put(j,"status",scanStatus);put(j,"busy",scanning);put(j,"count",library.length());emit("onScan",j);}
    private void loadLibrary(){try(InputStream in=new FileInputStream(new File(getFilesDir(),"library.json"))){library=new JSONArray(new String(readLimit(in,32*1024*1024),StandardCharsets.UTF_8));scanStatus=library.length()+" archivos FLAC guardados.";}catch(Exception ignored){library=new JSONArray();}}
    private void saveLibrary(){try{atomicWrite(new File(getFilesDir(),"library.json"),library.toString().getBytes(StandardCharsets.UTF_8));}catch(Exception e){notice("No se pudo guardar la biblioteca. Vuelve a escanear al abrir la app.");}}
    private static void atomicWrite(File file,byte[] bytes)throws IOException{AtomicFile a=new AtomicFile(file);FileOutputStream stream=null;try{stream=a.startWrite();stream.write(bytes);a.finishWrite(stream);}catch(IOException e){if(stream!=null)a.failWrite(stream);throw e;}}
    private static byte[] readLimit(InputStream in,int limit)throws IOException{if(in==null)throw new IOException("Sin acceso al archivo");ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>limit)throw new IOException("El archivo es demasiado grande");out.write(buffer,0,n);}return out.toByteArray();}
    private String key(String value){try{byte[] b=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));StringBuilder s=new StringBuilder();for(byte x:b)s.append(String.format(Locale.ROOT,"%02x",x));return s.toString();}catch(Exception e){throw new IllegalStateException(e);}}
    private File lyricFile(String uri){return new File(getFilesDir(),key(uri)+".lrc");}

    public class Bridge{
        @JavascriptInterface public void artwork(int index){ui.post(()->{
            if(index<0||index>=library.length())return;
            final String uri=library.optJSONObject(index).optString("uri");
            artworkWorker.execute(()->{String image=readArtwork(Uri.parse(uri));ui.post(()->{if(destroyed)return;JSONObject data=new JSONObject();put(data,"uri",uri);put(data,"cover",image);emit("onArtwork",data);});});});}
        @JavascriptInterface public void chooseFolder(){ui.post(()->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);i.putExtra(Intent.EXTRA_LOCAL_ONLY,true);startActivityForResult(i,TREE);});}
        @JavascriptInterface public void rescan(){ui.post(()->{String s=prefs.getString("folder",null);if(s==null)notice("Selecciona una carpeta primero.");else scan(Uri.parse(s));});}
        @JavascriptInterface public void playTrack(int i){ui.post(()->{playQueue=null;prefs.edit().remove("queue").apply();openTrack(i,true);});}
        @JavascriptInterface public void playQueue(String json){ui.post(()->{try{JSONArray source=new JSONArray(json);JSONArray valid=new JSONArray();for(int i=0;i<source.length()&&i<2000;i++){int index=source.optInt(i,-1);if(index>=0&&index<library.length()&&!contains(valid,index))valid.put(index);}if(valid.length()>0){playQueue=valid;prefs.edit().putString("queue",valid.toString()).apply();openTrack(valid.optInt(0),true);}}catch(Exception e){notice("No se pudo iniciar la lista.");}});}
        @JavascriptInterface public void savePlaylists(String json){ui.post(()->{try{JSONArray data=new JSONArray(json);if(data.length()>100||json.length()>500000)throw new Exception();prefs.edit().putString("playlists",data.toString()).apply();}catch(Exception e){notice("No se pudo guardar la playlist.");}});}
        @JavascriptInterface public void toggle(){ui.post(()->{if(!ready||player==null){notice("Selecciona una canción de la biblioteca.");return;}if(player.isPlaying())pause();else start();});}
        @JavascriptInterface public void seek(long ms){ui.post(()->{if(ready&&player!=null){player.seekTo(Math.max(0,Math.min(ms,player.getDuration())),MediaPlayer.SEEK_CLOSEST);sendPosition();}});}
        @JavascriptInterface public void skip(int direction){ui.post(()->skipTrack(direction));}
        @JavascriptInterface public void importLyrics(){ui.post(()->{if(selected==null)return;pendingLyric=Uri.parse(selected.optString("uri"));Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);i.putExtra(Intent.EXTRA_LOCAL_ONLY,true);startActivityForResult(i,LETTER);});}
        @JavascriptInterface public void appearance(boolean dark,boolean glass,int hue){ui.post(()->{prefs.edit().putBoolean("dark",dark).putBoolean("glass",glass).putInt("hue",Math.max(0,Math.min(5,hue))).apply();systemColors(dark);});}
        @JavascriptInterface public void favorite(){ui.post(()->{if(selected==null)return;String id=selected.optString("uri");Set<String> f=new HashSet<>(prefs.getStringSet("favorites",new HashSet<>()));if(!f.add(id))f.remove(id);prefs.edit().putStringSet("favorites",f).apply();emit("onFavorites",new JSONArray(f));});}
        @JavascriptInterface public void lyricOffset(int delta){ui.post(()->{if(selected==null)return;String id=key(selected.optString("uri"));int offset=Math.max(-60000,Math.min(60000,prefs.getInt("offset_"+id,0)+delta));prefs.edit().putInt("offset_"+id,offset).apply();sendLyrics();});}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null||data.getData()==null){pendingLyric=null;return;}Uri uri=data.getData();
        if(request==TREE){
            try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception e){notice("No se pudo conservar acceso a esa carpeta.");return;}
            String previous=prefs.getString("folder",null);boolean changed=previous==null||!previous.equals(uri.toString());
            prefs.edit().putString("folder",uri.toString()).putString("folderName",folderName(uri)).apply();
            if(changed){scanGeneration.incrementAndGet();songGeneration.incrementAndGet();pause();if(player!=null){PlaybackService.release();player=null;}ready=false;selected=null;lyrics=Lrc.empty();library=new JSONArray();saveLibrary();prefs.edit().remove("track").remove("position").apply();emit("onTrack","null");sendLibrary();
                if(previous!=null)try{getContentResolver().releasePersistableUriPermission(Uri.parse(previous),Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}
            }scan(uri);
        }else if(request==LETTER&&pendingLyric!=null){Uri target=pendingLyric;pendingLyric=null;importLyrics(uri,target.toString());}
    }
    private String folderName(Uri tree){try{Uri u=DocumentsContract.buildDocumentUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree));try(Cursor c=getContentResolver().query(u,new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst())return c.getString(0);}}catch(Exception ignored){}return "Carpeta de música";}
    private void scan(Uri tree){
        File[] cachedArt=getCacheDir().listFiles((dir,name)->name.startsWith("art_"));if(cachedArt!=null)for(File file:cachedArt)file.delete();
        int generation=scanGeneration.incrementAndGet();scanning=true;scanStatus="Buscando FLAC en la carpeta y sus subcarpetas…";sendScan();
        scanWorker.execute(()->{
            java.util.HashMap<String,Long> knownDates=new java.util.HashMap<>();for(int i=0;i<library.length();i++){JSONObject old=library.optJSONObject(i);if(old!=null)knownDates.put(old.optString("uri"),old.optLong("addedAt",System.currentTimeMillis()));}
            JSONArray found=new JSONArray();ArrayDeque<String> dirs=new ArrayDeque<>();Set<String> visited=new HashSet<>();int skipped=0;
            try{
                dirs.add(DocumentsContract.getTreeDocumentId(tree));
                while(!dirs.isEmpty()){
                    if(generation!=scanGeneration.get()||destroyed)return;
                    String id=dirs.remove();if(!visited.add(id))continue;
                    Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,id);
                    String[] columns={DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_MIME_TYPE};
                    try(Cursor c=getContentResolver().query(children,columns,null,null,null)){
                        if(c==null)throw new IOException("No se pudo leer una carpeta");
                        while(c.moveToNext()){
                            if(generation!=scanGeneration.get()||destroyed)return;
                            String child=c.getString(0),name=c.getString(1),mime=c.getString(2);
                            if(DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)){dirs.add(child);continue;}
                            if(name==null||!name.toLowerCase(Locale.ROOT).endsWith(".flac"))continue;
                            if(found.length()>=20000)throw new IOException("Esta prueba admite hasta 20000 FLAC por carpeta");
                            Uri doc=DocumentsContract.buildDocumentUriUsingTree(tree,child);JSONObject song=new JSONObject();
                            put(song,"uri",doc.toString());put(song,"title",name.substring(0,name.length()-5));put(song,"artist","Artista desconocido");put(song,"album","Sin álbum");put(song,"duration",0);put(song,"addedAt",knownDates.containsKey(doc.toString())?knownDates.get(doc.toString()):System.currentTimeMillis());
                            MediaMetadataRetriever m=new MediaMetadataRetriever();
                            try{m.setDataSource(this,doc);metadata(m,song);}catch(Exception e){skipped++;}finally{try{m.release();}catch(Exception ignored){}}
                            found.put(song);
                            if(found.length()%10==0){final int count=found.length();ui.post(()->{if(generation==scanGeneration.get()){scanStatus="Leyendo etiquetas · "+count+" FLAC";sendScan();}});}
                        }
                    }
                }
                ArrayList<JSONObject> sorted=new ArrayList<>();for(int i=0;i<found.length();i++)sorted.add(found.optJSONObject(i));sorted.sort((a,b)->a.optString("title").compareToIgnoreCase(b.optString("title")));final JSONArray result=new JSONArray(sorted);final int unread=skipped;
                ui.post(()->{if(destroyed||generation!=scanGeneration.get())return;library=result;saveLibrary();scanning=false;scanStatus=library.length()+" archivos FLAC encontrados."+(unread>0?" "+unread+" sin etiquetas legibles.":"");sendLibrary();sendScan();});
            }catch(Exception e){ui.post(()->{if(destroyed||generation!=scanGeneration.get())return;scanning=false;scanStatus="No se completó el escaneo. "+(e.getMessage()==null?"Vuelve a elegir la carpeta.":e.getMessage());sendScan();});}
        });
    }
    private void metadata(MediaMetadataRetriever m,JSONObject song){String t=m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE),a=m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST),album=m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM),duration=m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);if(t!=null&&!t.trim().isEmpty())put(song,"title",t);if(a!=null&&!a.trim().isEmpty())put(song,"artist",a);if(album!=null&&!album.trim().isEmpty())put(song,"album",album);try{put(song,"duration",Long.parseLong(duration));}catch(Exception ignored){}}
    private String imageData(byte[] bytes){
        if(bytes==null||bytes.length>20*1024*1024)return "";
        BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,o);
        if(o.outWidth<=0||o.outHeight<=0)return "";o.inSampleSize=1;
        while(Math.max(o.outWidth,o.outHeight)/o.inSampleSize>800)o.inSampleSize*=2;
        o.inJustDecodeBounds=false;Bitmap b=BitmapFactory.decodeByteArray(bytes,0,bytes.length,o);if(b==null)return "";
        ByteArrayOutputStream out=new ByteArrayOutputStream();b.compress(Bitmap.CompressFormat.JPEG,85,out);b.recycle();
        return "data:image/jpeg;base64,"+android.util.Base64.encodeToString(out.toByteArray(),android.util.Base64.NO_WRAP);
    }
    private String readArtwork(Uri uri){
        File cached=new File(getCacheDir(),"art_"+key(uri.toString())+".txt");
        try(InputStream in=new FileInputStream(cached)){return new String(readLimit(in,2*1024*1024),StandardCharsets.UTF_8);}catch(Exception ignored){}
        String image="";
        // Prefer the standardized front-cover PICTURE block directly; no system decoder dependency.
        try(InputStream in=getContentResolver().openInputStream(uri)){image=imageData(FlacArtwork.read(in));}catch(Exception ignored){}
        if(image.isEmpty()){
            MediaMetadataRetriever m=new MediaMetadataRetriever();
            try{m.setDataSource(this,uri);image=imageData(m.getEmbeddedPicture());}catch(Exception ignored){}finally{try{m.release();}catch(Exception ignored){}}
        }
        if(!image.isEmpty())try{atomicWrite(cached,image.getBytes(StandardCharsets.UTF_8));}catch(IOException ignored){}
        return image;
    }
    private void openTrack(int index,boolean autoplay){
        if(index<0||index>=library.length())return;int generation=songGeneration.incrementAndGet();pause();ready=false;if(player!=null)PlaybackService.release();
        JSONObject original=library.optJSONObject(index);if(original==null)return;try{selected=new JSONObject(original.toString());}catch(Exception e){return;}
        String uri=selected.optString("uri");int resume=autoplay?0:prefs.getInt("position",0);prefs.edit().putString("track",uri).putInt("position",resume).apply();
        put(selected,"index",index);put(selected,"cover","");emit("onTrack",selected);lyrics=Lrc.empty();sendLyrics();
        player=new MediaPlayer();PlaybackService.attach(player,uri);PlaybackService.metadata(selected.optString("title","Música"),selected.optString("artist","Artista desconocido"));final MediaPlayer p=player;p.setAudioAttributes(attributes());
        p.setOnPreparedListener(v->{if(destroyed||p!=player)return;ready=true;PlaybackService.prepared=true;if(resume>0)p.seekTo(Math.min(resume,p.getDuration()),MediaPlayer.SEEK_CLOSEST);if(autoplay)start();sendPosition();});
        p.setOnErrorListener((v,what,extra)->{if(v!=player)return true;ready=false;audio.abandonAudioFocusRequest(focus);notice("No se pudo reproducir este FLAC. Código "+what+"/"+extra);sendPosition();return true;});
        p.setOnCompletionListener(v->{if(v==player&&!destroyed)skipTrack(1);});
        mediaWorker.execute(()->{
            JSONObject enriched;
            try{enriched=new JSONObject(original.toString());}catch(Exception e){return;}
            MediaMetadataRetriever m=new MediaMetadataRetriever();
            try{m.setDataSource(this,Uri.parse(uri));metadata(m,enriched);}catch(Exception ignored){}finally{try{m.release();}catch(Exception ignored){}}
            put(enriched,"cover",readArtwork(Uri.parse(uri)));
            Lrc saved=Lrc.empty();try(InputStream in=new FileInputStream(lyricFile(uri))){saved=Lrc.parse(new String(readLimit(in,2*1024*1024),StandardCharsets.UTF_8));}catch(Exception ignored){}
            final Lrc loaded=saved;
            ui.post(()->{if(destroyed||generation!=songGeneration.get())return;put(enriched,"index",index);selected=enriched;lyrics=loaded;emit("onTrack",selected);sendLyrics();});
        });
        try{p.setDataSource(this,Uri.parse(uri));p.prepareAsync();}catch(Exception e){notice("No se puede abrir el archivo. Revisa la carpeta o vuelve a escanear.");}
    }
    private void start(){if(ready&&player!=null&&audio.requestAudioFocus(focus)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED){player.start();PlaybackService.update(this,true);sendPosition();}}
    private void pause(){if(ready&&player!=null){if(player.isPlaying())player.pause();prefs.edit().putInt("position",player.getCurrentPosition()).apply();}if(audio!=null&&focus!=null)audio.abandonAudioFocusRequest(focus);PlaybackService.update(this,ready);sendPosition();}
    private static boolean contains(JSONArray values,int index){for(int i=0;i<values.length();i++)if(values.optInt(i,-1)==index)return true;return false;}
    private void skipTrack(int direction){if(selected==null||library.length()==0)return;int current=-1;for(int i=0;i<library.length();i++){if(library.optJSONObject(i).optString("uri").equals(selected.optString("uri"))){current=i;break;}}int target=(current+(direction<0?-1:1)+library.length())%library.length();
        if(playQueue==null){try{String raw=prefs.getString("queue",null);if(raw!=null)playQueue=new JSONArray(raw);}catch(Exception ignored){}}
        if(playQueue!=null&&playQueue.length()>0){int at=-1;for(int i=0;i<playQueue.length();i++)if(playQueue.optInt(i)==current){at=i;break;}if(at>=0)target=playQueue.optInt((at+(direction<0?-1:1)+playQueue.length())%playQueue.length(),target);}
        openTrack(target,true);}
    private void sendPosition(){JSONObject j=new JSONObject();put(j,"ready",ready);put(j,"playing",ready&&player!=null&&player.isPlaying());put(j,"position",ready&&player!=null?player.getCurrentPosition():0);put(j,"duration",ready&&player!=null?player.getDuration():0);emit("onPosition",j);}
    private void sendLyrics(){JSONArray lines=new JSONArray();for(Lrc.Line l:lyrics.lines){JSONObject j=new JSONObject();put(j,"time",l.time);put(j,"text",l.text);lines.put(j);}JSONObject obj=new JSONObject();put(obj,"lines",lines);put(obj,"offset",selected==null?0:prefs.getInt("offset_"+key(selected.optString("uri")),0));emit("onLyrics",obj);}
    private void importLyrics(Uri source,String target){
        mediaWorker.execute(()->{try(InputStream in=getContentResolver().openInputStream(source)){
            byte[] bytes=readLimit(in,2*1024*1024);Charset encoding=StandardCharsets.UTF_8;if(bytes.length>=2&&((bytes[0]==(byte)0xff&&bytes[1]==(byte)0xfe)||(bytes[0]==(byte)0xfe&&bytes[1]==(byte)0xff)))encoding=StandardCharsets.UTF_16;
            String value=new String(bytes,encoding);Lrc parsed=Lrc.parse(value);if(parsed.lines.isEmpty()||parsed.lines.size()>5000)throw new IOException("Selecciona un LRC con tiempos válidos (máximo 5000 líneas).");atomicWrite(lyricFile(target),value.getBytes(StandardCharsets.UTF_8));
            ui.post(()->{if(destroyed)return;prefs.edit().putInt("offset_"+key(target),0).apply();if(selected!=null&&target.equals(selected.optString("uri"))){lyrics=parsed;sendLyrics();}notice("Letra añadida a la canción.");});
        }catch(Exception e){ui.post(()->notice("No se pudo importar: "+e.getMessage()));}});
    }
    @Override public void onBackPressed(){web.evaluateJavascript("window.goBack&&window.goBack()",value->{if(!"true".equals(value))super.onBackPressed();});}
    @Override protected void onStart(){super.onStart();visible=true;ui.post(tick);}
    @Override protected void onStop(){visible=false;ui.removeCallbacks(tick);super.onStop();}
    @Override protected void onDestroy(){PlaybackService.control=null;destroyed=true;scanGeneration.incrementAndGet();songGeneration.incrementAndGet();ui.removeCallbacksAndMessages(null);scanWorker.shutdownNow();mediaWorker.shutdownNow();artworkWorker.shutdownNow();if(player!=null){if(!ready || !player.isPlaying())PlaybackService.release();else{player.setOnPreparedListener(null);player.setOnErrorListener(null);player.setOnCompletionListener(v->PlaybackService.update(getApplicationContext(),true));}}unregisterReceiver(noisy);web.removeJavascriptInterface("Native");web.destroy();super.onDestroy();}
}
