# Road Class

YouTube on the Android Auto head unit, for long-form learning content.

## Get the APK (5 minutes, no tools needed)

1. Go to <https://github.com/new>, name the repo `roadclass`, keep it **Private**, click
   **Create repository**.
2. On the empty repo page click **uploading an existing file**.
3. Drag the *contents* of this folder in (all of it, including the `.github` folder).
   Click **Commit changes**.
4. Open the **Actions** tab. A run called "Build APK" starts on its own. Wait ~3 minutes
   for the green tick.
5. Click the run, scroll to **Artifacts**, download **RoadClass-APK**. Unzip it.
6. Install `RoadClass.apk` on the phone.

If the `.github` folder does not upload (some browsers hide dot-folders), do this instead:
Actions tab -> **set up a workflow yourself** -> paste the contents of
`.github/workflows/build.yml` -> Commit.

## Then, on the phone

1. Open Road Class, tap **Allow display over other apps**, grant it.
2. Android Auto app -> tap the version string 10 times -> Developer Mode unlocked.
3. Overflow menu -> Developer settings -> **Unknown sources: ON**.
4. Reconnect to the car. Road Class shows up with the navigation apps.

## How it works

Android Auto's public API has no video surface. But *navigation* apps are handed a real
`Surface` to draw maps on. This app declares itself as a navigation app, takes that
surface, points a `VirtualDisplay` at it, and shows a `Presentation` hosting a WebView.

Because the content is ours rather than a screen capture, no MediaProjection consent is
needed, and head-unit taps are dispatched straight into our own view tree - no root, no
accessibility service, no input injection permissions. Every API used here is public and
documented, which is why this should outlive the older `CATEGORY_PROJECTION` trick that
Fermata and CarStream rely on.

## Caveats

- Google's policy does not permit video in a navigation template. Nothing enforces it in
  code, but this app will never be Play Store material. Personal sideload only.
- Android Auto's speed lockout is not consulted anywhere in this code. Playback will run
  while the car is moving. That is on you.
- Keyboard input on the head unit is poor. Set the URL on the phone.
- YouTube's web UI changes without notice and can break tap targets.
- If the car screen stays black, the overlay permission is almost always the cause.
