# Zyqo

Ứng dụng Android gốc (Kotlin, Jetpack Compose). Dán khóa API, gửi file zip, AI đọc, sửa dự án và trả zip mới về.

## Nhà cung cấp
Anthropic, OpenAI, Google Gemini (khóa `AQ.` mới và `AIza` cũ), Groq, OpenRouter. Loại khóa được nhận diện tự động, khóa lạ thì chọn tay.

## Độ ổn định khóa API
- Lưu nhiều khóa cùng lúc, dán nhiều khóa một lần (cách nhau bằng dấu cách hoặc xuống dòng).
- Khóa bị từ chối hoặc hết hạn mức: tự chuyển sang khóa khác, khóa lỗi bị loại khỏi phiên.
- Chạm giới hạn tốc độ: đọc Retry-After, chờ ngắn nếu hợp lý, không thì cho khóa hoặc model đó nghỉ rồi chuyển tiếp.
- Mỗi khóa có danh sách model dự phòng xếp hạng tự động, model hỏng thì thử model kế tiếp.
- Yêu cầu quá lớn (ví dụ Groq bản miễn phí): tự co ngữ cảnh dự án rồi thử lại.
- Khóa Gemini `AQ.` dùng đường native với header `x-goog-api-key`. Đường tương thích OpenAI của Google không chấp nhận khóa này nên không dùng.

## Giao thức làm việc với dự án
- `edit` với cặp find/replace: sửa cục bộ, tiết kiệm token, khớp sai sẽ được báo ngược cho AI tự sửa.
- `read`: AI tự đọc file chưa nạp, tối đa 3 vòng tự động.
- `file` và `delete`: tạo, viết lại, xóa file. Có hoàn tác 10 bước.
- Anthropic dùng prompt caching cho phần dự án.

## Công cụ web (tìm kiếm và đọc trang)
- Mọi nhà cung cấp đều dùng được, kể cả Groq, vì công cụ chạy trong app: `search` và `fetch`.
- Tìm kiếm qua DuckDuckGo không cần khóa. Dán khóa Tavily (`tvly-...`) để kết quả sạch và ổn định hơn, tự quay về DuckDuckGo nếu Tavily lỗi.
- Tiết kiệm token: kết quả tìm chỉ gồm 5 mục ngắn; `fetch` bỏ menu, script, chân trang rồi chỉ giữ các đoạn khớp điều cần tìm, giới hạn theo nhà cung cấp; có bộ nhớ đệm; kết quả công cụ không lưu vào lịch sử trò chuyện; tin nhắn cũ được rút gọn khi gửi lại.
- An toàn: chỉ https, chặn địa chỉ nội bộ (localhost, mạng LAN, kể cả sau chuyển hướng), nội dung web được đánh dấu là dữ liệu không đáng tin để chống chèn lệnh.

## Công cụ cho AI agent và MCP
- Một lớp gọi công cụ thống nhất: `<call tool="ten">{json}</call>`, chạy được với mọi nhà cung cấp. Tham số được kiểm tra theo schema, sai thì báo ngược để AI tự sửa.
- Công cụ dự án: `list_files`, `grep` (regex có giới hạn thời gian), `read_file` (theo khoảng dòng), `validate`, `tool_help`. AI tìm rồi đọc đúng đoạn cần thay vì nạp cả file, tiết kiệm token trên dự án lớn.
- Tự kiểm tra sau mỗi lần sửa: cân bằng ngoặc, JSON, XML, dòng viết tắt kiểu `... existing`, dấu xung đột merge. Phát hiện lỗi thì AI được báo để sửa ngay (tối đa 2 lần mỗi lượt). Đây là cảnh báo heuristic, không thay cho việc build thật.
- MCP client qua HTTP (Streamable HTTP, https). Thêm máy chủ ở màn Công cụ, app tự `initialize`, `tools/list`, `tools/call`, giữ phiên, đọc cả phản hồi JSON lẫn SSE. Không hỗ trợ stdio (điện thoại không chạy được tiến trình máy chủ) và transport SSE cũ.
- Khi có nhiều hơn 12 công cụ, danh mục chỉ ghi tên, AI gọi `tool_help` để xem tham số, giúp tiết kiệm token.
- An toàn: công cụ MCP không đánh dấu `readOnlyHint` sẽ hỏi bạn trước khi chạy (Cho phép, Luôn cho phép, Từ chối). Chỉ bật Tin cậy với máy chủ bạn kiểm soát. Kết quả công cụ được coi là dữ liệu không đáng tin.
- Môi trường thực thi: dự án nằm trong không gian ảo trong app, có hoàn tác 10 bước và không chạy mã trên máy. Muốn chạy test, build hay shell thật, kết nối một máy chủ MCP sandbox.

## Kỹ năng
15 kỹ năng dựng sẵn (nghiên cứu công nghệ, kỹ sư tự trị, thêm tính năng, sửa lỗi, review, kiến trúc, tái cấu trúc, test, bảo mật, hiệu năng, di trú, CI/CD, API, tài liệu, tổng quan) và kỹ năng riêng do bạn tự viết.

## Nhật ký thay đổi
Xem CHANGELOG.md.

## Tải APK
1. Tạo repo GitHub, tải toàn bộ thư mục lên (giữ thư mục .github).
2. Tab Actions, chạy "Build APK". Job `test` chạy unit test, job `build` tạo APK.
3. Tải artifact zyqo-debug-apk, cài app-debug.apk.

## Bản ký chính thức (tùy chọn)
Thêm secret: KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD. Workflow tự build thêm release (R8) và AAB.
