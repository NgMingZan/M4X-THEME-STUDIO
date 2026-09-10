package com.m4x.themestudio.core

object OfflineTranslator {
    private val dictionary = linkedMapOf(
        "超级壁纸" to "Siêu hình nền",
        "锁屏壁纸" to "Hình nền màn hình khóa",
        "桌面壁纸" to "Hình nền màn hình chính",
        "锁屏" to "Màn hình khóa",
        "桌面" to "Màn hình chính",
        "主屏幕" to "Màn hình chính",
        "状态栏" to "Thanh trạng thái",
        "通知栏" to "Bảng thông báo",
        "控制中心" to "Trung tâm điều khiển",
        "控制中心样式" to "Kiểu trung tâm điều khiển",
        "图标" to "Biểu tượng",
        "字体" to "Phông chữ",
        "铃声" to "Nhạc chuông",
        "短信" to "Tin nhắn",
        "电话" to "Điện thoại",
        "联系人" to "Danh bạ",
        "设置" to "Cài đặt",
        "主题" to "Chủ đề",
        "壁纸" to "Hình nền",
        "天气" to "Thời tiết",
        "日期" to "Ngày",
        "时间" to "Thời gian",
        "星期日" to "Chủ nhật",
        "星期一" to "Thứ hai",
        "星期二" to "Thứ ba",
        "星期三" to "Thứ tư",
        "星期四" to "Thứ năm",
        "星期五" to "Thứ sáu",
        "星期六" to "Thứ bảy",
        "星期天" to "Chủ nhật",
        "星期" to "Thứ",
        "农历" to "Âm lịch",
        "电池" to "Pin",
        "充电" to "Đang sạc",
        "已充满" to "Đã sạc đầy",
        "音乐" to "Âm nhạc",
        "暂无音乐" to "Không phát nhạc",
        "暂停" to "Tạm dừng",
        "播放" to "Phát",
        "上一首" to "Bài trước",
        "下一首" to "Bài tiếp",
        "相机" to "Máy ảnh",
        "手电筒" to "Đèn pin",
        "点击解锁" to "Chạm để mở khóa",
        "上滑解锁" to "Vuốt lên để mở khóa",
        "双击" to "Nhấn đúp",
        "打开" to "Mở",
        "关闭" to "Tắt",
        "开启" to "Bật",
        "确定" to "Xác nhận",
        "取消" to "Hủy",
        "保存" to "Lưu",
        "应用" to "Áp dụng",
        "更多" to "Thêm",
        "关于" to "Giới thiệu",
        "作者" to "Tác giả",
        "版本" to "Phiên bản",
        "默认" to "Mặc định",
        "自定义" to "Tùy chỉnh",
        "选择" to "Chọn",
        "图片" to "Hình ảnh",
        "颜色" to "Màu sắc",
        "透明度" to "Độ trong suốt",
        "亮度" to "Độ sáng",
        "模糊" to "Làm mờ",
        "动画" to "Hoạt ảnh",
        "通知" to "Thông báo",
        "未读" to "Chưa đọc",
        "无通知" to "Không có thông báo",
        "闹钟" to "Báo thức",
        "日历" to "Lịch",
        "今天" to "Hôm nay",
        "明天" to "Ngày mai",
        "昨天" to "Hôm qua",
        "Lock screen" to "Màn hình khóa",
        "Home screen" to "Màn hình chính",
        "Status bar" to "Thanh trạng thái",
        "Notification" to "Thông báo",
        "Control center" to "Trung tâm điều khiển",
        "Wallpaper" to "Hình nền",
        "No notifications" to "Không có thông báo",
        "No music" to "Không phát nhạc"
    )

    data class Result(val text: String, val replacements: Int)

    fun translate(input: String, customPairs: Map<String, String> = emptyMap()): Result {
        var text = input
        var count = 0
        val pairs = (dictionary + customPairs).entries.sortedByDescending { it.key.length }
        for ((from, to) in pairs) {
            if (from.isBlank() || from == to) continue
            var localCount = 0
            var idx = text.indexOf(from)
            while (idx >= 0) {
                localCount++
                idx = text.indexOf(from, idx + from.length)
            }
            if (localCount > 0) {
                text = text.replace(from, to)
                count += localCount
            }
        }
        return Result(text, count)
    }
}
