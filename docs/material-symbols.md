# Material Symbols Usage

MedLog uses Material Symbols as local Android VectorDrawable XML, rendered from
Compose with `painterResource()` through `MedLogIcon`.

## Source

- Browse icons at <https://fonts.google.com/icons>.
- Reference implementation notes: <https://developers.google.com/fonts/docs/material_symbols>.
- Android Compose icon guidance: <https://developer.android.com/develop/ui/compose/graphics/images/material>.

The Android guidance is the important part for this project: do not use the old
Compose `material-icons` artifacts for app UI. Use the Material Symbols library
and keep the app icons as Android VectorDrawable XML, equivalent to copying an
icon from the Google Fonts Icons Android tab into `res/drawable`.

The Google Fonts family download URL for Material Symbols Outlined, Rounded,
and Sharp returns font assets for typography/ligature use:

```text
https://fonts.google.com/download?family=Material+Symbols+Outlined|Material+Symbols+Rounded|Material+Symbols+Sharp
```

For MedLog app icons, we do not ship or render the symbol font. Android UI
icons need normal Compose tinting, accessibility labels, and deterministic
offline rendering, so the maintained source is local VectorDrawable XML.

## Update Workflow

Run:

```bash
node scripts/update_material_symbols.mjs
```

The script fetches Material Symbols Rounded SVG paths from Google Fonts icon
endpoints, converts them to Android VectorDrawable XML, writes
`app/src/main/res/drawable/ic_symbol_*.xml`, and regenerates `MedLogIcons.kt`.
This is a batchable form of the same asset model recommended by the Android tab:
local XML drawables, not a runtime font dependency.

The generated set includes three resource classes:

- Baseline icons: 24dp `default` symbols for ordinary UI.
- Selected navigation icons: 24dp `fill=1` variants for current top-level destinations.
- Display icons: 40dp or 48dp optical-size variants for larger brand, empty-state, and hero moments.

After updating icons, run:

```bash
./gradlew :app:testDebugUnitTest --tests 'com.driezy.medlog.ui.icons.MaterialSymbolsMigrationGuardTest'
./gradlew :app:compileDebugKotlin :app:lintDebug
```

## Rules

- Use `MedLogIcon(icon = MedLogIcons.Name, ...)`.
- Do not import `androidx.compose.material.icons`.
- Do not add `material-icons-extended`.
- Do not render Symbols as text ligatures in app UI.
- Do not add a fake `Icon(imageVector: Int)` overload.
- Use selected `fill=1` variants for top-level navigation selected states.
- Use 40dp or 48dp optical-size variants when a symbol is intentionally displayed at those sizes.
