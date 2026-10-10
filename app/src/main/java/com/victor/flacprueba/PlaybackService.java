package com.victor.flacprueba;

import android.app.*;
import android.content.*;
import android.media.MediaPlayer;
import android.os.*;
import android.net.Uri;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Owns playback while the WebView activity is not visible. */
public class PlaybackService extends Service {
    interface Controller { void command(int direction); }
    static Controller control;
    static MediaPlayer player;
    static String trackUri;
    static String title="FLAC · Prueba", artist="Reproduciendo música";
    static void metadata(String t,String a){title=t;artist=a;if(instance!=null)instance.publish(player!=null&&prepared&&player.isPlaying());}
    static boolean prepared;
    private static PlaybackService instance;
    private static final int ID=47;
    private static final String CHANNEL="flac_playback";
    private static final String TOGGLE="com.victor.flacprueba.TOGGLE";
    private static final String STOP="com.victor.flacprueba.STOP";
    private static final String NEXT="com.victor.flacprueba.NEXT";
    private static final String PREV="com.victor.flacprueba.PREV";

    static void attach(MediaPlayer p,String uri){
        if(player!=null && player!=p) { try { player.release(); } catch(Exception ignored) {} }
        player=p; trackUri=uri; prepared=false;
    }
    static void update(Context context,boolean isPrepared){
        prepared=isPrepared;
        if(player!=null && isPrepared && player.isPlaying()){
            Intent intent=new Intent(context,PlaybackService.class);
            if(Build.VERSION.SDK_INT>=26)context.startForegroundService(intent);else context.startService(intent);
            if(instance!=null)instance.show();
        }else if(instance!=null){
            instance.stopForeground(STOP_FOREGROUND_DETACH);
            if(player!=null && isPrepared)instance.showPaused();else instance.stopSelf();
        }
    }
    static void release(){
        if(player!=null){try{player.release();}catch(Exception ignored){}}
        player=null;trackUri=null;prepared=false;
        if(instance!=null){instance.stopForeground(STOP_FOREGROUND_REMOVE);instance.stopSelf();}
    }
    @Override public void onCreate(){super.onCreate();instance=this;
        NotificationChannel channel=new NotificationChannel(CHANNEL,"Reproducción de música",NotificationManager.IMPORTANCE_LOW);
        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(channel);
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        String action=intent==null?null:intent.getAction();
        if(STOP.equals(action)){
            if(player!=null && prepared){player.pause();update(this,true);}
            return START_NOT_STICKY;
        }
        if(NEXT.equals(action)||PREV.equals(action)){int d=NEXT.equals(action)?1:-1;if(control!=null)control.command(d);else skipWithoutActivity(d);return START_STICKY;}
        if(TOGGLE.equals(action) && control!=null){control.command(0);return START_STICKY;}
        if(TOGGLE.equals(action) && player!=null && prepared){
            if(player.isPlaying())player.pause();else player.start();
            update(this,true);
        }
        if(player!=null && prepared && player.isPlaying())show();else if(player!=null&&prepared)showPaused();else stopSelf();
        return START_NOT_STICKY;
    }
    private PendingIntent command(String action,int request){
        Intent intent=new Intent(this,PlaybackService.class).setAction(action);
        return PendingIntent.getService(this,request,intent,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
    }
    private void show(){publish(true);}
    private void showPaused(){publish(false);}
    private void publish(boolean active){
        if(player==null||!prepared)return;
        Intent launch=new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent open=PendingIntent.getActivity(this,0,launch,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Action previous=new Notification.Action.Builder(android.R.drawable.ic_media_previous,"Anterior",command(PREV,3)).build();
        Notification.Action toggle=new Notification.Action.Builder(active?android.R.drawable.ic_media_pause:android.R.drawable.ic_media_play,active?"Pausar":"Reanudar",command(TOGGLE,1)).build();
        Notification.Action next=new Notification.Action.Builder(android.R.drawable.ic_media_next,"Siguiente",command(NEXT,4)).build();
        Notification notification=new Notification.Builder(this,CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(artist)
            .setContentIntent(open).setOngoing(active)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(previous).addAction(toggle).addAction(next)
            .setStyle(new Notification.MediaStyle().setShowActionsInCompactView(0,1,2)).build();
        if(active)startForeground(ID,notification);
        else ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(ID,notification);
    }
    private void skipWithoutActivity(int direction){
        try{
            File file=new File(getFilesDir(),"library.json");
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            try(InputStream in=new FileInputStream(file)){byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);}
            JSONArray songs=new JSONArray(out.toString("UTF-8"));if(songs.length()==0)return;
            int current=0;
            for(int i=0;i<songs.length();i++)if(songs.optJSONObject(i).optString("uri").equals(trackUri)){current=i;break;}
            int next=(current+direction+songs.length())%songs.length();JSONObject song=songs.optJSONObject(next);
            String uri=song.optString("uri");
            MediaPlayer replacement=new MediaPlayer();replacement.setAudioAttributes(new android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_MEDIA).setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC).build());
            replacement.setDataSource(this,Uri.parse(uri));attach(replacement,uri);
            title=song.optString("title","Música");artist=song.optString("artist","Artista desconocido");
            getSharedPreferences("player",MODE_PRIVATE).edit().putString("track",uri).putInt("position",0).apply();
            replacement.setOnPreparedListener(mp->{prepared=true;mp.start();update(this,true);});
            replacement.setOnCompletionListener(mp->skipWithoutActivity(1));
            replacement.prepareAsync();
        }catch(Exception ignored){}
    }
    @Override public void onDestroy(){if(instance==this)instance=null;super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}
