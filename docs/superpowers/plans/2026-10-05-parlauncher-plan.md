# ParLauncher Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build an ultra-lightweight, zero-bitmap, battery-efficient minimalist Android Launcher ("ParLauncher") with Persian/Gregorian typography and sub-10MB heap RAM usage.

**Architecture:** Single-Activity (`MainActivity`) architecture rendering home and search/drawer over a transparent window layer with `windowShowWallpaper="true"`. Zero bitmap decoding/caching; pure typographic Vazirmatn layout; instant allocation-free search filter; native `LauncherApps.Callback` lifecycle tracking.

**Tech Stack:** Kotlin 2.0, Gradle Kotlin DSL, Android SDK 35 (Min SDK 24), Java 17, AndroidX Core KTX, AndroidX RecyclerView, ICU4J (native Android ICU `android.icu.util.PersianCalendar`).

**Spec:** `docs/superpowers/specs/2026-10-05-parlauncher-design.md`

## Global Constraints

- Package name: `com.parboard.launcher`
- App label: `ParLauncher` (Persian: `پر لانچر`)
- Min SDK: 24, Target SDK: 35
- Zero bitmap memory footprint (no icon loading/rendering in RAM)
- Wallpaper rendered exclusively by `SurfaceFlinger` (`android:windowShowWallpaper="true"`, `android:windowBackground="@android:color/transparent"`)
- R8 minification and resource shrinking enabled in release build (`isMinifyEnabled = true`, `isShrinkResources = true`)
- Total RAM footprint strictly < 15MB (target 6-10MB)
- Release APK size < 1.5MB

## Review Focus

1. **Persian & English mixed search tokens:** Search query with Arabic characters (`ي`, `ك`) or half-spaces must match Persian launcher labels seamlessly.
2. **Empty or missing package handling:** If an uninstalled app was pinned to favorites, launching or rendering it must safely fallback/remove without crashing.
3. **IME dismiss and back gesture:** Pressing hardware back while in drawer must collapse drawer and hide IME cleanly without exiting the home launcher.
4. **Activity transition zero-latency:** App launch must not stutter or produce black frames over live wallpapers (`FLAG_ACTIVITY_NEW_TASK` + `overridePendingTransition(0, 0)`).
5. **Minute-boundary clock update:** Clock and date must update accurately at the start of each minute without continuously running background timers or wake-locks.

---

### Task 1: Gradle Scaffolding & Build Configuration

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `app/build.gradle.kts`
- Create: `app/proguard-rules.pro`
- Copy wrapper: `gradlew`, `gradlew.bat`, `gradle/wrapper/*` from `../PersianKeyboard`

**Interfaces:**
- Produces: Runnable Gradle project targeting Android 35 with Min SDK 24 and R8 enabled.

- [ ] **Step 1: Create root Gradle build scripts and properties**
Configure `settings.gradle.kts` with root project name `ParLauncher` and `:app` module. Configure `gradle.properties` with AndroidX and JVM args.

- [ ] **Step 2: Create `app/build.gradle.kts` with zero-bloat dependencies**
Include only `androidx.core:core-ktx:1.13.1` and `androidx.recyclerview:recyclerview:1.3.2`, with release build configured for R8 minification.

- [ ] **Step 3: Copy Gradle wrapper and local.properties from PersianKeyboard**
Copy `gradle/`, `gradlew`, `gradlew.bat`, and `local.properties`.

- [ ] **Step 4: Verify build scaffolding compiles**
Run: `./gradlew help`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**
```bash
git add settings.gradle.kts build.gradle.kts gradle.properties app/build.gradle.kts app/proguard-rules.pro local.properties gradlew gradlew.bat gradle/
git commit -m "chore: scaffold ParLauncher gradle project"
```

---

### Task 2: Wallpaper Scrim Theme, Window Setup & Manifest

**Files:**
- Create: `app/src/main/res/values/colors.xml`
- Create: `app/src/main/res/values/styles.xml`
- Create: `app/src/main/res/font/vazirmatn_regular.ttf`
- Create: `app/src/main/res/font/vazirmatn_bold.ttf`
- Create: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Produces: Home launcher manifest declaration (`android.intent.category.HOME`) and wallpaper-transparent theme with dark translucent scrim `#80121212`.

- [ ] **Step 1: Create font and color resources**
Add dark scrim `#80121212` and primary text colors in `colors.xml`. Embed lightweight Vazirmatn font files in `res/font/`.

- [ ] **Step 2: Create launcher theme in `styles.xml`**
Set `android:windowShowWallpaper` to `true`, `android:windowBackground` to `@android:color/transparent`, and status/navigation bars to transparent edge-to-edge.

- [ ] **Step 3: Define `AndroidManifest.xml` with launcher roles**
Declare `MainActivity` with:
```xml
<category android:name="android.intent.category.HOME" />
<category android:name="android.intent.category.DEFAULT" />
```
and `<queries>` / `QUERY_ALL_PACKAGES` permission required for Android 11+ app listing.

- [ ] **Step 4: Verify manifest merges cleanly**
Run: `./gradlew processDebugManifest`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**
```bash
git add app/src/main/res/ app/src/main/AndroidManifest.xml
git commit -m "feat: configure launcher wallpaper theme and manifest"
```

---

### Task 3: Domain Model & Zero-Allocation Search Engine

**Files:**
- Create: `app/src/main/java/com/parboard/launcher/model/AppItem.kt`
- Create: `app/src/main/java/com/parboard/launcher/util/SearchEngine.kt`
- Create: `app/src/test/java/com/parboard/launcher/SearchEngineTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  data class AppItem(val label: String, val packageName: String, val activityName: String, val normalizedToken: String)
  object SearchEngine {
      fun normalize(text: String): String
      fun filter(items: List<AppItem>, query: String): List<AppItem>
  }
  ```

- [ ] **Step 1: Write failing unit tests for search normalization and filtering**
In `SearchEngineTest.kt`:
```kotlin
@Test
fun testNormalizeArabicCharsAndHalfSpace() {
    val input = "فایل‌های صوتی كلاسيك"
    val normalized = SearchEngine.normalize(input)
    assertEquals("فایلهای صوتی کلاسیک", normalized)
}

@Test
fun testInstantFilterMatchesSubstring() {
    val items = listOf(
        AppItem("تنظیمات", "com.android.settings", ".Settings", SearchEngine.normalize("تنظیمات")),
        AppItem("دوربین", "com.android.camera", ".Camera", SearchEngine.normalize("دوربین"))
    )
    val result = SearchEngine.filter(items, "تنظیم")
    assertEquals(1, result.size)
    assertEquals("تنظیمات", result[0].label)
}
```

- [ ] **Step 2: Run test to verify failure**
Run: `./gradlew testDebugUnitTest --tests com.parboard.launcher.SearchEngineTest`
Expected: FAIL (unresolved reference `SearchEngine`)

- [ ] **Step 3: Implement `AppItem` and `SearchEngine`**
Implement fast normalization (mapping `ي` to `ی`, `ك` to `ک`, stripping zero-width non-joiner `\u200c`, lowercasing) and allocation-minimized list filtering.

- [ ] **Step 4: Run test to verify it passes**
Run: `./gradlew testDebugUnitTest --tests com.parboard.launcher.SearchEngineTest`
Expected: PASS

- [ ] **Step 5: Commit**
```bash
git add app/src/main/java/com/parboard/launcher/model/ app/src/main/java/com/parboard/launcher/util/ app/src/test/java/com/parboard/launcher/SearchEngineTest.kt
git commit -m "feat: add AppItem model and zero-allocation SearchEngine with tests"
```

---

### Task 4: Native Persian & Gregorian Date Formatter

**Files:**
- Create: `app/src/main/java/com/parboard/launcher/util/DateFormatter.kt`
- Create: `app/src/test/java/com/parboard/launcher/DateFormatterTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  object DateFormatter {
      fun getFormattedPersianDate(calendar: Calendar = Calendar.getInstance()): String
      fun getFormattedGregorianDate(calendar: Calendar = Calendar.getInstance()): String
  }
  ```

- [ ] **Step 1: Write unit tests for date formatting**
In `DateFormatterTest.kt`: verify that calling `getFormattedPersianDate` returns a non-empty Solar Hijri formatted string (e.g. including Persian day of week and month) without crashing or allocating external libraries.

- [ ] **Step 2: Run test to verify failure**
Run: `./gradlew testDebugUnitTest --tests com.parboard.launcher.DateFormatterTest`
Expected: FAIL

- [ ] **Step 3: Implement `DateFormatter` using native `android.icu.util.PersianCalendar`**
Use `android.icu.util.PersianCalendar` and `android.icu.util.ULocale("fa_IR")` for Persian dates, and standard Java Calendar for Gregorian dates.

- [ ] **Step 4: Run test to verify it passes**
Run: `./gradlew testDebugUnitTest --tests com.parboard.launcher.DateFormatterTest`
Expected: PASS

- [ ] **Step 5: Commit**
```bash
git add app/src/main/java/com/parboard/launcher/util/DateFormatter.kt app/src/test/java/com/parboard/launcher/DateFormatterTest.kt
git commit -m "feat: add zero-dependency Persian and Gregorian DateFormatter"
```

---

### Task 5: Pinned Favorites Storage (SharedPreferences)

**Files:**
- Create: `app/src/main/java/com/parboard/launcher/data/FavoritesRepository.kt`
- Create: `app/src/test/java/com/parboard/launcher/FavoritesRepositoryTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  class FavoritesRepository(private val context: Context) {
      fun getFavorites(): List<String> // list of "packageName|activityName"
      fun addFavorite(packageName: String, activityName: String): Boolean
      fun removeFavorite(packageName: String, activityName: String): Boolean
      fun isFavorite(packageName: String, activityName: String): Boolean
  }
  ```

- [ ] **Step 1: Write unit tests with mocked/in-memory SharedPreferences**
Verify: Adding up to 7 items succeeds; 8th item is capped; removing an item works; duplicates are prevented; order is preserved.

- [ ] **Step 2: Run test to verify failure**
Run: `./gradlew testDebugUnitTest --tests com.parboard.launcher.FavoritesRepositoryTest`
Expected: FAIL

- [ ] **Step 3: Implement `FavoritesRepository`**
Use `SharedPreferences` storing a simple delimited string with maximum 7 entries, committed asynchronously via `apply()`.

- [ ] **Step 4: Run test to verify it passes**
Run: `./gradlew testDebugUnitTest --tests com.parboard.launcher.FavoritesRepositoryTest`
Expected: PASS

- [ ] **Step 5: Commit**
```bash
git add app/src/main/java/com/parboard/launcher/data/ app/src/test/java/com/parboard/launcher/FavoritesRepositoryTest.kt
git commit -m "feat: implement FavoritesRepository with SharedPreferences persistence"
```

---

### Task 6: App Query, Lifecycle Callback & Launch Intent

**Files:**
- Create: `app/src/main/java/com/parboard/launcher/data/AppRepository.kt`
- Create: `app/src/main/java/com/parboard/launcher/util/AppLauncher.kt`

**Interfaces:**
- Produces:
  ```kotlin
  class AppRepository(private val context: Context) {
      fun loadInstalledApps(): List<AppItem>
      fun registerPackageCallback(onChanged: () -> Unit)
      fun unregisterPackageCallback()
  }
  object AppLauncher {
      fun launch(context: Context, item: AppItem)
  }
  ```

- [ ] **Step 1: Implement `AppRepository` using `LauncherApps` / `PackageManager`**
Query `Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)`. Sort alphabetically by label. Register `LauncherApps.Callback` for package install/uninstall/update events.

- [ ] **Step 2: Implement `AppLauncher.launch`**
Execute `FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED` and invoke `overridePendingTransition(0, 0)` on activity to ensure zero launch latency.

- [ ] **Step 3: Verify build compiles cleanly**
Run: `./gradlew compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**
```bash
git add app/src/main/java/com/parboard/launcher/data/AppRepository.kt app/src/main/java/com/parboard/launcher/util/AppLauncher.kt
git commit -m "feat: implement AppRepository with LauncherApps.Callback and zero-latency AppLauncher"
```

---

### Task 7: RecyclerView Drawer UI & Fast Instant Search

**Files:**
- Create: `app/src/main/res/layout/item_app.xml`
- Create: `app/src/main/java/com/parboard/launcher/ui/AppAdapter.kt`
- Create: `app/src/main/res/layout/view_drawer.xml`

**Interfaces:**
- Produces:
  - Single-viewholder `AppAdapter` with `setHasFixedSize(true)` and custom click & long-click callbacks.
  - Text-only item layout using Vazirmatn font.
  - Drawer view containing `EditText` search bar and `RecyclerView`.

- [ ] **Step 1: Create `item_app.xml`**
Single minimalist `TextView` with Vazirmatn typography, 48dp touch target height, zero image views.

- [ ] **Step 2: Implement `AppAdapter`**
`RecyclerView.Adapter` with efficient in-place list updates, zero bitmap binding, and click/long-click listeners.

- [ ] **Step 3: Create `view_drawer.xml`**
Search bar `EditText` at the top with translucent dark scrim background and high-performance `RecyclerView`.

- [ ] **Step 4: Verify build compiles cleanly**
Run: `./gradlew compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**
```bash
git add app/src/main/res/layout/item_app.xml app/src/main/res/layout/view_drawer.xml app/src/main/java/com/parboard/launcher/ui/AppAdapter.kt
git commit -m "feat: implement ultra-fast AppAdapter and drawer layout"
```

---

### Task 8: MainActivity, Home Screen & Default Role Integration

**Files:**
- Create: `app/src/main/res/layout/activity_main.xml`
- Create: `app/src/main/java/com/parboard/launcher/ui/MainActivity.kt`
- Create: `app/src/main/java/com/parboard/launcher/util/DefaultRoleHelper.kt`

**Interfaces:**
- Produces:
  - Complete `MainActivity` handling:
    1. Digital clock & Solar Hijri / Gregorian date display.
    2. Pinned favorites (5-7 apps) with tap-to-launch and long-press to remove.
    3. Drawer open/close transition (`translationY`), automatic IME popup.
    4. Back press dispatch to close drawer before exiting.
    5. Onboarding / helper to set ParLauncher as default home app.

- [ ] **Step 1: Create `activity_main.xml`**
Flat `FrameLayout` with `#80121212` scrim containing `home_container` and `drawer_container`.

- [ ] **Step 2: Implement `DefaultRoleHelper`**
Provide check and intent trigger for `RoleManager.ROLE_HOME` on API 29+ or `Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS` fallback.

- [ ] **Step 3: Implement `MainActivity`**
Wire together Clock, Date, Favorites, Drawer, Search TextWatcher, IME auto-show, and lifecycle registration.

- [ ] **Step 4: Verify debug build succeeds**
Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**
```bash
git add app/src/main/res/layout/activity_main.xml app/src/main/java/com/parboard/launcher/ui/MainActivity.kt app/src/main/java/com/parboard/launcher/util/DefaultRoleHelper.kt
git commit -m "feat: implement MainActivity with home screen, favorites, and default launcher role"
```

---

### Task 9: Verification, Release Optimization & Benchmark

**Files:**
- Modify: `app/build.gradle.kts` (verification and signing if available)
- Verify: Full test suite passing
- Verify: ProGuard / R8 optimized release build

- [ ] **Step 1: Run complete test suite**
Run: `./gradlew test`
Expected: All unit tests pass.

- [ ] **Step 2: Build release APK**
Run: `./gradlew assembleRelease`
Expected: `BUILD SUCCESSFUL` and APK generated in `app/build/outputs/apk/release/`.

- [ ] **Step 3: Measure APK size**
Verify APK size is < 1.5MB.

- [ ] **Step 4: Final commit and tag**
```bash
git add .
git commit -m "chore: verify release build and test suite"
git tag v1.0.0
```
