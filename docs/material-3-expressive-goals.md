# Material 3 Expressive improvement goals

Reviewed 2026-11-08 against the current source and the resolved debug dependency graph. Anshin uses Compose Material 3 `1.5.0-beta01` with Expressive motion. The audit found zero handwritten XML screen layouts and zero Material 2 component dependencies. The work below addresses interaction behavior and redundant screens.

## 1. Make date and time entry correct and consistent

- [ ] Fix Health's date conversion first. Seed Material's picker with the record's local calendar date, read the selection as a `LocalDate`, and combine it with the existing local time in the record's display zone. Keep the existing timestamp when the user confirms the same day. [Health record sheet](../app/src/main/java/com/driezy/medlog/feature/health/main/HealthRecordSheet.kt) · [Medication date conversion](../app/src/main/java/com/driezy/medlog/feature/medications/editor/MedicationDateInput.kt)
- [ ] Share one Material time-entry control across Health, History, routine settings, and medication reminders. Use `TimePicker` and `TimeInput` with a shared draft and a mode switch. Respect the device's time format at the UI boundary and preserve the stored schedule format. Keep the reminder editor inline and use a dialog for focused time edits. [Routine editor](../app/src/main/java/com/driezy/medlog/ui/components/RoutineScheduleEditor.kt) · [Material time-picker guidance](https://developer.android.com/develop/ui/compose/components/time-pickers-dialogs)

**Done when** a date round-trips in Singapore and Los Angeles, including around local midnight and across daylight-saving boundaries. Both time-entry modes retain edits when switched, work in 12-hour and 24-hour formats, and discard drafts on Cancel or Back.

## 2. Give each control the behavior its appearance promises

- [ ] Replace empty-click chips on medication cards, History, and Diary with static labels styled from the theme. Give each removable reminder chip one accessible remove action. [Medication card](../app/src/main/java/com/driezy/medlog/ui/components/MedicationCard.kt) · [Reminder chips](../app/src/main/java/com/driezy/medlog/feature/medications/editor/AddMedicationFormComponents.kt)
- [ ] Use `SingleChoiceSegmentedButtonRow` for short, mutually exclusive choices such as theme mode and medication frequency. Use a selectable group with radio rows when long labels or supporting text need more room. Keep the theme palette swatches as selectable cards with group semantics. Use one labeled toggle target per settings switch row. [Appearance settings](../app/src/main/java/com/driezy/medlog/feature/settings/SettingsAppearanceContent.kt) · [Schedule choices](../app/src/main/java/com/driezy/medlog/feature/medications/editor/AddMedicationScheduleSections.kt) · [Settings rows](../app/src/main/java/com/driezy/medlog/feature/settings/SettingsRowsComponents.kt)

**Done when** passive labels expose no click action, every actionable chip has an effect, and TalkBack identifies the purpose and state of each choice. One tap on a switch row changes its state once. Labels fit or reflow at 200% font scale.

## 3. Consolidate overlapping screens and tasks

- [ ] Combine Intelligence and Cloud API configuration into one Settings destination. Keep a single cloud status and enable switch, with the provider, credentials, and model form in an expandable section. Retire the separate Cloud API screen after checking route callers and saved back-stack behavior. Preserve the existing opt-in, credential handling, and cloud analysis gates. [Intelligence settings](../app/src/main/java/com/driezy/medlog/feature/settings/SettingsIntelligenceContent.kt) · [Cloud API configuration](../app/src/main/java/com/driezy/medlog/feature/settings/CloudApiSettingsContent.kt)
- [ ] Make My Medications → Archived the home for archived medication management. Replace the duplicate restore list in Modules with a shortcut that opens the Archived filter directly. Keep Restore available from medication detail. [Settings archive list](../app/src/main/java/com/driezy/medlog/feature/settings/SettingsHomeModulesContent.kt) · [My Medications](../app/src/main/java/com/driezy/medlog/feature/medications/list/MyMedicationsScreen.kt)
- [ ] Share the camera scaffold, permission gate, capture states, processing, and retry across medication and health OCR. Reuse the medication editor's scan and custom-dose handlers for add and edit. Keep medication text selection and health result review specific to their tasks. [Medication OCR](../app/src/main/java/com/driezy/medlog/capability/ocr/OcrScannerPage.kt) · [Health OCR](../app/src/main/java/com/driezy/medlog/capability/ocr/HealthOcrScannerPage.kt) · [Medication editor](../app/src/main/java/com/driezy/medlog/feature/medications/editor/AddMedicationScreen.kt)

**Done when** the separate Cloud API screen and duplicate archive list are gone, every existing action remains reachable in its owning flow, and Back returns users to their previous context. Scan denial, cancellation, retry, and successful capture return to the correct draft without losing its edits.

## 4. Adopt Expressive components where they improve the flow

- [ ] Migrate the shared navigation wrapper to `currentWindowAdaptiveInfoV2()` and `NavigationSuiteScaffoldDefaults.navigationSuiteType()`. Use the matching short bar or wide rail while preserving destinations, feature flags, and back stacks. Keep system window breakpoints and touch targets independent of the app's compact density setting. [Navigation wrapper](../app/src/main/java/com/driezy/medlog/ui/navigation/MedLogNavigationComponents.kt) · [Navigation suite defaults](https://developer.android.com/reference/kotlin/androidx/compose/material3/adaptive/navigationsuite/NavigationSuiteScaffoldDefaults)
- [ ] Move the custom emphasized text styles into Material's built-in `Typography` roles using the existing Anshin font metrics. Keep the editorial number styles for the screens that need them. [Type definitions](../app/src/main/java/com/driezy/medlog/ui/theme/Type.kt) · [Material typography API](https://developer.android.com/reference/kotlin/androidx/compose/material3/Typography)

**Done when** compact phones, short landscape windows, and tablet or foldable windows show the appropriate navigation. Destination selection, scroll restoration, Back, and large-text labels work after a window resize. The typography migration preserves text hierarchy and readability in both themes.

## Component choices

| User intent | Component |
| --- | --- |
| Navigate to a primary destination | Adaptive short navigation bar or wide rail |
| Change one persistent on/off setting | Material list item with a labeled switch |
| Choose one of a few options | Single-choice segmented control or a group of radio rows |
| Filter visible results | Filter chips with the selection behavior reflected in semantics |
| Remove an entered value | Input chip with one labeled action |
| Read a status or category | Static text or a noninteractive surface |
| Confirm a short, consequential action | Alert dialog with explicit action labels |
| Edit related fields briefly | Modal bottom sheet with predictable Back and dismiss behavior |
| Complete a multi-step task or review captured data | Full screen with its own title and navigation |
| Wait for work or track measurable progress | Expressive loading indicator for waiting and determinate progress for known completion |

Keep the Android splash and launcher widget configuration entry points. CameraX's Compose viewfinder and generated Glance widget previews can be handled separately from screen consolidation.

## Verification

Run the affected unit and Compose tests, `./gradlew ktlintCheck test assembleDebug compileDebugAndroidTestKotlin`, and smoke-test the affected journeys on an installed debug build. Exercise dark and light themes, 200% font scale, TalkBack, compact and expanded windows, and labels in English, Simplified Chinese, Japanese, and Korean. Check add/edit medication, Health date/time editing, the archived-medication shortcut, and Intelligence with cloud AI off and on. Use sample data when testing scans and credential entry.

Baseline evidence comes from the previous emulator check of Health's dial-only picker, a separate reproduction of the timezone conversions, and inspection of the pinned Material 3 library. The acceptance checks above apply to future implementation.
