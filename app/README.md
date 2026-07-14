# IronVault (ایران‌ولت / آیرون‌ولت) 🔒

**IronVault** is a highly secure, offline-first, military-grade password manager and credentials vault designed for Android. Built with modern Android development practices, it offers absolute peace of mind by keeping your passwords, notes, and sensitive data safe through local database encryption, robust local backup facilities, and secure Google Drive automated synchronization.

---

## 🌟 ویژگی‌های کلیدی (Key Features)

- 🔒 **امنیت در سطح نظامی (Military-Grade Security):** رمزنگاری فوق‌العاده قوی تمامی اطلاعات در پایگاه‌داده محلی.
- ☁️ **پشتیبان‌گیری ابری گوگل درایو (Google Drive Cloud Backups):** هماهنگ‌سازی و بکاپ خودکار رمزگذاری‌شده روی گوگل درایو شخصی شما.
- 💾 **پشتیبان‌گیری محلی (Local Encrypted Backups):** امکان برون‌بری و درون‌ریزی فایل‌های بکاپ رمزگذاری‌شده به‌صورت آفلاین.
- 🔑 **تولیدکننده رمز عبور هوشمند (Advanced Password Generator):** ساخت کلمات عبور فوق‌امن و تلفظ‌پذیر با کنترل کامل روی طول، حروف بزرگ/کوچک، اعداد و نشانه‌ها.
- 📊 **آنالیزور قدرت رمز (Real-time Strength Meter):** محاسبه آنتروپی و میزان امنیت گذرواژه‌ها در زمان واقعی.
- 🎨 **رابط کاربری مدرن Material 3 (Beautiful M3 Design):** تم تاریک جذاب و چشم‌نواز با فواصل استاندارد و آیکون اختصاصی و باکیفیت.
- 🚫 **حفاظت در برابر عکس‌برداری (Anti-Screen Exposure):** غیرفعال‌سازی امکان اسکرین‌شات گرفتن از برنامه در حالت Production جهت حفاظت از لو رفتن رمزها.

---

## 🛠️ معماری و فناوری‌ها (Architecture & Tech Stack)

- **Language:** Kotlin 100%
- **UI Framework:** Jetpack Compose (Material Design 3)
- **Database:** Room Database (Offline storage with local persistence)
- **State Management:** MVVM Architecture with `ViewModel` and `StateFlow`
- **Asynchronous Operations:** Kotlin Coroutines & Flow
- **Dependency Management:** Gradle Kotlin DSL (with Version Catalog `libs.versions.toml`)
- **Testing:** JVM Local Unit tests, Robolectric & Roborazzi (Screenshot Testing)

---

## 🚀 راهنمای نصب و بیلد (Build & Run Guide)

### پیش‌نیازها (Prerequisites)
1. **JDK 17** یا بالاتر روی سیستم نصب باشد.
2. **Android Studio (Koala / Ladybug)** یا نسخه‌های جدیدتر.
3. **Android SDK** نسخه 34 یا بالاتر.

---

### 📦 دستورات خط فرمان گریدل (Gradle CLI Commands)

برای بیلد گرفتن و تست برنامه بدون نیاز به محیط گرافیکی اندروید استودیو، دستورات زیر را در ریشه پروژه اجرا کنید:

#### ۱. اجرای تست‌های واحد و روبولکتریک (Run Unit & Robolectric Tests)
برای بررسی درستی کارکرد منطق برنامه و تست‌های محلی:
```bash
gradle :app:testDebugUnitTest
```

#### ۲. کامپایل کل پروژه (Compile App)
برای اطمینان از عدم وجود هرگونه خطای کامپایل و سینتکس:
```bash
gradle compileDebugSources
```

#### ۳. ساخت فایل APK دیباگ (Build Debug APK)
برای بیلد گرفتن و تولید فایل نهایی نصب (APK):
```bash
gradle :app:assembleDebug
```
فایل APK خروجی در مسیر زیر ذخیره می‌شود:
`app/build/outputs/apk/debug/app-debug.apk`

#### ۴. نصب روی دستگاه/شبیه‌ساز متصل (Install on Device/Emulator)
اگر یک موبایل یا شبیه‌ساز به سیستم متصل است:
```bash
gradle :app:installDebug
```

---

## 🔑 مدیریت کلیدها و امنیت (Secret Management)

برنامه از پلاگین **Secrets Gradle Plugin** برای مدیریت کلیدهای امنیتی استفاده می‌کند تا هیچ کلید یا رمزی به صورت هاردکد درون کدهای پروژه قرار نگیرد.

برای اجرا و استفاده از بخش همگام‌سازی گوگل درایو:
1. یک فایل به نام `.env` در ریشه پروژه بسازید.
2. متغیرهای مورد نیاز خود مانند کلاینت‌آیدی گوگل را در آن قرار دهید:
```env
GOOGLE_CLIENT_ID="your_google_client_id_here"
```
*توجه داشته باشید که این کلیدها در زمان بیلد به کلاس `BuildConfig` تزریق می‌شوند.*

---

## 🎨 آیکون جدید برنامه (New App Icon)
آیکون برنامه بازطراحی شده و یک آیکون تطبیق‌پذیر (Adaptive Icon) بسیار شیک و مدرن، متشکل از یک گاوصندوق فولادی با هاله‌های آبی نئون امنیتی جایگزین آیکون پیش‌فرض اندروید شده است که با استانداردهای طراحی Material You کاملاً همخوانی دارد.
