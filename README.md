# M4X Theme Studio V2.2 — NO ROOT

Bản này cố ý **không dùng root, Shizuku, su hoặc quyền hệ thống**.

## Luồng sử dụng
1. Chọn `.mtz`, `.zip` hoặc file `lockscreen` bằng Android Storage Access Framework.
2. App sao chép tệp vào vùng riêng của ứng dụng.
3. Tự mở ZIP/MTZ và các component ZIP lồng nhau.
4. Quét XML/MAML và OCR ảnh Trung/Anh.
5. Dịch sang tiếng Việt bằng ML Kit.
6. Ghi thay đổi và đóng gói lại.
7. Xuất vào `Download/M4XThemeStudio`.
8. Mở Xiaomi Theme Manager và nhập/áp dụng thủ công.

## Không cần root cho
- Đọc MTZ/lockscreen do người dùng chọn.
- Việt hóa XML/MAML.
- OCR và chỉnh ảnh.
- Tạo/đóng gói MTZ.
- Xuất tệp ra Downloads.

## Root vẫn là giới hạn của Android/Xiaomi ở đâu?
Không-root **không thể** đọc thẳng dữ liệu riêng của `com.android.thememanager`, tự đồng bộ toàn bộ theme đã cài, xóa theme trong private catalog hoặc ép Theme Manager áp theme bằng API private. Vì vậy V2.2 bỏ hẳn các bước đó và dùng nhập/xuất tệp thủ công.

## Build APK
Upload toàn bộ project lên GitHub → Actions → `Build Android APK` → `Run workflow` → tải artifact `M4X-Theme-Studio-debug`.
