#!/usr/bin/env python3
"""
sync_station_assets.py
-----------------------
Reusable asset sync/scrape script for the McMurdo Station Map.
Fetches and syncs all station map data (JSON catalog, building photos,
and PDF building info sheets) from https://www.mcmurdo.usap.gov/map/
into the assets directory.

Features:
- Resumable incremental / delta sync: skips files already present with valid size.
- Rate-limited: configurable request delays to reduce network traffic.
- Retries with exponential backoff and timeout handling.
- Flags for photos only, PDFs only, or custom target directory.
- Prepares full app assets directory structure.
"""

import os
import sys
import json
import time
import shutil
import argparse
import urllib.request
import urllib.parse
import urllib.error

BASE_URL = "https://www.mcmurdo.usap.gov/map/"
USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) McMurdoMap-Android/1.0"
DEFAULT_DELAY = 0.5  # seconds between requests
MAX_RETRIES = 3

def fetch_url(url, dest_path=None, delay=DEFAULT_DELAY):
    """Downloads a URL with rate limiting and retries. Writes to dest_path if given."""
    headers = {"User-Agent": USER_AGENT}
    req = urllib.request.Request(url, headers=headers)
    
    for attempt in range(1, MAX_RETRIES + 1):
        try:
            time.sleep(delay)
            with urllib.request.urlopen(req, timeout=20) as resp:
                content = resp.read()
                if dest_path:
                    os.makedirs(os.path.dirname(dest_path), exist_ok=True)
                    # Write to a temporary file first to avoid partial downloads on interrupt
                    tmp_path = dest_path + ".tmp"
                    with open(tmp_path, "wb") as f:
                        f.write(content)
                    if os.path.exists(dest_path):
                        os.remove(dest_path)
                    os.rename(tmp_path, dest_path)
                    return len(content)
                return content
        except Exception as e:
            if attempt == MAX_RETRIES:
                print(f"\n  [ERROR] Failed to fetch {url} after {MAX_RETRIES} attempts: {e}")
                return None
            time.sleep(delay * (2 ** attempt))

def copy_core_extracted_assets(extracted_dir, target_dir):
    """Copies base CSS, JS, fonts, and UI images from extracted_web to target assets."""
    if not os.path.exists(extracted_dir):
        return
    print(f"[*] Copying core web framework assets from '{extracted_dir}' to '{target_dir}'...")
    for root, dirs, files in os.walk(extracted_dir):
        rel = os.path.relpath(root, extracted_dir)
        dest_root = os.path.join(target_dir, rel) if rel != "." else target_dir
        os.makedirs(dest_root, exist_ok=True)
        for f in files:
            if f == "index.html" or f.lower().endswith(('.jpg', '.jpeg')):
                continue
            src_f = os.path.join(root, f)
            dest_f = os.path.join(dest_root, f)
            if not os.path.exists(dest_f):
                shutil.copy2(src_f, dest_f)
    print("  [OK] Core web framework assets synced.")

def sync_assets(target_dir, delay=DEFAULT_DELAY, skip_photos=False, skip_docs=False, force=False):
    start_time = time.time()
    print("=" * 70)
    print(" McMurdo Station Map — Offline Asset Synchronizer")
    print("=" * 70)
    print(f"Target Directory: {os.path.abspath(target_dir)}")
    print(f"Rate-limit delay: {delay:.2f}s per request")
    print(f"Options: skip_photos={skip_photos}, skip_docs={skip_docs}, force={force}")
    print("-" * 70)

    os.makedirs(target_dir, exist_ok=True)
    json_dir = os.path.join(target_dir, "assets", "json")
    img_dir = os.path.join(target_dir, "assets", "img")
    photos_dir = os.path.join(img_dir, "buildings")
    doc_dir = os.path.join(target_dir, "assets", "documents")

    os.makedirs(json_dir, exist_ok=True)
    os.makedirs(img_dir, exist_ok=True)
    os.makedirs(photos_dir, exist_ok=True)
    os.makedirs(doc_dir, exist_ok=True)

    # Copy base framework files if extracted_web exists
    copy_core_extracted_assets("extracted_web", target_dir)

    # 1. Fetch metadata JSON
    json_path = os.path.join(json_dir, "mcm-map-data.json")
    json_url = f"{BASE_URL}assets/json/mcm-map-data.json"
    print(f"\n[1/3] Checking station catalog metadata ({json_url})...")
    
    json_bytes = None
    if not force and os.path.exists(json_path) and os.path.getsize(json_path) > 1000:
        print(f"  [OK] Local catalog already present: {os.path.getsize(json_path)} bytes")
        with open(json_path, "rb") as f:
            json_bytes = f.read()
    else:
        json_bytes = fetch_url(json_url, delay=delay)
        if json_bytes:
            with open(json_path, "wb") as f:
                f.write(json_bytes)
            print(f"  [OK] Downloaded latest station catalog: {len(json_bytes)} bytes")
        elif os.path.exists(json_path):
            print(f"  [WARN] Server request failed; using cached local catalog.")
            with open(json_path, "rb") as f:
                json_bytes = f.read()
        else:
            print(f"  [FATAL] No catalog available and cannot reach server.")
            return False

    buildings = json.loads(json_bytes.decode("utf-8", errors="ignore"))
    print(f"  Catalog contains {len(buildings)} buildings and facility records.")

    # Collect unique photos and info sheets
    photos = set()
    info_sheets = set()
    for b in buildings:
        p = b.get("photo")
        if p and p != "no-image.png":
            photos.add(p)
        s = b.get("info_sheet")
        if s:
            info_sheets.add(s)

    # 2. Photos (saved in 'buildings' subfolder)
    total_photo_bytes = 0
    if not skip_photos:
        photo_list = sorted(photos)
        print(f"\n[2/3] Checking {len(photo_list)} building photos in '{photos_dir}'...")
        to_download = []
        for p in photo_list:
            t = os.path.join(photos_dir, p)
            if force or not os.path.exists(t) or os.path.getsize(t) < 500:
                to_download.append(p)

        print(f"  {len(photo_list) - len(to_download)} photos already cached. {len(to_download)} to download.")
        
        for i, photo in enumerate(to_download, 1):
            target = os.path.join(photos_dir, photo)
            photo_url = f"{BASE_URL}assets/img/{urllib.parse.quote(photo)}"
            print(f"  [{i}/{len(to_download)}] Downloading photo: {photo} ...", end="", flush=True)
            sz = fetch_url(photo_url, dest_path=target, delay=delay)
            if sz:
                total_photo_bytes += sz
                print(f" OK ({sz/1024:.1f} KB)")
            else:
                print(" FAILED")
    else:
        print("\n[2/3] Skipping photos (--skip-photos enabled).")

    # 3. Documents
    total_doc_bytes = 0
    if not skip_docs:
        doc_list = sorted(info_sheets)
        print(f"\n[3/3] Checking {len(doc_list)} Building Information Sheet PDFs...")
        to_download_docs = []
        for d in doc_list:
            t = os.path.join(doc_dir, d)
            if force or not os.path.exists(t) or os.path.getsize(t) < 1000:
                to_download_docs.append(d)

        print(f"  {len(doc_list) - len(to_download_docs)} PDFs already cached. {len(to_download_docs)} to download.")
        
        for i, doc in enumerate(to_download_docs, 1):
            target = os.path.join(doc_dir, doc)
            doc_url = f"{BASE_URL}assets/documents/{urllib.parse.quote(doc)}"
            print(f"  [{i}/{len(to_download_docs)}] Downloading PDF: {doc} ...", end="", flush=True)
            sz = fetch_url(doc_url, dest_path=target, delay=delay)
            if sz:
                total_doc_bytes += sz
                print(f" OK ({sz/1024:.1f} KB)")
            else:
                print(" FAILED")
    else:
        print("\n[3/3] Skipping PDFs (--skip-docs enabled).")

    # Write / update sync metadata file for help & info
    now_utc = time.gmtime()
    months = ["January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"]
    formatted_date = f"{months[now_utc.tm_mon - 1]} {now_utc.tm_mday}, {now_utc.tm_year}"
    iso_date = time.strftime("%Y-%m-%dT%H:%M:%SZ", now_utc)

    # Count actual photos and documents present on disk
    actual_photos = [f for f in os.listdir(photos_dir) if f.lower().endswith(('.jpg', '.jpeg', '.png', '.webp')) and f != 'no-image.png'] if os.path.exists(photos_dir) else []
    actual_docs = [f for f in os.listdir(doc_dir) if f.lower().endswith('.pdf')] if os.path.exists(doc_dir) else []

    metadata = {
        "last_synced": iso_date,
        "last_synced_formatted": formatted_date,
        "source_url": BASE_URL,
        "total_buildings": len(buildings),
        "total_photos": len(actual_photos),
        "total_documents": len(actual_docs)
    }
    meta_path = os.path.join(target_dir, "sync_metadata.json")
    with open(meta_path, "w", encoding="utf-8") as f:
        json.dump(metadata, f, indent=2)
    print(f"  [METADATA] Updated single source of truth: {meta_path}")
    print(f"             Last Synced: {formatted_date} | {len(actual_photos)} photos | {len(actual_docs)} PDF specs")

    # Re-generate map_view.html so map outlines and labels match new data
    script_map = os.path.join(os.path.dirname(__file__), "create_mobile_map_html.py")
    if os.path.exists(script_map):
        print("\n[*] Rebuilding mobile map HTML and SVG labels from updated station data...")
        import subprocess
        subprocess.run([sys.executable, script_map], check=False)

    elapsed = time.time() - start_time
    print("\n" + "=" * 70)
    print(" Sync Finished Successfully!")
    print(f" Elapsed Time: {elapsed:.1f}s")
    print(f" Downloaded: {total_photo_bytes/1024:.1f} KB photos, {total_doc_bytes/1024/1024:.2f} MB PDFs")
    print(f" All files stored in: {os.path.abspath(target_dir)}")
    print("=" * 70)
    return True

def main():
    parser = argparse.ArgumentParser(description="Synchronize McMurdo Station Map assets for offline Android use.")
    parser.add_argument("--target-dir", default="app/src/main/assets", help="Target assets directory (default: app/src/main/assets)")
    parser.add_argument("--delay", type=float, default=DEFAULT_DELAY, help="Delay between requests in seconds (default: 0.4s)")
    parser.add_argument("--skip-photos", action="store_true", help="Skip downloading building photos")
    parser.add_argument("--skip-docs", action="store_true", help="Skip downloading PDF spec sheets")
    parser.add_argument("--force", action="store_true", help="Force re-download of already existing files")
    
    args = parser.parse_args()
    try:
        sync_assets(
            target_dir=args.target_dir,
            delay=args.delay,
            skip_photos=args.skip_photos,
            skip_docs=args.skip_docs,
            force=args.force
        )
    except KeyboardInterrupt:
        print("\n\n[!] Script stopped by user. Any completed files are saved.")
        print("[!] Re-run the script to resume.")
        sys.exit(0)

if __name__ == "__main__":
    main()
