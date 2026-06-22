# UI Design System Notes

This document records durable UI decisions for MedLog. It is not a work log.
Update it when a design rule changes, when a new reusable pattern is adopted,
or when a guard test is added to protect an architectural choice.

## Material 3 Expressive

- MedLog is a Compose Material 3 app. Expressive behavior should be implemented
  through theme, component, motion, typography, and color decisions, not through
  one-off decoration.
- The app theme uses `MotionScheme.expressive()`. Custom motion should read
  spatial specs for shape, bounds, position, and size changes, and effects specs
  for alpha or color changes.
- Avoid raw duration/easing constants and bare Compose transition specs in
  shared UI. `MotionSystemGuardTest` protects this rule.
- Shape theming is centralized through `MedLogShapes`, aligned to the Material 3
  role scale: extraSmall `4dp`, small `8dp`, medium `12dp`, large `16dp`, and
  extraLarge `28dp`.
- Component-level shape overrides are allowed when they express a local surface
  role. Repeated overrides should become semantic helpers instead of ad hoc
  `RoundedCornerShape(...)` calls.

## Color Roles

- Compose screens should use `MaterialTheme.colorScheme` and matching
  container/on-container role pairs.
- Dynamic color is enabled through Compose dynamic color APIs when supported by
  the platform and user setting.
- Static fallback palettes must define the expanded Material 3 role table,
  including surface container roles, bright/dim surfaces, scrim, surface tint,
  and fixed accent roles.
- Non-Compose surfaces, including notifications and Glance widgets, should use
  project-owned bridges from the same MedLog palette instead of launcher colors
  or default widget colors.
- Hardcoded colors are limited to cases with a documented platform or rendering
  reason. Audited custom colors such as warning states, chart labels, and OCR
  scrims should use Material roles.

## Icons

- App icons use local Material Symbols VectorDrawable XML rendered through
  `MedLogIcon(icon = MedLogIcons.Name, ...)`.
- The old Compose Material Icons artifacts are not used. See
  [material-symbols.md](material-symbols.md) for the source and update workflow.
- Selected top-level navigation uses `fill=1` symbol variants.
- Symbols displayed at 40dp or 48dp use optical-size-specific resources instead
  of scaled 24dp paths.

## Carousel Usage

Use Material 3 carousels only for compact visual previews or summaries. Do not
turn dense task, record, or filter controls into carousels.

Approved uses:

- Health metrics summary: `HorizontalUncontainedCarousel`.
- Settings widget previews: `HorizontalCenteredHeroCarousel`.

Non-uses:

- Medication tasks, PRN records, low-stock warnings, interaction warnings,
  health records, history logs, medication detail logs, symptom diary entries,
  and filter chips.
- Welcome onboarding remains a pager because it contains setup steps, toggles,
  and permissions rather than compact visual media.

Every carousel on a vertical page needs another path to the complete content,
such as a full list or show-all surface.

## Editorial Treatment

Editorial type is reserved for standalone showcase moments. It is not a
replacement for labels, navigation, list rows, chips, settings rows, or routine
form surfaces.

The approved MedLog editorial moment is Home's today-progress surface:

- `EditorialTypography` provides the tokens.
- `EditorialProgressMoment` shows the progress numeral while work is in
  progress and a localized completion word when all planned doses are done.
- Motion uses the app motion scheme and color uses Material roles.

## Top-Level Screens

- Top-level pages use predictable app bars and primary actions. The main page
  action should be a labeled `ExtendedFloatingActionButton` when text plus an
  icon is needed to identify the action.
- The scanner capture button is an in-context camera control and may use the
  dedicated capture FAB styling instead of the page-level FAB pattern.
- Settings groups should stay consolidated around user concerns rather than
  presenting every setting as a peer card. Current primary containers are
  Appearance, Reminders, OCR & Health, Widgets, and Data & About.

## Verification

For UI-system changes, run focused guard tests first, then normal Android
verification:

```bash
./gradlew :app:testDebugUnitTest --tests '*GuardTest'
./gradlew :app:ktlintCheck :app:testDebugUnitTest :app:assembleDebug
```

Run Android lint as a separate verification step when changing resources,
themes, or XML-driven behavior. If lint stalls in the local toolchain, record it
as a tooling-performance issue instead of folding unrelated UI changes into the
same investigation.
