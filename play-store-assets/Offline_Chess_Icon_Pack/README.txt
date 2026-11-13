OFFLINE CHESS — ICON PACK

Contents
--------
1. Play_Store_Icon_512x512.png
   Google Play Store listing icon: 512 x 512 PNG.

2. Android_Launcher/res/
   Legacy Android launcher PNGs:
   mdpi 48x48, hdpi 72x72, xhdpi 96x96,
   xxhdpi 144x144, xxxhdpi 192x192.
   Copy these folders into app/src/main/res/.

3. Extra_Sizes/
   Additional 256x256, 384x384 and 1024x1024 exports.

4. Optional_Feature_Icons/
   Optional decorative UI tiles for the app's menu.

IMPORTANT
---------
- Google Play listing icon requirement: 512x512 PNG.
- The Play Store icon is separate from Android launcher icons.
- For modern Android launchers, create/provide a proper adaptive icon
  (foreground artwork with transparent background + background layer)
  and optionally a monochrome layer for themed icons. This pack currently
  includes raster launcher sizes; adaptive foreground/background assets
  should be integrated separately rather than using a flattened store icon.
- Review the icon at small sizes and ensure it remains recognizable.
- The generated artwork includes a rounded-square visual treatment. Android
  launchers may apply their own icon mask, so adjust the artwork if needed.
