package com.nilaweera.aalearn;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.view.Gravity;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media.MediaBrowserServiceCompat;

import java.util.ArrayList;
import java.util.List;

/**
 * Media-app face of Road Class.
 *
 * Android Auto discovers media apps through a completely different, far less
 * restricted path than Car App Library template apps. A WebView runs offscreen on
 * the phone, injected JS reports playback state back over a JavascriptInterface,
 * and that drives a real MediaSession - so the head unit sees an ordinary media
 * app with working steering-wheel controls.
 *
 * Audio only. Media apps are never handed a drawing Surface; video lives in
 * CarWebService.
 */
public class YoutubeAudioService extends MediaBrowserServiceCompat {

    private static final String ROOT_ID = "root";
    private static final String CHANNEL = "playback";
    private static final int NOTIF_ID = 42;

    /** Polls the page and reports state changes. Cheap and resilient to UI churn. */
    private static final String JS_WATCH =
            "(function(){"
                    + "if(window.__rcHooked) return; window.__rcHooked=true;"
                    + "var last='';"
                    + "setInterval(function(){"
                    + "  var v=document.querySelector('video');"
                    + "  if(!v){return;}"
                    + "  var t=(document.title||'').replace(/ - YouTube.*$/,'');"
                    + "  var s=v.paused?'paused':'playing';"
                    + "  var msg=s+'|'+t;"
                    + "  if(msg!==last){last=msg;RoadClass.onState(s,t);}"
                    + "},700);"
                    + "})();";

    private final Handler main = new Handler(Looper.getMainLooper());
    private MediaSessionCompat session;
    private WebView web;
    private WindowManager wm;

    @Override
    public void onCreate() {
        super.onCreate();

        session = new MediaSessionCompat(this, "RoadClass");
        session.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS
                | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
        session.setCallback(new Callback());
        setSessionToken(session.getSessionToken());
        setState(PlaybackStateCompat.STATE_STOPPED);
    }

    // ------------------------------------------------------------ browse tree

    @Nullable
    @Override
    public BrowserRoot onGetRoot(@NonNull String pkg, int uid, @Nullable Bundle hints) {
        return new BrowserRoot(ROOT_ID, null);
    }

    @Override
    public void onLoadChildren(@NonNull String parentId,
                               @NonNull Result<List<MediaBrowserCompat.MediaItem>> result) {
        List<MediaBrowserCompat.MediaItem> items = new ArrayList<>();
        if (ROOT_ID.equals(parentId)) {
            items.add(item("start", "Continue", Prefs.startUrl(this)));
            items.add(item("subs", "Subscriptions",
                    "https://m.youtube.com/feed/subscriptions"));
            items.add(item("later", "Watch later",
                    "https://m.youtube.com/playlist?list=WL"));
        }
        result.sendResult(items);
    }

    private MediaBrowserCompat.MediaItem item(String id, String title, String url) {
        Bundle extras = new Bundle();
        extras.putString("url", url);
        MediaDescriptionCompat d = new MediaDescriptionCompat.Builder()
                .setMediaId(id)
                .setTitle(title)
                .setExtras(extras)
                .build();
        return new MediaBrowserCompat.MediaItem(d,
                MediaBrowserCompat.MediaItem.FLAG_PLAYABLE);
    }

    // -------------------------------------------------------------- controls

    private final class Callback extends MediaSessionCompat.Callback {

        @Override
        public void onPlayFromMediaId(String mediaId, Bundle extras) {
            String url = extras != null ? extras.getString("url") : null;
            if (url == null) url = Prefs.startUrl(YoutubeAudioService.this);
            ensureWeb();
            web.loadUrl(url);
        }

        @Override
        public void onPlay() {
            if (web == null) {
                onPlayFromMediaId("start", null);
                return;
            }
            js("var v=document.querySelector('video'); if(v) v.play();");
        }

        @Override
        public void onPause() {
            js("var v=document.querySelector('video'); if(v) v.pause();");
        }

        @Override
        public void onStop() {
            teardown();
            setState(PlaybackStateCompat.STATE_STOPPED);
            stopForeground(true);
        }

        @Override
        public void onSkipToNext() {
            // Seek forward 30s - more useful than "next" for lectures.
            js("var v=document.querySelector('video'); if(v) v.currentTime+=30;");
        }

        @Override
        public void onSkipToPrevious() {
            js("var v=document.querySelector('video'); if(v) v.currentTime-=30;");
        }

        @Override
        public void onSeekTo(long pos) {
            js("var v=document.querySelector('video'); if(v) v.currentTime=" + (pos / 1000) + ";");
        }
    }

    private void js(String code) {
        main.post(() -> {
            if (web != null) web.evaluateJavascript(code, null);
        });
    }

    // --------------------------------------------------------------- webview

    /**
     * The WebView must be attached to a window or Chromium will not run the media
     * pipeline. A 1x1 overlay is the standard trick - invisible, but "attached".
     */
    private void ensureWeb() {
        if (web != null) return;

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);

        web.addJavascriptInterface(new Bridge(), "RoadClass");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView v, String url) {
                v.evaluateJavascript(JS_WATCH, null);
            }
        });

        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                1, 1, type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;

        try {
            wm.addView(web, lp);
        } catch (Exception ignored) {
            // No overlay permission - playback may still work on some builds.
        }
    }

    private void teardown() {
        if (web == null) return;
        try {
            if (wm != null) wm.removeView(web);
        } catch (Exception ignored) {
        }
        web.destroy();
        web = null;
    }

    /** Injected JS calls back in here. */
    private final class Bridge {
        @JavascriptInterface
        public void onState(String state, String title) {
            main.post(() -> {
                boolean playing = "playing".equals(state);
                session.setActive(true);
                setMetadata(title);
                setState(playing
                        ? PlaybackStateCompat.STATE_PLAYING
                        : PlaybackStateCompat.STATE_PAUSED);
                if (playing) startForeground(NOTIF_ID, notification(title));
            });
        }
    }

    // ------------------------------------------------------- session plumbing

    private void setMetadata(String title) {
        session.setMetadata(new android.support.v4.media.MediaMetadataCompat.Builder()
                .putString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_TITLE,
                        title == null || title.isEmpty() ? "Road Class" : title)
                .build());
    }

    private void setState(int state) {
        session.setPlaybackState(new PlaybackStateCompat.Builder()
                .setActions(PlaybackStateCompat.ACTION_PLAY
                        | PlaybackStateCompat.ACTION_PAUSE
                        | PlaybackStateCompat.ACTION_PLAY_PAUSE
                        | PlaybackStateCompat.ACTION_STOP
                        | PlaybackStateCompat.ACTION_SEEK_TO
                        | PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                        | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                        | PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID)
                .setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1f)
                .build());
    }

    private Notification notification(String title) {
        NotificationManager nm =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm != null
                && nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(new NotificationChannel(
                    CHANNEL, "Playback", NotificationManager.IMPORTANCE_LOW));
        }

        PendingIntent pi = PendingIntent.getActivity(this, 0,
                new Intent(this, PhoneActivity.class),
                PendingIntent.FLAG_IMMUTABLE);

        return new androidx.core.app.NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(title == null || title.isEmpty() ? "Road Class" : title)
                .setContentIntent(pi)
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                        .setMediaSession(session.getSessionToken()))
                .build();
    }

    @Override
    public void onDestroy() {
        teardown();
        if (session != null) session.release();
        super.onDestroy();
    }
}
