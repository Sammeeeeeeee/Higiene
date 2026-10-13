# Higiene

Android app that shows the official Food Standards Agency (FSA) hygiene ratings of the food businesses nearest to you. Works offline. Your location stays on the phone.

## Install

1. Open the [latest release](https://github.com/Sammeeeeeeee/Higiene/releases/latest) on your phone.
2. Download `Higiene-vX.Y.apk` and open it. Allow installs from your browser or file manager when Android asks.
3. On first launch, tap **Download ratings**. The app fetches the FSA's full UK file (about 145 MB; waits for Wi-Fi by default), then the OS postcode file (14 MB).

Needs Android 8.0 or newer.

## What it does

- Nearby list sorted by distance: official rating badge, distance, business type, inspection date, chain logo.
- Filters: business type, minimum rating, hide exempt, hide awaiting inspection, inspected within 1–3 years. Sort by distance, rating, recency or name.
- Search by name, street or postcode (full, sector such as `E1 6`, or outcode such as `E1`), all offline.
- Detail page: score breakdown, new-rating-pending notice, the business's right to reply, FSA page, directions, share.
- Favourites, with a notification when a rating changes.
- Whole UK: 0–5 in England, Wales and Northern Ireland; Pass / Improvement required in Scotland. Welsh councils' businesses get the bilingual badge.
- Finds businesses other apps miss: about 1 in 8 restaurants have no map position in the FSA data. Higiene places them at their postcode centre. On the October 2026 file this cuts restaurants without a position from 17,689 to 6,008 (the rest publish no postcode, mostly home or mobile caterers; the app lists them by council).
- **Who's really cooking?** Every business registered at the same address or postcode (delivery-only kitchens often share one).
- OpenStreetMap extras on the detail page, shown only when OpenStreetMap has them: opening hours, cuisine, website, phone, diets.
- Optional, with your own keys (Settings → Photos and Google ratings): a street photo of the shopfront (Google Street View, or Mapillary) and the Google rating, opening hours and phone. Built-in daily and monthly caps keep a Google key inside Google's free allowance. Without keys these cards don't appear.

## How it works

| Data | Source | Refresh | Licence |
|---|---|---|---|
| Ratings | [FSA bulk CSV](https://ratings.food.gov.uk/open-data) (`FHRS_All_en-GB.csv`) | Daily, Wi-Fi only by default; skipped via ETag when unchanged | [OGL v3](https://www.nationalarchives.gov.uk/doc/open-government-licence/version/3/) |
| Rating badges | [FSA images.zip](https://ratings.food.gov.uk/open-data), bundled unaltered | With app updates | FSA trademarks, [FSA terms](https://ratings.food.gov.uk/terms-and-conditions) |
| Postcode positions (GB) | [OS Code-Point Open](https://www.ordnancesurvey.co.uk/products/code-point-open) | Checked monthly | OGL v3 |
| Council names | [FSA API `/Authorities`](https://api.ratings.food.gov.uk/help) | Weekly | OGL v3 |
| Hours, cuisine, website | [OpenStreetMap](https://www.openstreetmap.org/copyright), objects tagged `fhrs:id`, from a file built weekly by [`osm-data.yml`](.github/workflows/osm-data.yml) (`tools/osm_extract.py` on the Geofabrik UK extract) and published as the [`osm-data` release](https://github.com/Sammeeeeeeee/Higiene/releases/tag/osm-data) | Weekly (about 4 MB) | ODbL |
| Street photo, Google rating and hours (optional) | Your own key: [Places API (New)](https://developers.google.com/maps/documentation/places/web-service) and [Street View Static API](https://developers.google.com/maps/documentation/streetview), or a [Mapillary](https://www.mapillary.com/developer) token | Fetched when you open a business; Google content is not stored (place IDs only) | [Google Maps terms](https://maps.google.com/help/terms_maps); Mapillary photos CC BY-SA 4.0 |
| Chain names | [name-suggestion-index](https://github.com/osmlab/name-suggestion-index) (`tools/build_brands.py`) | With app updates | BSD-3-Clause |
| Chain logos | Wikimedia Commons, then Facebook | Cached | Owners' trademarks |

- The CSV is streamed straight into SQLite. Each row's hash is compared with the stored one, so a daily update rewrites only the rows that changed. The whole update is one transaction, committed only when the byte count and the server's `Content-MD5` match; a cut-off or truncated download changes nothing.
- Missing positions: OS postcode centre first (OSGB36 → WGS84 with a Helmert shift, about 5 m error), then the average of FSA-located businesses with the same postcode (covers Northern Ireland, which Code-Point Open omits).
- Distances, postcode look-ups and search run on the phone. The app never sends your location anywhere.
- OpenStreetMap: the public Overpass servers don't allow apps to query them per business ([usage policy](https://dev.overpass-api.de/overpass-doc/en/preface/commons.html)), so a scheduled workflow extracts the ~134,000 UK objects with an `fhrs:id` and useful tags into a small file. The phone downloads it with the ratings and matches by FHRSID, offline.

Higiene is not made, checked or endorsed by the Food Standards Agency.

## Build

JDK 21 and the Android SDK (platform 37):

```sh
./gradlew testDebugUnitTest assembleRelease
```

Optional checks:

```sh
# Full import against the real files (prints timings and coverage)
HIGIENE_CSV=/path/FHRS_All_en-GB.csv HIGIENE_CODEPOINT=/path/codepo_gb.zip ./gradlew testDebugUnitTest --tests '*FullDataTest*'
# Screenshots of key screens
HIGIENE_SCREENSHOTS=/tmp/screens ./gradlew testDebugUnitTest --tests '*ScreenshotTest*'
```

Releases: merging to `main` runs `.github/workflows/release.yml`. If `versionName` in `app/build.gradle.kts` has no tag yet, it publishes release `v<versionName>` with the signed APK. Bump `versionCode` and `versionName` to ship a new version.

**Signing key:** `keystore/` holds the release key and its password, in public, by the owner's choice. Anyone can sign an APK with it, so only install Higiene APKs from this repository's Releases page. Moving to a private key later means one uninstall and reinstall (favourites are lost).

## Licence

GPL-3.0. See [LICENSE](LICENSE).
