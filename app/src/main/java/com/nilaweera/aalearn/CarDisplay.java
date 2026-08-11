package com.nilaweera.aalearn;

import android.app.Presentation;
import android.content.Context;
import android.graphics.Color;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import androidx.car.app.SurfaceContainer;

/**
 * Renders the WebView onto the head unit.
 *
 * We own a VirtualDisplay whose output Surface is the one Android Auto handed us
 * for the navigation template. A Presentation on that display hosts the WebView.
 *
 * Because the content is ours, no MediaProjection consent is needed
 * (OWN_CONTENT_ONLY, not a screen capture) and touch events can be dispatched
 * straight into our own view tree - no INJECT_EVENTS, no accessibility service,
 * no root. That is the whole reason this design beats mirroring another app.
 */
public class CarDisplay {

    private static final String UA_TV =
            "Mozilla/5.0 (X11; Linux armv7l) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/120.0.0.0 Safari/537.36 CrKey/1.54.250320";

    private final Context ctx;
    private VirtualDisplay display;
    private WebPresentation presentation;

    /** Tracks a synthetic drag so scroll gestures look like a real finger. */
    private long downTime;
    private float lastX;
    private float lastY;
    private boolean dragging;

    public CarDisplay(Context ctx) {
        this.ctx = ctx;
    }

    // ---------------------------------------------------------------- surface

    public void attach(SurfaceContainer sc) {
        if (sc.getSurface() == null) return;
        detach();

        DisplayManager dm = (DisplayManager) ctx.getSystemService(Context.DISPLAY_SERVICE);
        if (dm == null) return;

        int dpi = sc.getDpi() > 0 ? sc.getDpi() : 160;

        display = dm.createVirtualDisplay(
                "RoadClass",
                Math.max(sc.getWidth(), 1),
                Math.max(sc.getHeight(), 1),
                dpi,
                sc.getSurface(),
                DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
                        | DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION);

        if (display == null) return;

        presentation = new WebPresentation(ctx, display.getDisplay());
        // A Presentation shown from a Service is a non-activity window.
        presentation.getWindow().setType(
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        presentation.show();
    }

    public void detach() {
        if (presentation != null) {
            presentation.dismiss();
            presentation = null;
        }
        if (display != null) {
            display.release();
            display = null;
        }
        dragging = false;
    }

    // ----------------------------------------------------------------- input

    /** Single tap from the head unit's touchscreen. */
    public void tap(float x, float y) {
        endDrag();
        long t = SystemClock.uptimeMillis();
        send(t, t, MotionEvent.ACTION_DOWN, x, y);
        send(t, t + 40, MotionEvent.ACTION_UP, x, y);
    }

    /**
     * Android Auto reports scrolls as deltas, not positions, so we synthesise a
     * drag: DOWN once, then MOVE per delta, and UP when the gesture goes quiet.
     */
    public void scroll(float distanceX, float distanceY) {
        long t = SystemClock.uptimeMillis();
        if (!dragging) {
            dragging = true;
            downTime = t;
            lastX = width() / 2f;
            lastY = height() / 2f;
            send(downTime, t, MotionEvent.ACTION_DOWN, lastX, lastY);
        }
        lastX -= distanceX;
        lastY -= distanceY;
        send(downTime, t, MotionEvent.ACTION_MOVE, lastX, lastY);
    }

    /** Call when the gesture stream stops. */
    public void endDrag() {
        if (!dragging) return;
        dragging = false;
        send(downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, lastX, lastY);
    }

    private void send(long down, long event, int action, float x, float y) {
        if (presentation == null) return;
        View decor = presentation.getWindow().getDecorView();
        MotionEvent ev = MotionEvent.obtain(down, event, action, x, y, 0);
        ev.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
        decor.dispatchTouchEvent(ev);
        ev.recycle();
    }

    private int width() {
        return display == null ? 0 : display.getDisplay().getWidth();
    }

    private int height() {
        return display == null ? 0 : display.getDisplay().getHeight();
    }

    // ------------------------------------------------------------ navigation

    public void goBack() {
        if (presentation != null) presentation.goBack();
    }

    public void goHome() {
        if (presentation != null) presentation.goHome();
    }

    // ---------------------------------------------------------- presentation

    /** The window that actually lives on the car display. */
    private static class WebPresentation extends Presentation {

        private FrameLayout root;
        private WebView web;
        private View customView;
        private WebChromeClient.CustomViewCallback customViewCallback;

        WebPresentation(Context outer, android.view.Display display) {
            super(outer, display);
        }

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            Context c = getContext();
            root = new FrameLayout(c);
            root.setBackgroundColor(Color.BLACK);

            web = new WebView(c);
            web.setBackgroundColor(Color.BLACK);

            WebSettings s = web.getSettings();
            s.setJavaScriptEnabled(true);
            s.setDomStorageEnabled(true);
            s.setDatabaseEnabled(true);
            s.setMediaPlaybackRequiresUserGesture(false);
            s.setLoadWithOverviewMode(true);
            s.setUseWideViewPort(true);
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);

            String url = Prefs.startUrl(c);
            if (url.contains("/tv")) s.setUserAgentString(UA_TV);

            web.setWebViewClient(new WebViewClient() {
                @Override
                public boolean shouldOverrideUrlLoading(WebView v, String u) {
                    return false;   // stay inside the WebView
                }
            });

            // HTML5 fullscreen. Without this the video stays boxed in the page.
            web.setWebChromeClient(new WebChromeClient() {
                @Override
                public void onShowCustomView(View view, CustomViewCallback cb) {
                    if (customView != null) {
                        cb.onCustomViewHidden();
                        return;
                    }
                    customView = view;
                    customViewCallback = cb;
                    web.setVisibility(View.GONE);
                    root.addView(customView, new FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT));
                }

                @Override
                public void onHideCustomView() {
                    if (customView == null) return;
                    root.removeView(customView);
                    customView = null;
                    web.setVisibility(View.VISIBLE);
                    if (customViewCallback != null) {
                        customViewCallback.onCustomViewHidden();
                        customViewCallback = null;
                    }
                }
            });

            root.addView(web, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            setContentView(root);

            web.loadUrl(url);
        }

        void goBack() {
            if (customView != null) {
                web.getWebChromeClient().onHideCustomView();
                return;
            }
            if (web != null && web.canGoBack()) web.goBack();
        }

        void goHome() {
            if (web != null) web.loadUrl(Prefs.startUrl(getContext()));
        }

        @Override
        public void dismiss() {
            if (web != null) {
                web.loadUrl("about:blank");
                web.destroy();
                web = null;
            }
            super.dismiss();
        }
    }
}
