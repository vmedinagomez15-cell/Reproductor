package com.victor.flacprueba;

import android.app.*;
import android.content.*;
import android.media.MediaPlayer;
import android.os.*;

/** Owns playback while the WebView activity is not visible. */
public class PlaybackService extends Service {
    static MediaPlayer player;
    static String trackUri;
    static boolean prepared;
    private static PlaybackService instance;
    private static final int ID=47;
    private static final String CHANNEL="flac_playback";
    private static final String TOGGLE="com.victor.flacprueba.TOGGLE";
    private static final String STOP="com.victor.flacprueba.STOP";

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
            instance.stopForeground(STOP_FOREGROUND_REMOVE);
            instance.stopSelf();
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
        if(TOGGLE.equals(action) && player!=null && prepared){
            if(player.isPlaying())player.pause();else player.start();
            update(this,true);
        }
        if(player!=null && prepared && player.isPlaying())show();else stopSelf();
        return START_NOT_STICKY;
    }
    private PendingIntent command(String action,int request){
        Intent intent=new Intent(this,PlaybackService.class).setAction(action);
        return PendingIntent.getService(this,request,intent,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
    }
    private void show(){
        if(player==null||!prepared||!player.isPlaying())return;
        Intent launch=new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent open=PendingIntent.getActivity(this,0,launch,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notification=new Notification.Builder(this,CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("FLAC · Prueba")
            .setContentText("Reproduciendo música")
            .setContentIntent(open).setOngoing(true)
            .addAction(new Notification.Action.Builder(android.R.drawable.ic_media_pause,"Pausar",command(TOGGLE,1)).build())
            .addAction(new Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel,"Detener",command(STOP,2)).build())
            .setStyle(new Notification.MediaStyle()).build();
        startForeground(ID,notification);
    }
    @Override public void onDestroy(){if(instance==this)instance=null;super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}
