# Offline Chess

A native Android chess application built with Kotlin and Jetpack Compose, running the chess rules engine and Stockfish directly on the device. Games, history, and preferences are handled locally. The application currently does not declare the Internet permission.

> Development Status: Successfully built and tested on an Android 14 emulator. Built with `compileSdk`/`targetSdk` 36 and verified the 16 KB ELF alignment of the native library. Not yet tested on Android 16 devices or devices with a 16 KB page size; no licensed puzzle collection is currently available; the AAB has not yet been signed with the developer's upload key.

## Table of Contents

- [Features](#features)
- [Configuration and Architecture](#configuration-and-architecture)
- [Development Requirements](#development-requirements)
- [Build and Testing](#build-and-testing)
- [Running on an Emulator or Phone](#running-on-an-emulator-or-phone)
- [Building a Release Version and Signing the AAB](#building-a-release-version-and-signing-the-aab)
- [Google Play Preparation Checklist](#google-play-preparation-checklist)
- [16 KB Compatibility Verification](#16-kb-compatibility-verification)
- [Known Limitations](#known-limitations)
- [Stockfish License](#stockfish-license)

## Features

- Generates legal moves; detects check, checkmate, stalemate, and special chess rules, including castling, en passant, and pawn promotion.
- Supports FEN, SAN, and PGN; move undo; PGN import through the file picker; and PGN sharing.
- Play against Stockfish 19 offline, choose White or Black, adjust the Skill Level, view move suggestions, and analyze multiple variations.
- Fixed 10-minute chess clock for each player, saved with the current game and restored when the application is reopened.
- Local game history using Room; resume the most recent game, reopen saved games, or review saved games.
- Review mode allows users to navigate through individual positions; Stockfish analyzes each position at depth 10, and the application displays move evaluations. These are engine-based estimates, not official rankings.
- Choose the system/light/dark theme; switch the in-app language between English, Vietnamese, Simplified Chinese, Spanish, Korean, Japanese, and French.
- The Training screen currently displays a notification indicating that no licensed puzzle collection is available; the application does not display sample data as actual puzzles.

## Configuration and Architecture

| Property | Value |
|---|---|
| Android application ID | `com.offlinechess` |
| `minSdk` | 26 (Android 8.0) |
| `compileSdk` / `targetSdk` | 36 (Android 16) |
| Current application version | `0.1.0` (`versionCode` 1) |
| Packaged native ABI | `arm64-v8a` |
| Gradle wrapper | 8.13 |
| Android Gradle Plugin | 8.9.2 |
| Kotlin | 2.0.21 |
| NDK / CMake | NDK 27.1.12297006 / CMake 3.22.1 |

- `app/src/main/java/com/offlinechess/chess/`: chess rules, PGN handling, and `ChessGameViewModel`.
- `app/src/main/java/com/offlinechess/engine/`: Kotlin-side UCI communication.
- `app/src/main/cpp/jni/`: JNI bridge; Stockfish is linked as a native library and does not run as an executable or through `ProcessBuilder`.
- `app/src/main/cpp/stockfish/`: Stockfish source code.
- `app/src/main/java/com/offlinechess/data/`: game history storage using Room and preferences using DataStore.
- `app/src/main/java/com/offlinechess/ui/`: Compose screens.
- `app/src/main/res/values*/`: UI strings and translations.
- `app/src/test/`: tests for chess rules, PGN, UCI analysis, and evaluation classification.

The NNUE network is bundled to allow the engine to operate offline; the application copies the network into its private storage directory when Stockfish is initialized for the first time. This is the main reason for the large installation package.

## Development Requirements

On Windows, the following are required:

- Android Studio or Android SDK command-line tools.
- JDK 17.
- Android SDK Platform 36 and Android Build Tools 36.
- NDK `27.1.12297006` and CMake `3.22.1` (can be installed through **Android Studio → SDK Manager → SDK Tools**).
- Sufficient free disk space for the Stockfish source code, SDK/NDK, Gradle cache, and APK/AAB files; the current debug build is approximately 95 MB.

The project pins the NDK/CMake versions in Gradle. Open the project in Android Studio and wait for Gradle Sync to complete; alternatively, use the Gradle wrapper in PowerShell from the project root directory.

## Build and Testing

Run unit tests and build the debug APK:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```

Output APK:

```text
app\build\outputs\apk\debug\app-debug.apk
```

Build the unsigned release variant using:

```powershell
.\gradlew.bat assembleRelease
```

The output is `app\build\outputs\apk\release\app-release-unsigned.apk`.

Build the Android App Bundle:

```powershell
.\gradlew.bat bundleRelease
```

Current output:

```text
app\build\outputs\bundle\release\app-release.aab
```

The command above builds an AAB **without the developer's personal upload key**; therefore, do not upload this bundle to Play Console as a signed release build. See [Building a Release Version and Signing the AAB](#building-a-release-version-and-signing-the-aab).

## Running on an Emulator or Phone

### Android Studio Emulator

1. Open **Tools → Device Manager** and create/start a virtual device. An Android 14 emulator has been used to test the main workflows; an Android 16 virtual device should also be added for compatibility testing.
2. Select an ARM64 configuration if available. The APK currently only packages `arm64-v8a`; x86_64 emulators may require native translation and are not guaranteed to be supported.
3. Select the `debug` build variant, click **Run**, or install the APK using:

```powershell
.\gradlew.bat installDebug
```

### Android Phone via USB

1. Enable **Developer options** and **USB debugging** on your phone; connect it via USB and accept the authorization prompt.
2. Check the connected device:

```powershell
adb devices
```

3. Install and launch the application:

```powershell
.\gradlew.bat installDebug
adb shell monkey -p com.offlinechess 1
```

The phone must run Android 8.0 or later and support the ARM64 ABI. To install a manually built APK:

```powershell
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
```

### Recommended Test Scenarios

- Start a new game as White, make a move, and wait for Stockfish to respond; repeat with Black selected.
- Test move undo, move suggestions, suggestion arrows, position analysis, and review of saved games.
- Wait a few minutes to verify that the chess clock is running; close and reopen the application to check that the game and remaining time are restored.
- Test importing a valid PGN file, sharing PGN, opening game history, and deleting a game.
- Change the language, switch between light/dark themes, adjust the Skill Level, rotate the screen, and put the application in the background.
- Check error logs when necessary:

```powershell
adb logcat -s AndroidRuntime OfflineChessStockfish
```

The Android 14 emulator has been tested for engine gameplay, game history, review mode, and the chess clock. Physical devices and Android 16 have not yet been tested in the current development environment.

## Building a Release Version and Signing the AAB

Do not share or commit the keystore, passwords, aliases, or private keys. Losing the upload key may complicate the application update process.

1. Open the project in Android Studio.
2. Select **Build → Generate Signed Bundle / APK… → Android App Bundle**.
3. Select the `app` module. Create a new upload keystore or select an existing securely managed keystore; record its file path, alias, and password in a secure location.
4. Select the `release` build variant and complete the wizard to generate a signed AAB.
5. Back up the keystore offline and configure **Play App Signing** in Play Console. The upload key is used to verify uploaded releases; Google Play manages the app-signing key if Play App Signing is enabled.
6. Keep the application ID unchanged after release. Increment `versionCode` for each upload; update `versionName` when necessary in `app/build.gradle.kts`.
7. Verify the signature and bundle contents before distribution. Do not use the debug keystore as a release signing key.

You can also generate a signed APK using the same wizard to test the release version. Debug and release builds use different signing keys; when installing an APK signed with a different key over an existing application, Android may require you to uninstall the previous version first.

## Google Play Preparation Checklist

A successful build does not mean the application is ready for release or guaranteed to be approved by Google Play. Before publishing:

1. Review the current target API requirements in Play Console. The project currently targets API 36.
2. Complete the app information: name, description, icon, screenshots, category, and support email.
3. Complete the applicable Play Console forms: Data safety, content rating, target audience, advertising, app access, and privacy policy if required. Declare the actual application behavior; do not assume that being offline automatically exempts the application from these forms.
4. Check the permissions in the merged manifest. The application currently does not declare `INTERNET`; PGN import uses the file picker provided by Android.
5. Upload a **signed AAB** to Internal testing first; install the application from the Play testing link and test it on the devices distributed by Play.
6. Fix issues identified in the pre-launch report; check crashes/ANRs, Android 16 compatibility, ARM64 devices, and 16 KB page-size compatibility.
7. Use Closed testing, Open testing, or Production according to the account's eligibility and the requirements currently displayed in Play Console. Some new personal developer accounts must meet Google's specified testing duration and tester-count requirements before requesting Production access.
8. Submit the application to Production only after the store listing, policies, testing, signing, and user support arrangements are complete.

The application does not include a licensed puzzle collection. If you want to release Training as a feature with actual puzzle content, you need to select a properly licensed puzzle source, document the license terms/attribution requirements, and test offline distribution.

## 16 KB Compatibility Verification

The application includes a JNI/C++ library named `libstockfish.so`. CMake sets the linker option `-Wl,-z,max-page-size=16384`; the current ELF build has been checked, and its `LOAD` segments report an alignment of `0x4000`.

The following checks can be performed after building on Windows:

```powershell
$readelf = "$env:LOCALAPPDATA\Android\Sdk\ndk\27.1.12297006\toolchains\llvm\prebuilt\windows-x86_64\bin\llvm-readelf.exe"
& $readelf -l .\app\build\intermediates\stripped_native_libs\release\stripReleaseDebugSymbols\out\lib\arm64-v8a\libstockfish.so

$zipalign = "$env:LOCALAPPDATA\Android\Sdk\build-tools\36.0.0\zipalign.exe"
& $zipalign -c -P 16 -v 4 .\app\build\outputs\apk\debug\app-debug.apk
```

The commands above check artifact alignment but do not replace runtime testing. Create an emulator/device image with a 16 KB page size according to Android's instructions, install the application, and test native library initialization, NNUE loading, engine gameplay, saving/restoring games, and review mode. Repeat these checks after each update to the NDK, CMake, native libraries, or packaging configuration.

## Known Limitations

- The chess clock is fixed at 10 minutes per player, with no increment or customizable time control.
- Review mode analyzes each position using Stockfish at depth 10; results depend on the engine and its settings and are not official tournament evaluations.
- No licensed puzzle collection is available.
- Only the `arm64-v8a` ABI is packaged; 32-bit ARM and x86 devices are not directly supported.
- The NNUE network increases the APK size: the debug APK is approximately 95 MB, while the release APK is approximately 89 MB in the tested builds. The AAB is approximately 88 MB before Play generates device-optimized APKs.
- The Android 14 emulator has been tested; Android 16, 16 KB page-size runtime compatibility, and physical devices still require testing.
- The AAB generated using the Gradle command in the repository has not been signed with a personal upload key. Sign the bundle and configure Play App Signing before release.

## Stockfish License

The bundled engine source code is Stockfish 19, licensed under GPLv3. The source code and license are located in `app\src\main\cpp\stockfish\` and `app\src\main\cpp\stockfish\Copying.txt`. The application also bundles the NNUE network for offline engine evaluation. Before distribution, review and comply with the license obligations applicable to the source code/binaries and accompanying resources; this README does not replace legal advice.



==================

# Offline Chess

Ứng dụng cờ vua Android native, viết bằng Kotlin và Jetpack Compose, chạy luật cờ và Stockfish trên thiết bị. Ván cờ, lịch sử và tùy chọn được xử lý cục bộ. Ứng dụng hiện không khai báo quyền Internet.

> Trạng thái phát triển: đã build và chạy thử trên emulator Android 14. Đã build với `compileSdk`/`targetSdk` 36 và kiểm tra căn chỉnh ELF 16 KB cho thư viện native. Chưa kiểm thử trên thiết bị Android 16 hoặc thiết bị có page size 16 KB; chưa có bộ puzzle được cấp phép; AAB hiện chưa ký bằng upload key của nhà phát triển.

## Mục lục

- [Tính năng](#tính-năng)
- [Cấu hình và kiến trúc](#cấu-hình-và-kiến-trúc)
- [Yêu cầu phát triển](#yêu-cầu-phát-triển)
- [Build và kiểm thử](#build-và-kiểm-thử)
- [Chạy thử trên emulator hoặc điện thoại](#chạy-thử-trên-emulator-hoặc-điện-thoại)
- [Build bản phát hành và ký AAB](#build-bản-phát-hành-và-ký-aab)
- [Danh sách chuẩn bị Google Play](#danh-sách-chuẩn-bị-google-play)
- [Kiểm tra tương thích 16 KB](#kiểm-tra-tương-thích-16-kb)
- [Giới hạn đã biết](#giới-hạn-đã-biết)
- [Giấy phép Stockfish](#giấy-phép-stockfish)

## Tính năng

- Sinh nước đi hợp lệ; nhận biết chiếu, chiếu hết, hòa và các luật nhập thành, bắt tốt qua đường, phong cấp.
- FEN, SAN, PGN; hoàn tác nước đi; nhập PGN bằng trình chọn tệp và chia sẻ PGN.
- Chơi với Stockfish 19 offline, chọn bên Trắng/Đen, điều chỉnh Skill Level, xem gợi ý và phân tích nhiều biến.
- Đồng hồ cố định 10 phút mỗi bên, lưu cùng ván đang chơi và tiếp tục sau khi mở lại ứng dụng.
- Lịch sử ván cục bộ bằng Room; tiếp tục ván gần nhất, mở lại hoặc xem lại ván đã lưu.
- Chế độ review cho phép duyệt từng vị trí; Stockfish phân tích mỗi vị trí ở depth 10 và ứng dụng hiển thị mức đánh giá nước đi. Đây là ước tính từ engine, không phải xếp hạng chính thức.
- Chọn giao diện theo hệ thống/sáng/tối; đổi ngôn ngữ trong ứng dụng: English, Tiếng Việt, 简体中文, Español, 한국어, 日本語, Français.
- Màn Training hiện thông báo chưa có bộ câu đố được cấp phép; ứng dụng không hiển thị dữ liệu mẫu như puzzle thật.

## Cấu hình và kiến trúc

| Thuộc tính | Giá trị |
|---|---|
| Android application ID | `com.offlinechess` |
| `minSdk` | 26 (Android 8.0) |
| `compileSdk` / `targetSdk` | 36 (Android 16) |
| Phiên bản ứng dụng hiện tại | `0.1.0` (`versionCode` 1) |
| ABI native đóng gói | `arm64-v8a` |
| Gradle wrapper | 8.13 |
| Android Gradle Plugin | 8.9.2 |
| Kotlin | 2.0.21 |
| NDK / CMake | NDK 27.1.12297006 / CMake 3.22.1 |

- `app/src/main/java/com/offlinechess/chess/`: luật cờ, PGN và `ChessGameViewModel`.
- `app/src/main/java/com/offlinechess/engine/`: giao tiếp UCI phía Kotlin.
- `app/src/main/cpp/jni/`: JNI bridge; Stockfish được link thành thư viện native, không chạy executable hoặc `ProcessBuilder`.
- `app/src/main/cpp/stockfish/`: source Stockfish.
- `app/src/main/java/com/offlinechess/data/`: lưu lịch sử bằng Room và tùy chọn bằng DataStore.
- `app/src/main/java/com/offlinechess/ui/`: các màn hình Compose.
- `app/src/main/res/values*/`: chuỗi giao diện và bản dịch.
- `app/src/test/`: kiểm thử luật cờ, PGN, phân tích UCI và phân loại đánh giá.

Mạng NNUE được đóng gói để engine hoạt động offline; ứng dụng sao chép mạng vào vùng lưu trữ riêng khi khởi tạo Stockfish lần đầu. Đây là nguyên nhân chính khiến file cài đặt lớn.

## Yêu cầu phát triển

Trên Windows cần:

- Android Studio hoặc Android SDK command-line tools.
- JDK 17.
- Android SDK Platform 36 và Android Build Tools 36.
- NDK `27.1.12297006` và CMake `3.22.1` (có thể cài từ **Android Studio → SDK Manager → SDK Tools**).
- Dung lượng trống đủ cho source Stockfish, SDK/NDK, Gradle cache và APK/AAB; bản debug hiện khoảng 95 MB.

Project pin phiên bản NDK/CMake trong Gradle. Mở project bằng Android Studio và chờ Gradle Sync; hoặc dùng Gradle wrapper trong PowerShell từ thư mục gốc.

## Build và kiểm thử

Chạy unit tests và build APK debug:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```

APK đầu ra:

```text
app\build\outputs\apk\debug\app-debug.apk
```

Build biến thể release chưa ký bằng:

```powershell
.\gradlew.bat assembleRelease
```

Đầu ra là `app\build\outputs\apk\release\app-release-unsigned.apk`.

Build Android App Bundle:

```powershell
.\gradlew.bat bundleRelease
```

Đầu ra hiện tại:

```text
app\build\outputs\bundle\release\app-release.aab
```

Lệnh trên build AAB **chưa có upload key riêng của nhà phát triển**, vì vậy đừng tải bundle đó lên Play Console như bản phát hành đã ký. Xem [Build bản phát hành và ký AAB](#build-bản-phát-hành-và-ký-aab).

## Chạy thử trên emulator hoặc điện thoại

### Emulator trong Android Studio

1. Mở **Tools → Device Manager** và tạo/khởi động thiết bị ảo. Emulator Android 14 đã được dùng để kiểm tra các luồng chính; nên bổ sung thiết bị ảo Android 16 cho kiểm thử tương thích.
2. Chọn cấu hình ARM64 nếu có. APK hiện chỉ đóng gói `arm64-v8a`; emulator x86_64 có thể cần native translation và không phải môi trường đảm bảo được hỗ trợ.
3. Chọn cấu hình `debug`, nhấn **Run**, hoặc cài APK bằng:

```powershell
.\gradlew.bat installDebug
```

### Điện thoại Android qua USB

1. Bật **Developer options** và **USB debugging** trên điện thoại; kết nối USB và chấp nhận hộp thoại cấp quyền.
2. Kiểm tra thiết bị:

```powershell
adb devices
```

3. Cài và mở ứng dụng:

```powershell
.\gradlew.bat installDebug
adb shell monkey -p com.offlinechess 1
```

Điện thoại phải chạy Android 8.0 trở lên và có ABI ARM64. Để cài APK đã build thủ công:

```powershell
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
```

### Các luồng nên thử

- Bắt đầu ván mới với Trắng, đi một nước và chờ Stockfish trả lời; lặp lại với lựa chọn chơi Đen.
- Thử hoàn tác, gợi ý nước đi, mũi tên gợi ý, phân tích thế cờ và review ván đã lưu.
- Chờ vài phút để xác nhận đồng hồ chạy; đóng/mở lại ứng dụng để kiểm tra ván và thời gian được khôi phục.
- Thử nhập một tệp PGN hợp lệ, chia sẻ PGN, mở lịch sử và xóa ván.
- Đổi ngôn ngữ, sáng/tối, Skill Level; xoay màn hình và đưa ứng dụng xuống nền.
- Quan sát log lỗi khi cần:

```powershell
adb logcat -s AndroidRuntime OfflineChessStockfish
```

Emulator Android 14 đã được kiểm tra chơi engine, lịch sử, review và đồng hồ. Thiết bị thật và Android 16 chưa được kiểm thử trong môi trường phát triển hiện tại.

## Build bản phát hành và ký AAB

Không chia sẻ hoặc commit keystore, mật khẩu, alias hay private key. Mất upload key có thể làm phức tạp quy trình cập nhật ứng dụng.

1. Mở project trong Android Studio.
2. Chọn **Build → Generate Signed Bundle / APK… → Android App Bundle**.
3. Chọn module `app`. Tạo upload keystore mới hoặc chọn keystore đã quản lý an toàn; ghi lại đường dẫn, alias và mật khẩu ở nơi bảo mật.
4. Chọn build variant `release`, hoàn tất wizard để tạo AAB đã ký.
5. Sao lưu keystore ngoại tuyến và thiết lập **Play App Signing** trong Play Console. Upload key dùng để xác nhận bản tải lên; Google Play quản lý app-signing key nếu bật Play App Signing.
6. Giữ nguyên application ID sau khi phát hành. Tăng `versionCode` cho mỗi lần upload; cập nhật `versionName` khi cần trong `app/build.gradle.kts`.
7. Kiểm tra chữ ký và nội dung bundle trước khi phân phối. Không dùng debug keystore làm khóa phát hành.

Có thể tạo APK đã ký từ wizard tương tự để cài thử bản release. Bản debug và release dùng chữ ký khác nhau; khi cài APK đã ký khác khóa lên trên ứng dụng hiện có, Android có thể yêu cầu gỡ bản cũ trước.

## Danh sách chuẩn bị Google Play

Việc build thành công không đồng nghĩa ứng dụng đã sẵn sàng hoặc chắc chắn được Google Play duyệt. Trước khi phát hành:

1. Xem lại yêu cầu target API hiện hành trong Play Console. Project đang target API 36.
2. Hoàn tất thông tin app: tên, mô tả, icon, ảnh chụp màn hình, danh mục và email hỗ trợ.
3. Hoàn tất các biểu mẫu Play Console phù hợp: Data safety, content rating, target audience, quảng cáo, quyền truy cập ứng dụng và privacy policy nếu được yêu cầu. Khai báo đúng hành vi thực tế; không suy luận rằng “offline” tự động miễn các biểu mẫu.
4. Kiểm tra quyền trong merged manifest. Ứng dụng hiện không khai báo `INTERNET`; import PGN dùng trình chọn tệp do Android cung cấp.
5. Upload **AAB đã ký** lên Internal testing trước; cài từ liên kết Play và kiểm tra các thiết bị được Play phân phối.
6. Sửa lỗi từ pre-launch report, kiểm tra crash/ANR, tương thích Android 16, thiết bị ARM64 và page size 16 KB.
7. Dùng Closed testing, Open testing hoặc Production theo quyền của tài khoản và điều kiện đang hiển thị trong Play Console. Một số tài khoản cá nhân mới phải đáp ứng thời gian và số lượng tester do Google quy định trước khi xin quyền Production.
8. Chỉ gửi Production khi nội dung cửa hàng, chính sách, thử nghiệm, chữ ký và hỗ trợ người dùng đã hoàn tất.

Ứng dụng chưa kèm puzzle có bản quyền. Nếu muốn phát hành Training như một tính năng có nội dung, cần chọn nguồn puzzle có giấy phép phù hợp, ghi nhận điều khoản/attribution và kiểm thử việc phân phối offline.

## Kiểm tra tương thích 16 KB

Ứng dụng có thư viện JNI/C++ `libstockfish.so`. CMake đặt linker option `-Wl,-z,max-page-size=16384`; ELF build hiện tại đã kiểm tra và các `LOAD` segment báo alignment `0x4000`.

Có thể kiểm tra sau khi build trên Windows:

```powershell
$readelf = "$env:LOCALAPPDATA\Android\Sdk\ndk\27.1.12297006\toolchains\llvm\prebuilt\windows-x86_64\bin\llvm-readelf.exe"
& $readelf -l .\app\build\intermediates\stripped_native_libs\release\stripReleaseDebugSymbols\out\lib\arm64-v8a\libstockfish.so

$zipalign = "$env:LOCALAPPDATA\Android\Sdk\build-tools\36.0.0\zipalign.exe"
& $zipalign -c -P 16 -v 4 .\app\build\outputs\apk\debug\app-debug.apk
```

Các lệnh trên kiểm tra alignment artifact, không thay thế kiểm thử runtime. Tạo emulator/device image page size 16 KB theo hướng dẫn Android, cài app và kiểm tra khởi động thư viện native, tải NNUE, chơi engine, lưu/khôi phục và review. Kiểm tra lại sau mỗi lần cập nhật NDK, CMake, thư viện native hoặc cấu hình đóng gói.

## Giới hạn đã biết

- Đồng hồ cố định 10 phút mỗi bên, không có increment hoặc chọn thời lượng.
- Review phân tích từng vị trí bằng Stockfish depth 10; kết quả phụ thuộc engine/cài đặt và không phải đánh giá giải đấu chính thức.
- Không có bộ puzzle được cấp phép.
- Chỉ đóng gói ABI `arm64-v8a`; không hỗ trợ trực tiếp thiết bị 32-bit ARM hoặc x86.
- NNUE làm APK lớn: debug khoảng 95 MB, release APK khoảng 89 MB trong các build đã kiểm tra. AAB khoảng 88 MB trước khi Play tạo APK tối ưu theo thiết bị.
- Emulator Android 14 đã được kiểm tra; Android 16, page size 16 KB runtime và thiết bị vật lý vẫn cần kiểm thử.
- AAB tạo bằng lệnh Gradle trong repo chưa được ký bằng upload key cá nhân. Ký và thiết lập Play App Signing trước khi phát hành.

## Giấy phép Stockfish

Source engine đi kèm là Stockfish 19 theo GPLv3. Source và giấy phép nằm trong `app\src\main\cpp\stockfish\` và `app\src\main\cpp\stockfish\Copying.txt`. Ứng dụng cũng đóng gói mạng NNUE để engine đánh giá offline. Trước khi phân phối, hãy rà soát và đáp ứng các nghĩa vụ của giấy phép đối với source/binary và các tài nguyên đi kèm; README này không thay thế tư vấn pháp lý.
