package com.nilaweera.aalearn;

import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.car.app.AppManager;
import androidx.car.app.CarAppService;
import androidx.car.app.CarContext;
import androidx.car.app.Screen;
import androidx.car.app.Session;
import androidx.car.app.SessionInfo;
import androidx.car.app.SurfaceCallback;
import androidx.car.app.SurfaceContainer;
import androidx.car.app.model.Action;
import androidx.car.app.model.ActionStrip;
import androidx.car.app.model.CarIcon;
import androidx.car.app.model.Template;
import androidx.car.app.navigation.model.NavigationTemplate;
import androidx.car.app.validation.HostValidator;
import androidx.core.graphics.drawable.IconCompat;

/**
 * The car-side entry point.
 *
 * This is a normal Car App Library service declared as a navigation app. Nav apps
 * are the only category handed a real drawing Surface (intended for maps) - we
 * render a WebView onto it instead. Everything here is public, documented API.
 */
public class CarWebService extends CarAppService {

    @NonNull
    @Override
    public HostValidator createHostValidator() {
        // Sideloaded builds are not signed by a host-trusted key, so during
        // development every host must be allowed. Tighten this if you ever
        // ship the app to anyone else.
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR;
    }

    @NonNull
    @Override
    public Session onCreateSession(@NonNull SessionInfo sessionInfo) {
        return new Session() {
            @NonNull
            @Override
            public Screen onCreateScreen(@NonNull Intent intent) {
                return new WebScreen(getCarContext());
            }
        };
    }

    /** Owns the surface lifecycle and forwards head-unit gestures. */
    private static final class WebScreen extends Screen implements SurfaceCallback {

        private final CarDisplay carDisplay;

        WebScreen(@NonNull CarContext ctx) {
            super(ctx);
            carDisplay = new CarDisplay(ctx);
            ctx.getCarService(AppManager.class).setSurfaceCallback(this);
        }

        @Override
        public void onSurfaceAvailable(@NonNull SurfaceContainer sc) {
            carDisplay.attach(sc);
        }

        @Override
        public void onSurfaceDestroyed(@NonNull SurfaceContainer sc) {
            carDisplay.detach();
        }

        @Override
        public void onClick(float x, float y) {
            carDisplay.tap(x, y);
        }

        @Override
        public void onScroll(float distanceX, float distanceY) {
            carDisplay.scroll(distanceX, distanceY);
        }

        @Override
        public void onFling(float velocityX, float velocityY) {
            carDisplay.endDrag();
        }

        @NonNull
        @Override
        public Template onGetTemplate() {
            Action back = new Action.Builder()
                    .setIcon(new CarIcon.Builder(
                            IconCompat.createWithResource(getCarContext(),
                                    android.R.drawable.ic_media_previous)).build())
                    .setOnClickListener(carDisplay::goBack)
                    .build();

            Action home = new Action.Builder()
                    .setIcon(new CarIcon.Builder(
                            IconCompat.createWithResource(getCarContext(),
                                    android.R.drawable.ic_menu_revert)).build())
                    .setOnClickListener(carDisplay::goHome)
                    .build();

            return new NavigationTemplate.Builder()
                    .setActionStrip(new ActionStrip.Builder()
                            .addAction(back)
                            .addAction(home)
                            .build())
                    .setMapActionStrip(new ActionStrip.Builder()
                            .addAction(Action.PAN)
                            .build())
                    .build();
        }
    }
}
