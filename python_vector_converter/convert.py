import cv2
import numpy as np
import xml.etree.ElementTree as ET
from xml.dom import minidom


# ─── Smooth curve helpers ───────────────────────────────────────────────────

def _catmull_rom_to_bezier_path(points: np.ndarray) -> str:
    """Convert a closed polygon (Nx2 float array) to a smooth SVG path.

    Uses the Catmull-Rom → cubic Bézier conversion, which guarantees C¹
    continuity (tangent-smooth joins) at every anchor point.

    Segment from P[i] to P[i+1]:
        CP1 = P[i]   + (P[i+1] - P[i-1]) / 6
        CP2 = P[i+1] - (P[i+2] - P[i]  ) / 6
    """
    n = len(points)
    if n < 2:
        return ""

    def fmt(v: float) -> str:
        # Round to 2 dp and drop trailing zeros for compact output
        s = f"{v:.2f}".rstrip("0").rstrip(".")
        return s if s else "0"

    path = f"M{fmt(points[0][0])},{fmt(points[0][1])} "

    for i in range(n):
        p_prev = points[(i - 1) % n]
        p_curr = points[i]
        p_next = points[(i + 1) % n]
        p_next2 = points[(i + 2) % n]

        cp1 = p_curr + (p_next - p_prev) / 6.0
        cp2 = p_next - (p_next2 - p_curr) / 6.0

        path += (
            f"C{fmt(cp1[0])},{fmt(cp1[1])} "
            f"{fmt(cp2[0])},{fmt(cp2[1])} "
            f"{fmt(p_next[0])},{fmt(p_next[1])} "
        )

    path += "Z "
    return path


# ─── Main converter ─────────────────────────────────────────────────────────

def png_to_vector_drawable(
    png_path: str,
    xml_path: str,
    fill_color: str = "#FFFFFFFF",
    viewport_size: int = 108,
    *,
    # Epsilon as a fraction of perimeter (higher → fewer anchor points → smoother)
    simplify_epsilon_ratio: float = 0.015,
    # Minimum contour area as fraction of total image area (filters noise)
    min_area_ratio: float = 0.001,
) -> None:
    """Convert a PNG/WEBP to an Android Vector Drawable with smooth Bézier paths.

    Improvements over the naïve approach:
    - Traces at **original image resolution** to preserve fine detail, then
      scales coordinates to the viewport after simplification.
    - Applies a slight Gaussian blur before thresholding to anti-alias rough
      pixel edges in the source bitmap.
    - Replaces straight-line (L) segments with smooth **cubic Bézier** (C)
      curves via Catmull-Rom spline conversion — ensuring C¹ continuity at
      every anchor.
    """
    print(f"Converting {png_path} → {xml_path}")

    # 1. Read at full resolution
    img = cv2.imread(png_path, cv2.IMREAD_UNCHANGED)
    if img is None:
        print(f"  ERROR: Could not read {png_path}")
        return

    # 2. Build binary mask
    if img.ndim == 3 and img.shape[2] == 4:
        # RGBA: use alpha channel as the mask directly.
        alpha = img[:, :, 3]
        blurred = cv2.GaussianBlur(alpha, (5, 5), 0)
        _, thresh = cv2.threshold(blurred, 127, 255, cv2.THRESH_BINARY)
    else:
        # BGR / RGB without alpha: dark shapes on white background.
        # Invert so shapes become white (255 = filled) and background black (0).
        gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
        blurred = cv2.GaussianBlur(gray, (5, 5), 0)
        _, thresh = cv2.threshold(blurred, 0, 255, cv2.THRESH_BINARY_INV + cv2.THRESH_OTSU)

    orig_h, orig_w = thresh.shape[:2]
    scale_x = viewport_size / orig_w
    scale_y = viewport_size / orig_h

    # 3. Find contours at original resolution
    contours, _ = cv2.findContours(thresh, cv2.RETR_TREE, cv2.CHAIN_APPROX_NONE)
    if not contours:
        print("  WARNING: No contours found.")
        return

    # 4. Filter out noise (very small contours)
    min_area = orig_w * orig_h * min_area_ratio
    contours = [c for c in contours if cv2.contourArea(c) >= min_area]
    if not contours:
        print("  WARNING: All contours below min_area threshold.")
        return

    # 5. Build smooth path for each contour
    path_data_parts: list[str] = []
    for contour in contours:
        # Scale coordinates to viewport before simplification
        pts = contour.astype(float)
        pts[:, 0, 0] *= scale_x
        pts[:, 0, 1] *= scale_y

        # Simplify: Douglas-Peucker with a generous epsilon to keep curve-defining
        # anchor points without keeping pixel-step noise
        arc_len = cv2.arcLength(pts.astype(np.float32), True)
        epsilon = simplify_epsilon_ratio * arc_len
        simplified = cv2.approxPolyDP(pts.astype(np.float32), epsilon, True)

        if len(simplified) < 2:
            continue

        points_2d = simplified[:, 0, :]  # shape (N, 2)
        path_data_parts.append(_catmull_rom_to_bezier_path(points_2d))

    if not path_data_parts:
        print("  WARNING: No usable paths generated.")
        return

    path_data = " ".join(path_data_parts).strip()

    # 6. Build Android Vector Drawable XML
    vector = ET.Element("vector", {
        "xmlns:android": "http://schemas.android.com/apk/res/android",
        "android:width": f"{viewport_size}dp",
        "android:height": f"{viewport_size}dp",
        "android:viewportWidth": str(viewport_size),
        "android:viewportHeight": str(viewport_size),
    })
    ET.SubElement(vector, "path", {
        "android:fillColor": fill_color,
        "android:pathData": path_data,
        "android:fillType": "evenOdd",
    })

    # 7. Serialise with pretty-print
    xml_str = ET.tostring(vector, encoding="utf-8")
    pretty_xml = minidom.parseString(xml_str).toprettyxml(indent="    ")
    pretty_xml = "\n".join(line for line in pretty_xml.splitlines() if line.strip())
    pretty_xml = pretty_xml.replace(
        "<?xml version=\"1.0\" ?>",
        "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
    )

    with open(xml_path, "w", encoding="utf-8") as f:
        f.write(pretty_xml)

    print(f"  Done — {len(path_data_parts)} path(s), "
          f"{path_data.count('C')} Bézier segments.")
