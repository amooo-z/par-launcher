# ParLauncher Architecture Specification (Design Spec)

- **Package:** `com.parboard.launcher`
- **Application Name:** `ParLauncher` (Persian: `پر لانچر`)
- **Target SDK:** 35 (Android 15)
- **Min SDK:** 24 (Android 7.0+)
- **Language & Tooling:** Kotlin 2.0+, Gradle Kotlin DSL (`build.gradle.kts`), Java 17
- **Target Memory Footprint:** < 15MB Heap (Operating Target: 6MB - 10MB)
- **APK Target Size:** < 1.5MB release build (with R8 code shrinking and resource stripping)
- **Idle Battery Consumption:** Strict 0% (Event-driven via `LauncherApps.Callback`, zero wake-locks, zero background polling services)

---

## 1. System Philosophy & Non-Negotiable Constraints

1. **Absolute Zero-Bitmap Pipeline (Text-Only Mode):**
   - The launcher will **not** query, load, resize, or cache application icon drawables or bitmaps into process memory.
   - Applications are identified and rendered purely via typographic labels (using the bundled ultra-lightweight Vazirmatn font).
   - This eliminates `Bitmap` memory allocations entirely from the launcher heap, cutting base memory usage from ~25-35MB down to ~6-8MB.

2. **Zero-RAM Wallpaper Compositing:**
   - Under no circumstances does the application obtain wallpaper bytes via `WallpaperManager.getDrawable()` (which allocates 20MB to 40MB of ARGB_8888 bitmap RAM).
   - The launcher theme in `styles.xml` declares:
     ```xml
     <item name="android:windowShowWallpaper">true</item>
     <item name="android:windowBackground">@android:color/transparent</item>
     ```
   - Hardware compositor (`SurfaceFlinger` / `system_server`) renders the live wallpaper directly on the display surface behind our window layer.
   - A translucent dark scrim color (`#80121212`) is applied across the root layout to maintain contrast and WCAG readability over dynamic wallpapers.

3. **Event-Driven App Cache Invalidation:**
   - Instead of legacy manifest-registered `BroadcastReceiver` filters for package events, `MainActivity` registers an instance of `LauncherApps.Callback` via `getSystemService(LauncherApps::class.java)`.
   - Callbacks (`onPackageAdded`, `onPackageRemoved`, `onPackageChanged`) notify the in-memory array to invalidate and refresh without waking the CPU when the launcher is idle or in the background.

---

## 2. Architecture & Data Structures

### 2.1 Domain Model
A flat, immutable, compact data class:

```kotlin
data class AppItem(
    val label: String,
    val packageName: String,
    val activityName: String,
    val normalizedToken: String
)
```
- `normalizedToken`: Pre-computed lowercase Persian/English string with Arabic character normalization (e.g., standardizing `ي` to `ی` and `ك` to `ک`). Pre-computed at query time so real-time keystroke searches execute in under 0.2ms with zero allocation per character typed.

### 2.2 Storage Layer: Fast Key-Value Store
- Pinned favorites are stored in `SharedPreferences` (`"parlauncher_prefs"`) as an ordered JSON array or delimited string:
  `"com.android.settings|com.google.android.youtube|..."`
- Stored items do not exceed 7 entries.
- Changes are written asynchronously with `apply()`.

### 2.3 Calendar & Date Formatting
- Uses native `android.icu.util.PersianCalendar` and `android.icu.text.SimpleDateFormat` available since Android 7.0 (`minSdk 24`).
- Zero external libraries for Jalali/Solar Hijri calculations.
- Clock updates are synchronized to the minute boundary via a lightweight `BroadcastReceiver` listening to `Intent.ACTION_TIME_TICK` (or registered during `onStart`/`onStop`).

---

## 3. UI / UX Design & Interactions

### 3.1 Single-Activity Layout Hierarchy (`activity_main.xml`)
Root is a flat `FrameLayout` with two visual layers:

```
FrameLayout (Root, background="#80121212")
 ├── HomeView (LinearLayout)
 │    ├── ClockDateContainer (LinearLayout)
 │    │    ├── TextClock (HH:mm) [Vazirmatn Bold]
 │    │    └── TextView (Persian Solar Date + Gregorian Date) [Vazirmatn Regular]
 │    ├── FavoritesContainer (LinearLayout, vertical list of 5-7 AppItems)
 │    └── SearchTrigger (TextView / Minimal Indicator "جستجو یا لمس برای دراور...")
 └── DrawerView (LinearLayout, translationY = screenHeight or View.GONE initially)
      ├── SearchInput (EditText, no border, Vazirmatn Regular, clear button)
      ├── FastScrollBar / SectionIndicator
      └── RecyclerView (Single Viewholder, setHasFixedSize(true), text-only item)
```

### 3.2 Transitions & Gestures
- **Opening App Drawer:**
  - Tapping the search trigger or swiping up on the home screen smoothly translates `DrawerView` into view (`translationY = 0f`).
  - Calls `WindowInsetsControllerCompat(window, searchInput).show(WindowInsetsCompat.Type.ime())` (or `InputMethodManager.showSoftInput`) to immediately raise the system keyboard (ParBoard).
- **Closing App Drawer:**
  - Pressing Android Back button (intercepted via `onBackPressedDispatcher`) or swiping down translates `DrawerView` down and hides the keyboard.
- **Launching an Application:**
  - Launch intent constructed cleanly:
    ```kotlin
    val intent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
        setClassName(item.packageName, item.activityName)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
    }
    context.startActivity(intent)
    ```
  - Activity transition overridden with `overridePendingTransition(0, 0)` for zero perceived latency.

### 3.3 Pinning / Customizing Favorites
- **In Drawer:** Long-press on any `AppItem` shows a fast context dialog/popup: "پین به علاقه‌مندی‌ها" (Pin to favorites) or "حذف از علاقه‌مندی‌ها" (Unpin).
- **On Home Screen:** Long-press on any favorite allows immediate unpinning.

---

## 4. Release & ProGuard Optimization Configuration

```kotlin
buildTypes {
    release {
        isMinifyEnabled = true
        isShrinkResources = true
        proguardFiles(
            getDefaultProguardFile("proguard-android-optimize.txt"),
            "proguard-rules.pro"
        )
    }
}
```

R8 optimizations will aggressively strip any unused Android framework classes and Kotlin stdlib metadata.

---

## 5. Default Home App Role Management
- Manifest declares:
  ```xml
  <intent-filter>
      <action android:name="android.intent.action.MAIN" />
      <category android:name="android.intent.category.HOME" />
      <category android:name="android.intent.category.DEFAULT" />
  </intent-filter>
  ```
- If ParLauncher is not currently set as default, an unobtrusive banner or initial prompt can launch:
  - On API 29+: `RoleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)`
  - On API <29: `Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)` fallback to `Intent(Settings.ACTION_HOME_SETTINGS)`

---

## 6. Verification & Test Plan

1. **Unit Tests (JVM):**
   - `SearchFilterTest`: Test token normalization and filtering with Persian characters (`ی/ي`, `ک/ك`, half-space `\u200c`, numbers).
   - `PersianDateFormatterTest`: Verify correct Solar Hijri conversion and formatting using ICU.
   - `FavoritesRepositoryTest`: Verify persistence, ordering, and capacity limit (7 items).

2. **Integration & Performance Verification:**
   - Launching apps via `LauncherApps` without crash.
   - Memory Profiling: Dump heap via Android Studio Profiler / ADB to prove total heap < 12MB.
   - APK size check: Verify final release APK is < 1.5MB.
