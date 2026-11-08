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
