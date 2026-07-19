#!/usr/bin/env node
/*
 * Generates MedLog's local Material Symbols VectorDrawable set.
 *
 * Source: Google Fonts Icons / Material Symbols Rounded SVG endpoints.
 * Android Developers recommends Google Font Icons / Material Symbols XML from
 * the Android tab instead of the old Compose material-icons artifacts. This
 * script keeps MedLog on that runtime model by generating local VectorDrawable
 * XML files and a single Compose wrapper entry point.
 * The Google Fonts family zip URL:
 * https://fonts.google.com/download?family=Material+Symbols+Outlined|Material+Symbols+Rounded|Material+Symbols+Sharp
 * contains font assets for typography/ligature use. Android app icons should
 * stay as VectorDrawable XML so Compose can render them through painterResource
 * with normal Icon tinting and semantics.
 */

import fs from "node:fs";
import https from "node:https";
import path from "node:path";
import { fileURLToPath } from "node:url";

const ICONS = [
  ["AccessAlarm", "access_alarm"],
  ["AccessTime", "access_time"],
  ["Add", "add"],
  ["AddToHomeScreen", "add_to_home_screen"],
  ["Adjust", "adjust"],
  ["AirlineStops", "airline_stops"],
  ["AlarmAdd", "alarm_add"],
  ["Archive", "archive"],
  ["ArrowBack", "arrow_back"],
  ["ArrowForward", "arrow_forward"],
  ["AutoAwesome", "auto_awesome"],
  ["Bedtime", "bedtime"],
  ["Bloodtype", "bloodtype"],
  ["BreakfastDining", "breakfast_dining"],
  ["Brightness5", "brightness_5"],
  ["CalendarMonth", "calendar_month"],
  ["CameraAlt", "photo_camera"],
  ["Cancel", "cancel"],
  ["Category", "category"],
  ["CenterFocusStrong", "center_focus_strong"],
  ["Check", "check"],
  ["CheckCircle", "check_circle"],
  ["ChevronRight", "chevron_right"],
  ["Close", "close"],
  ["CloudUpload", "cloud_upload"],
  ["Coffee", "coffee"],
  ["ColorLens", "color_lens"],
  ["DarkMode", "dark_mode"],
  ["DateRange", "date_range"],
  ["Delete", "delete"],
  ["DinnerDining", "dinner_dining"],
  ["DocumentScanner", "document_scanner"],
  ["DoneAll", "done_all"],
  ["Edit", "edit"],
  ["EditNote", "edit_note"],
  ["EventRepeat", "event_repeat"],
  ["ExpandLess", "expand_less"],
  ["ExpandMore", "expand_more"],
  ["Favorite", "favorite"],
  ["FitnessCenter", "fitness_center"],
  ["FlashOff", "flash_off"],
  ["FlashOn", "flash_on"],
  ["FlightTakeoff", "flight_takeoff"],
  ["Healing", "healing"],
  ["History", "history"],
  ["Home", "home"],
  ["HourglassBottom", "hourglass_bottom"],
  ["Info", "info"],
  ["Inventory", "inventory"],
  ["Inventory2", "inventory_2"],
  ["IosShare", "ios_share"],
  ["LightMode", "light_mode"],
  ["LocalDrink", "local_drink"],
  ["LocalFlorist", "local_florist"],
  ["LunchDining", "lunch_dining"],
  ["MedicalServices", "medical_services"],
  ["Medication", "medication"],
  ["Memory", "memory"],
  ["Mic", "mic"],
  ["Monitor", "monitor"],
  ["MonitorHeart", "monitor_heart"],
  ["MonitorWeight", "monitor_weight"],
  ["MoreHoriz", "more_horiz"],
  ["MoreVert", "more_vert"],
  ["NightsStay", "nights_stay"],
  ["Notes", "notes"],
  ["NotificationAdd", "notification_add"],
  ["Notifications", "notifications"],
  ["NotificationsActive", "notifications_active"],
  ["NotificationsOff", "notifications_off"],
  ["OpenInNew", "open_in_new"],
  ["Palette", "palette"],
  ["PanTool", "pan_tool"],
  ["PriorityHigh", "priority_high"],
  ["QrCode2", "qr_code_2"],
  ["QrCodeScanner", "qr_code_scanner"],
  ["Refresh", "refresh"],
  ["Remove", "remove"],
  ["Replay", "replay"],
  ["Schedule", "schedule"],
  ["Science", "science"],
  ["Search", "search"],
  ["SearchOff", "search_off"],
  ["Settings", "settings"],
  ["SkipNext", "skip_next"],
  ["Speed", "speed"],
  ["Storage", "storage"],
  ["Thermostat", "thermostat"],
  ["Timer", "timer"],
  ["Today", "today"],
  ["TouchApp", "touch_app"],
  ["TrendingUp", "trending_up"],
  ["Tune", "tune"],
  ["Undo", "undo"],
  ["UnfoldLess", "unfold_less"],
  ["Upload", "upload"],
  ["VerifiedUser", "verified_user"],
  ["ViewAgenda", "view_agenda"],
  ["Visibility", "visibility"],
  ["VisibilityOff", "visibility_off"],
  ["Warning", "warning"],
  ["WaterDrop", "water_drop"],
  ["WbSunny", "wb_sunny"],
  ["Widgets", "widgets"],
];

const SELECTED_ICONS = [
  ["HomeSelected", "home"],
  ["HistorySelected", "history"],
  ["MedicalServicesSelected", "medical_services"],
  ["EditNoteSelected", "edit_note"],
  ["MonitorHeartSelected", "monitor_heart"],
  ["SettingsSelected", "settings"],
];

const DISPLAY_ICONS = [
  ["MedicationDisplay40", "medication", 40],
  ["RefreshDisplay48", "refresh", 48],
  ["MonitorHeartDisplay48", "monitor_heart", 48],
  ["SearchOffDisplay48", "search_off", 48],
  ["CheckCircleDisplay48", "check_circle", 48],
];

const STYLE = "materialsymbolsrounded";
const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const DRAWABLE_DIR = path.join(ROOT, "app/src/main/res/drawable");
const ICON_ENTRY = path.join(ROOT, "app/src/main/java/com/driezy/medlog/ui/icons/MedLogIcons.kt");

function fetchText(url) {
  return new Promise((resolve, reject) => {
    const request = https.get(url, (response) => {
      let data = "";
      response.setEncoding("utf8");
      response.on("data", (chunk) => {
        data += chunk;
      });
      response.on("end", () => {
        if (response.statusCode !== 200) {
          reject(new Error(`${response.statusCode} ${url}`));
          return;
        }
        resolve(data);
      });
    });
    request.on("error", reject);
    request.setTimeout(20_000, () => request.destroy(new Error(`timeout ${url}`)));
  });
}

async function fetchWithRetry(url) {
  let lastError;
  for (let attempt = 1; attempt <= 5; attempt += 1) {
    try {
      return await fetchText(url);
    } catch (error) {
      lastError = error;
      await new Promise((resolve) => setTimeout(resolve, 400 * attempt));
    }
  }
  throw lastError;
}

function escapeXml(value) {
  return value
    .replaceAll("&", "&amp;")
    .replaceAll('"', "&quot;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;");
}

function svgPaths(svg) {
  return Array.from(svg.matchAll(/<path\s+d="([^"]+)"/g), (match) => match[1]);
}

function vectorXml({ symbolName, paths, sizeDp = 24, variantLabel = "baseline" }) {
  const pathXml = paths
    .map(
      (pathData) => `        <path
            android:fillColor="#000000"
            android:pathData="${escapeXml(pathData)}" />`,
    )
    .join("\n");

  return `<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    android:width="${sizeDp}dp"
    android:height="${sizeDp}dp"
    android:viewportWidth="960"
    android:viewportHeight="960"
    tools:ignore="VectorPath">
    <!-- Generated by scripts/update_material_symbols.mjs from Google Fonts Material Symbols Rounded: ${symbolName}, ${variantLabel}. -->
    <group android:translateY="960">
${pathXml}
    </group>
</vector>
`;
}

function medLogIconsKt(entries, selectedEntries, displayEntries) {
  const baseConstants = entries
    .map(([propertyName, symbolName]) => {
      return `    @DrawableRes val ${propertyName}: Int = R.drawable.ic_symbol_${symbolName}`;
    })
    .join("\n\n");
  const selectedConstants = selectedEntries
    .map(([propertyName, symbolName]) => {
      return `    @DrawableRes val ${propertyName}: Int = R.drawable.ic_symbol_${symbolName}_fill1`;
    })
    .join("\n\n");
  const displayConstants = displayEntries
    .map(([propertyName, symbolName, sizeDp]) => {
      return `    @DrawableRes val ${propertyName}: Int = R.drawable.ic_symbol_${symbolName}_opsz${sizeDp}`;
    })
    .join("\n\n");
  const constants = [baseConstants, selectedConstants, displayConstants].filter(Boolean).join("\n\n");

  return `@file:Suppress("FunctionName")

package com.driezy.medlog.ui.icons

// Generated by scripts/update_material_symbols.mjs.
// Source: Google Fonts Material Symbols Rounded VectorDrawable-compatible paths.
// Baseline icons use 24dp. Selected navigation icons use fill=1 variants.
// Display icons use optical-size-specific 40dp or 48dp variants.

import androidx.annotation.DrawableRes
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.driezy.medlog.R
import androidx.compose.material3.Icon as MaterialIcon

object MedLogIcons {
${constants}
}

@Composable
fun MedLogIcon(
    @DrawableRes icon: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    MaterialIcon(
        painter = painterResource(icon),
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint,
    )
}
`;
}

async function writeSymbolDrawable({ symbolName, pathSuffix = "", urlVariant = "default", sizeDp = 24, variantLabel = "baseline" }) {
  const url = `https://fonts.gstatic.com/s/i/short-term/release/${STYLE}/${symbolName}/${urlVariant}/${sizeDp}px.svg`;
  const svg = await fetchWithRetry(url);
  const paths = svgPaths(svg);
  if (paths.length === 0) {
    throw new Error(`No SVG path found for ${symbolName}`);
  }
  const drawablePath = path.join(DRAWABLE_DIR, `ic_symbol_${symbolName}${pathSuffix}.xml`);
  fs.writeFileSync(drawablePath, vectorXml({ symbolName, paths, sizeDp, variantLabel }));
}

async function main() {
  fs.mkdirSync(DRAWABLE_DIR, { recursive: true });

  for (const [, symbolName] of ICONS) {
    await writeSymbolDrawable({ symbolName });
  }

  for (const [, symbolName] of SELECTED_ICONS) {
    await writeSymbolDrawable({
      symbolName,
      pathSuffix: "_fill1",
      urlVariant: "fill1",
      variantLabel: "selected fill=1 state",
    });
  }

  for (const [, symbolName, sizeDp] of DISPLAY_ICONS) {
    await writeSymbolDrawable({
      symbolName,
      pathSuffix: `_opsz${sizeDp}`,
      sizeDp,
      variantLabel: `optical size ${sizeDp}dp display variant`,
    });
  }

  fs.writeFileSync(ICON_ENTRY, medLogIconsKt(ICONS, SELECTED_ICONS, DISPLAY_ICONS));
  console.log(`Generated ${ICONS.length + SELECTED_ICONS.length + DISPLAY_ICONS.length} Material Symbols drawable XML files.`);
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
