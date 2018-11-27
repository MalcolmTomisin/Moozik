package com.skillslevel.moozik;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.session.MediaSessionManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.os.RemoteException;
import android.provider.MediaStore;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.support.v4.app.NotificationCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.app.NotificationCompat.MediaStyle;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;

import static android.content.ContentValues.TAG;


public class MediaPlayerService extends Service implements MediaPlayer.OnCompletionListener, MediaPlayer.OnPreparedListener,
        MediaPlayer.OnErrorListener, MediaPlayer.OnSeekCompleteListener, MediaPlayer.OnInfoListener,
        MediaPlayer.OnBufferingUpdateListener, AudioManager.OnAudioFocusChangeListener {

    private final IBinder mBinder = new LocalBinder();
    private ArrayList<Audio> audioList;
    private int audioIndex = -1;
    private Audio activeAudio;
    public static final String ACTION_PLAY = "com.skillslevel.moozik.ACTION_PLAY";
    public static final String ACTION_PAUSE = "com.skillslevel.moozik.ACTION_PAUSE";
    public static final String ACTION_PREVIOUS = "com.skillslevel.moozik.ACTION_PREVIOUS";
    public static final String ACTION_NEXT = "com.skillslevel.moozik.ACTION_NEXT";
    public static final String ACTION_STOP = "com.skillslevel.moozik.ACTION_STOP";
    //MediaSession
    private MediaSessionManager mediaSessionManager;
    private MediaSessionCompat mediaSession;
    private MediaControllerCompat.TransportControls transportControls;

    //AudioPlayer notification ID
    private static final int NOTIFICATION_ID = 101;


    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        // Perform one-time setup procedures

        // Manage incoming phone calls during playback.
        // Pause MediaPlayer on incoming call,
        // Resume on hangup.
        callStateListener();
        //ACTION_AUDIO_BECOMING_NOISY -- change in audio outputs -- BroadcastReceiver
        registerAudioChangeReceiver();
        //Listen for new Audio to play -- BroadcastReceiver
        register_playNewAudio();


    }

    @Override
    public void onAudioFocusChange(int focusChange) {
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_GAIN:
                if (mediaPlayer == null) initMediaPlayer();
                else if (!mediaPlayer.isPlaying()) mediaPlayer.start();
                mediaPlayer.setVolume(1.0f, 1.0f);
                break;

            case AudioManager.AUDIOFOCUS_LOSS:
                if (mediaPlayer.isPlaying()) mediaPlayer.stop();
                mediaPlayer.release();
                mediaPlayer = null;
                break;

            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                if (mediaPlayer.isPlaying()) mediaPlayer.pause();
                break;

            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                if (mediaPlayer.isPlaying()) mediaPlayer.setVolume(0.1f, 0.1f);
                break;
        }

    }

    private boolean requestAudioFocus() {
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        int result = audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {

            return true;
        }
        return false;
    }

    private boolean removeAudioFocus() {
        return AudioManager.AUDIOFOCUS_REQUEST_GRANTED ==
                audioManager.abandonAudioFocus(this);
    }

    @Override
    public void onBufferingUpdate(MediaPlayer mediaPlayer, int percent) {

    }

    @Override
    public void onCompletion(MediaPlayer mediaPlayer) {

        stopMedia();

        stopSelf();

    }

    @Override
    public boolean onError(MediaPlayer mediaPlayer, int what, int error) {

        switch (what) {
            case MediaPlayer.MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK:
                Log.d("MediaPlayer Error", "MEDIA ERROR NOT VALID FOR PROGRESSIVE PLAYBACK " + error);
                break;
            case MediaPlayer.MEDIA_ERROR_SERVER_DIED:
                Log.d("MediaPlayer Error", "MEDIA ERROR SERVER DIED " + error);
                break;
            case MediaPlayer.MEDIA_ERROR_UNKNOWN:
                Log.d("MediaPlayer Error", "MEDIA ERROR UNKNOWN " + error);
                break;
        }

        return false;
    }

    @Override
    public boolean onInfo(MediaPlayer mediaPlayer, int what, int error) {
        return false;
    }

    @Override
    public void onPrepared(MediaPlayer mediaPlayer) {
        playMedia();
    }

    @Override
    public void onSeekComplete(MediaPlayer mediaPlayer) {

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
//Load data from SharedPreferences
            StorageDataUtil storage = new StorageDataUtil(getApplicationContext());
            audioList = storage.loadAudio();
            audioIndex = storage.loadAudioIndex();
            if (audioIndex != -1 && audioIndex < audioList.size()) {
//index is in a valid range
                activeAudio = audioList.get(audioIndex);
            } else {
                stopSelf();
            }
        } catch (NullPointerException e) {
            stopSelf();
        }
//Request audio focus
        if (requestAudioFocus() == false) {
//Could not gain focus
            stopSelf();
        }
        if (mediaSessionManager == null) {
            try {
                initMediaSession();
                initMediaPlayer();
            } catch (RemoteException e) {
                e.printStackTrace();
                stopSelf();
            }
            buildNotification(PlayBackStatus.PLAYING);
        }
//Handle Intent action from MediaSession.TransportControls
        handleIncomingActions(intent);
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            stopMedia();
            mediaPlayer.release();
        }
        removeAudioFocus();

        if (phoneStateListener != null) {
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
        }

        removeNotification();


        unregisterReceiver(audioChange);
        unregisterReceiver(playNewAudio);

        //clear cached playlist
        new StorageDataUtil(getApplicationContext()).clearCachedAudioPlaylist();
    }

    public class LocalBinder extends Binder {

        public MediaPlayerService getService() {
            return MediaPlayerService.this;
        }
    }

    private MediaPlayer mediaPlayer;
   private File path = android.os.Environment.getExternalStorageDirectory();
    Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
   // public static String DIRECTORY_MUSIC;

    private String getRealPathFromURI (Context context, Uri contentUri){
        Cursor cursor = null;
        String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0";

        try{
            String[] proj = {MediaStore.Audio.Media.DATA,
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.DISPLAY_NAME,
                    MediaStore.Audio.Media.DURATION};
            cursor = context.getContentResolver().query(contentUri,proj, selection, null, null);
            int columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);
            cursor.moveToFirst();
            return cursor.getString(columnIndex);
        } catch (Exception e) {
            Log.e(TAG, "getRealPathFromURI Exception : " + e.toString());
            return "";
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        }




    private void initMediaPlayer() {
        if (mediaPlayer == null)
            mediaPlayer = new MediaPlayer();

        mediaPlayer.setOnCompletionListener(this);
        mediaPlayer.setOnErrorListener(this);
        mediaPlayer.setOnBufferingUpdateListener(this);
        mediaPlayer.setOnSeekCompleteListener(this);
        mediaPlayer.setOnInfoListener(this);

        mediaPlayer.reset();
        //Context context = getApplicationContext();
        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        try {
          //  FileInputStream is = new FileInputStream(path);
          //  FileDescriptor fileDescriptor = is.getFD();
            mediaPlayer.setDataSource(activeAudio.getData());

          //  is.close();
        } catch (IOException e) {
            e.printStackTrace();
            stopSelf();
        }

        mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                mp.start();
            }
        });

        mediaPlayer.prepareAsync();


    }

    private int resumePosition;

    private void playMedia() {
        if (!mediaPlayer.isPlaying()) {
            mediaPlayer.start();
        }
    }

    private void stopMedia() {
        if (mediaPlayer == null) return;
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.stop();
        }
    }

    private void pauseMedia() {
        if ( mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            resumePosition = mediaPlayer.getCurrentPosition();
        }
    }

    private void resumeMedia() {
        if (!mediaPlayer.isPlaying()) {
            mediaPlayer.seekTo(resumePosition);
            mediaPlayer.start();
        }
    }

    private AudioManager audioManager;

    private BroadcastReceiver audioChange = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            pauseMedia();
            buildNotification(PlayBackStatus.PAUSED);
        }
    };

    private void registerAudioChangeReceiver() {
        IntentFilter intentFilter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        registerReceiver(audioChange, intentFilter);
    }

    private boolean ongoingCall = false;
    private PhoneStateListener phoneStateListener;
    private TelephonyManager telephonyManager;

    private void callStateListener() {
        telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        phoneStateListener = new PhoneStateListener() {
            @Override
            public void onCallStateChanged(int state, String incomingNumber) {
                switch (state) {
                    case TelephonyManager.CALL_STATE_RINGING:
                    case TelephonyManager.CALL_STATE_OFFHOOK:
                        if (mediaPlayer != null) {
                            pauseMedia();
                            ongoingCall = true;
                        }
                        break;

                    case TelephonyManager.CALL_STATE_IDLE:
                        if (mediaPlayer != null) {
                            if (ongoingCall) {
                                ongoingCall = false;
                                resumeMedia();
                            }
                        }
                        break;
                }
            }
        };
        telephonyManager.listen(phoneStateListener,
                PhoneStateListener.LISTEN_CALL_STATE);
    }

    private BroadcastReceiver playNewAudio = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            audioIndex = new StorageDataUtil(getApplicationContext()).loadAudioIndex();
            if (audioIndex != -1 && audioIndex < audioList.size()) {
                activeAudio = audioList.get(audioIndex);
            } else {
                stopSelf();
            }
            stopMedia();
            mediaPlayer.reset();
            initMediaPlayer();
            updateMetaData();
            buildNotification(PlayBackStatus.PLAYING);
        }
    };

    private void register_playNewAudio() {
        IntentFilter filter = new IntentFilter(MainActivity.Broadcast_PLAY_NEW_AUDIO);
        registerReceiver(playNewAudio, filter);
    }

    private void initMediaSession() throws RemoteException {
        if (mediaSessionManager != null) return; //mediaSessionManager exists
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mediaSessionManager = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
        }

        mediaSession = new MediaSessionCompat(getApplicationContext(), "AudioPlayer");
//Get MediaSessions transport controls
        transportControls = mediaSession.getController().getTransportControls();
//set MediaSession -> ready to receive media commands
        mediaSession.setActive(true);
//indicate that the MediaSession handles transport control commands
// through its MediaSessionCompat.Callback.
        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
//Set mediaSession's MetaData
        updateMetaData();
// Attach Callback to receive MediaSession updates
        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            // Implement callbacks
            @Override
            public void onPlay() {
                super.onPlay();
                resumeMedia();
                buildNotification(PlayBackStatus.PLAYING);
            }

            @Override
            public void onPause() {
                super.onPause();
                pauseMedia();
                buildNotification(PlayBackStatus.PAUSED);
            }

            @Override
            public void onSkipToNext() {
                super.onSkipToNext();
                skipToNext();
                updateMetaData();
                buildNotification(PlayBackStatus.PLAYING);
            }

            @Override
            public void onSkipToPrevious() {
                super.onSkipToPrevious();
                skipToPrevious();
                updateMetaData();
                buildNotification(PlayBackStatus.PLAYING);
            }

            @Override
            public void onStop() {
                super.onStop();
                removeNotification();
//Stop the service
                stopSelf();
            }

            @Override
            public void onSeekTo(long position) {
                super.onSeekTo(position);
            }
        });
    }

    private void updateMetaData() {
        Bitmap albumArt = BitmapFactory.decodeResource(getResources(),
                R.drawable.ic_play);

        // Update the current metadata
// Update the current metadata
        mediaSession.setMetadata(new MediaMetadataCompat.Builder()
                .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, albumArt)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, activeAudio.getArtist())
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, activeAudio.getAlbum())
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, activeAudio.getTitle())
                .build());
    }

    private void skipToNext() {
        if (audioIndex == audioList.size() - 1) {
//if last in playlist
            audioIndex = 0;
            activeAudio = audioList.get(audioIndex);
        } else {
//get next in playlist
            activeAudio = audioList.get(++audioIndex);
        }
        new StorageDataUtil(getApplicationContext()).storeAudioIndex(audioIndex);
        stopMedia();
//reset mediaPlayer
        mediaPlayer.reset();
        initMediaPlayer();
    }

    private void skipToPrevious() {
        if (audioIndex == 0) {
//if first in playlist
//set index to the last of audioList
            audioIndex = audioList.size() - 1;
            activeAudio = audioList.get(audioIndex);
        } else {
//get previous in playlist
            activeAudio = audioList.get(--audioIndex);
        }
//Update stored index
        new StorageDataUtil(getApplicationContext()).storeAudioIndex(audioIndex);
        stopMedia();
//reset mediaPlayer
        mediaPlayer.reset();
        initMediaPlayer();
    }
    private void buildNotification(PlayBackStatus playbackStatus) {
        //initializing notification channel
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        String channelId = "mplayer_Notif";
        CharSequence channelName = "mPlayer";
        int importance = NotificationManager.IMPORTANCE_DEFAULT;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel notificationChannel = new NotificationChannel(channelId, channelName, importance);
            notificationChannel.setLockscreenVisibility(1);
            notificationManager.createNotificationChannel(notificationChannel);
        }
        int notificationAction = android.R.drawable.ic_media_pause;//needs to be initialized
        PendingIntent play_pauseAction = null;
//Build a new notification according to the current state of the MediaPlayer
        if (playbackStatus == PlayBackStatus.PLAYING) {
            notificationAction = android.R.drawable.ic_media_pause;
//create the pause action
            play_pauseAction = playbackAction(1);
        } else if (playbackStatus == PlayBackStatus.PAUSED) {
            notificationAction = android.R.drawable.ic_media_play;
//create the play action
            play_pauseAction = playbackAction(0);
        }
        Bitmap largeIcon = BitmapFactory.decodeResource(getResources(),
                R.drawable.ic_play); //replace with your own image
// Create a new Notification
        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, channelId)
                .setShowWhen(false)
// Set the Notification style
                .setStyle(
                        new MediaStyle()
// Attach our MediaSession token
                                .setMediaSession(mediaSession.getSessionToken())
// Show our playback controls in the compact notification view.
                                .setShowActionsInCompactView(0, 1, 2))
// Set the Notification color
                .setColor(getResources().getColor(R.color.colorPrimary))
// Set the large and small icons
                .setLargeIcon(largeIcon)
                .setSmallIcon(android.R.drawable.stat_sys_headset)
// Set Notification content information
                .setContentText(activeAudio.getArtist())
                .setContentTitle(activeAudio.getAlbum())
                .setContentInfo(activeAudio.getTitle())
// Add playback actions
                .addAction(android.R.drawable.ic_media_previous, "previous", playbackAction(3))
                .addAction(notificationAction, "pause", play_pauseAction)
                .addAction(android.R.drawable.ic_media_next, "next", playbackAction(2));
        ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).notify(NOTIFICATION_ID, notificationBuilder.build());
    }

    private void removeNotification() {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(NOTIFICATION_ID);
    }
    private PendingIntent playbackAction(int actionNumber) {
        Intent playbackAction = new Intent(this, MediaPlayerService.class);
        switch (actionNumber) {
            case 0:
// Play
                playbackAction.setAction(ACTION_PLAY);
                return PendingIntent.getService(this, actionNumber, playbackAction, 0);
            case 1:
// Pause
                playbackAction.setAction(ACTION_PAUSE);
                return PendingIntent.getService(this, actionNumber, playbackAction, 0);
            case 2:
// Next track
                playbackAction.setAction(ACTION_NEXT);
                return PendingIntent.getService(this, actionNumber, playbackAction, 0);
            case 3:
// Previous track
                playbackAction.setAction(ACTION_PREVIOUS);
                return PendingIntent.getService(this, actionNumber, playbackAction, 0);
            default:
                break;
        }
        return null;
    }

    private void handleIncomingActions(Intent playbackAction){
        if (playbackAction == null || playbackAction.getAction() == null) return;
        String actionString = playbackAction.getAction();
        if (actionString.equalsIgnoreCase(ACTION_PLAY)) {
            transportControls.play();
        } else if (actionString.equalsIgnoreCase(ACTION_PAUSE)) {
            transportControls.pause();
        } else if (actionString.equalsIgnoreCase(ACTION_NEXT)) {
            transportControls.skipToNext();
        } else if (actionString.equalsIgnoreCase(ACTION_PREVIOUS)) {
            transportControls.skipToPrevious();
        } else if (actionString.equalsIgnoreCase(ACTION_STOP)) {
            transportControls.stop();
        }
    }
}
