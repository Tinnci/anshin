import cv2
import numpy as np
import os
import xml.etree.ElementTree as ET
from xml.dom import minidom

def png_to_vector_drawable(png_path, xml_path, fill_color="#FFFFFFFF", viewport_size=108):
    print(f"Converting {png_path} to {xml_path}")
    
    # 1. Read image with alpha channel
    img = cv2.imread(png_path, cv2.IMREAD_UNCHANGED)
    if img is None:
        print(f"Error: Could not read {png_path}")
        return

    # 2. Extract alpha channel or convert to grayscale and threshold
    if img.shape[2] == 4:
        # Use alpha channel if present
        alpha = img[:, :, 3]
        _, thresh = cv2.threshold(alpha, 127, 255, cv2.THRESH_BINARY)
    else:
        # Convert to grayscale
        gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
        # Assuming dark foreground on light background or vice versa. 
        # Using Otsu's thresholding
        _, thresh = cv2.threshold(gray, 0, 255, cv2.THRESH_BINARY_INV + cv2.THRESH_OTSU)
        
    # Resize to viewport size for 1:1 coordinate mapping
    thresh = cv2.resize(thresh, (viewport_size, viewport_size), interpolation=cv2.INTER_NEAREST)

    # 3. Find contours
    # cv2.RETR_TREE retrieves all contours and creates a full family hierarchy list
    # cv2.CHAIN_APPROX_SIMPLE compresses horizontal, vertical, and diagonal segments
    contours, hierarchy = cv2.findContours(thresh, cv2.RETR_TREE, cv2.CHAIN_APPROX_SIMPLE)

    if not contours:
        print("No contours found.")
        return

    # 4. Convert contours to SVG path data
    path_data = ""
    for contour in contours:
        # Optional: simplify the contour
        epsilon = 0.005 * cv2.arcLength(contour, True)
        approx = cv2.approxPolyDP(contour, epsilon, True)
        
        for i, point in enumerate(approx):
            x, y = point[0]
            if i == 0:
                path_data += f"M{x},{y} "
            else:
                path_data += f"L{x},{y} "
        path_data += "Z "

    # 5. Build the XML Document
    # <vector> root
    vector = ET.Element("vector", {
        "xmlns:android": "http://schemas.android.com/apk/res/android",
        "android:width": f"{viewport_size}dp",
        "android:height": f"{viewport_size}dp",
        "android:viewportWidth": str(viewport_size),
        "android:viewportHeight": str(viewport_size)
    })

    # <path>
    ET.SubElement(vector, "path", {
        "android:fillColor": fill_color,
        "android:pathData": path_data.strip(),
        # evenOdd helps with holes if hierarchy is present
        "android:fillType": "evenOdd" 
    })

    # 6. Pretty print and save
    xml_str = ET.tostring(vector, encoding="utf-8")
    parsed = minidom.parseString(xml_str)
    pretty_xml = parsed.toprettyxml(indent="    ")

    # Remove the XML declaration added by minidom and replace it with standard Android one
    pretty_xml = '\n'.join([line for line in pretty_xml.split('\n') if line.strip()])
    pretty_xml = pretty_xml.replace('<?xml version="1.0" ?>', '<?xml version="1.0" encoding="utf-8"?>')

    with open(xml_path, "w", encoding="utf-8") as f:
        f.write(pretty_xml)
        
    print("Done.")

if __name__ == "__main__":
    # Foreground
    bg_path = "/Users/driezy/.gemini/antigravity/brain/1ea57815-64c6-4323-834a-105ca1988e6b/app_icon_foreground_1771770509808.png"
    out_fg = "/Users/driezy/Downloads/MedLogAndroid/app/src/main/res/drawable/ic_launcher_foreground.xml"
    out_mono = "/Users/driezy/Downloads/MedLogAndroid/app/src/main/res/drawable/ic_launcher_monochrome.xml"
    
    png_to_vector_drawable(bg_path, out_fg, fill_color="@color/ic_launcher_foreground", viewport_size=108)
    png_to_vector_drawable(bg_path, out_mono, fill_color="#FF000000", viewport_size=108)
    
    print("Writing background gradient directly since Python raster tracing is bad for gradients...")
    # Background gradient as XML
    bg_xml = """<?xml version="1.0" encoding="utf-8"?>
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
    bg_out = "/Users/driezy/Downloads/MedLogAndroid/app/src/main/res/drawable/ic_launcher_background.xml"
    with open(bg_out, "w", encoding="utf-8") as f:
        f.write(bg_xml)
