# M4X Theme Studio V3.0 — NO ROOT

M4X Theme Studio là công cụ Android xử lý theme Xiaomi/HyperOS theo luồng tệp chuẩn Android, không yêu cầu root để đọc, Việt hoá và đóng gói MTZ.

## V3.0 có gì

- Nhập `.mtz`, `.zip`, `.bak` hoặc lockscreen dạng ZIP.
- Kiểm tra cấu trúc MTZ/ZIP trước khi xử lý.
- Chặn archive có đường dẫn bất thường/traversal và giới hạn archive quá lớn.
- Giữ nguyên tệp gốc.
- Xử lý ra tệp tạm, kiểm tra lại rồi mới thay bản output hoàn chỉnh.
- Quét XML/MAML/JSON/text trong theme và ZIP lồng nhau.
- Dịch Trung/Anh/Nhật/Hàn → Việt bằng ML Kit.
- Bảo vệ placeholder như `%s`, `%1$d`, `${...}`, `#{...}`.
- OCR ảnh khi người dùng bật tuỳ chọn.
- Xuất kết quả vào `Download/M4XThemeStudio`.
- GitHub Actions tự build APK với JDK 17 + Android API 35.

## Quy tắc an toàn

V3 không ghi đè source đã nhập. Khi Việt hoá, app tạo file tạm, đóng gói, kiểm tra ZIP lần cuối, sau đó mới tạo bản `_VI`.

## Không root

Không-root vẫn không thể tự đọc dữ liệu riêng của `com.android.thememanager` hoặc ép Xiaomi Theme Manager áp theme bằng private API. Sau khi xuất MTZ, người dùng nhập/áp dụng thủ công trong Theme Manager.

## Build

GitHub → Actions → **Build Android APK** → Run workflow.

Artifact: `M4X-Theme-Studio-V3-debug`
