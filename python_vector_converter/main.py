"""Entry point: regenerate launcher icon Drawable XMLs from source PNGs."""

from convert import png_to_vector_drawable
import os

# ─── Absolute paths (adjust if the Gemini temp dir changes) ─────────────────
GEMINI_DIR = os.path.expanduser(
    "~/.gemini/antigravity/brain/1ea57815-64c6-4323-834a-105ca1988e6b"
)
PROJECT_RES = os.path.expanduser(
    "~/Downloads/MedLogAndroid/app/src/main/res/drawable"
)

FG_PNG = os.path.join(GEMINI_DIR, "app_icon_foreground_1771770509808.png")
OUT_FG = os.path.join(PROJECT_RES, "ic_launcher_foreground.xml")
OUT_MONO = os.path.join(PROJECT_RES, "ic_launcher_monochrome.xml")
OUT_BG = os.path.join(PROJECT_RES, "ic_launcher_background.xml")

BG_XML = """\
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
  <path
      android:pathData="M0,0h108v108h-108z">
    <aapt:attr name="android:fillColor">
      <gradient
          android:startX="0"
          android:startY="0"
          android:endX="108"
          android:endY="108"
          android:type="linear">
        <item android:offset="0" android:color="#FFB3A6F0" />
        <item android:offset="0.5" android:color="#FFD1C4E9" />
        <item android:offset="1" android:color="#FFAEEED3" />
      </gradient>
    </aapt:attr>
  </path>
</vector>
"""


def main() -> None:
    # Foreground (white paths on transparent → coloured at runtime)
    png_to_vector_drawable(
        FG_PNG, OUT_FG,
        fill_color="@color/ic_launcher_foreground",
        viewport_size=108,
    )

    # Monochrome copy (black fill for adaptive icon monochrome layer)
    png_to_vector_drawable(
        FG_PNG, OUT_MONO,
        fill_color="#FF000000",
        viewport_size=108,
    )

    # Background: gradient XML written directly (raster tracing is bad for gradients)
    print(f"Writing gradient background → {OUT_BG}")
    with open(OUT_BG, "w", encoding="utf-8") as f:
        f.write(BG_XML)
    print("  Done.")


if __name__ == "__main__":
    main()
