# Changelog

## 0.5.0-p4 (Giai đoạn 4: ngữ cảnh và chi phí)
- Hướng dẫn dự án: tự đọc `ZYQO.md`, `AGENTS.md` hoặc `CLAUDE.md` (ở gốc hoặc một cấp thư mục), giới hạn độ dài, gắn nhãn là dữ liệu của dự án chứ không phải lệnh hệ thống.
- Chỉ mục tóm tắt trong prompt nhẹ: lớp, đối tượng, giao diện theo thư mục, thay cho nội dung file.
- Sổ ghi chú theo dự án: công cụ `note_add` và `note_list`, ghi chú lưu trên máy, nạp lại ở phiên sau (tối đa 30 ghi chú, 300 ký tự mỗi ghi chú).
- Che quan sát cũ: kết quả công cụ cũ hơn 3 lượt thay bằng một dòng; dữ liệu gốc của Gemini (chữ ký suy nghĩ) không bị đụng.
- Tóm tắt lịch sử dài: phần bị lược bớt được thay bằng bản tóm tắt ngắn, không tốn thêm lệnh gọi model.
- `grep` thêm tham số `context` (0 đến 3 dòng); `read_file` báo cách đọc tiếp khi bị cắt.
- Đo token thật: đọc số token vào và ra từ Anthropic, Groq, OpenAI, Gemini, ghi vào Nhật ký gỡ lỗi, tự hiệu chỉnh hệ số ước lượng.
- Thứ tự prompt cố định, phần thay đổi (hướng dẫn, ghi chú) nằm cuối để giữ tiền tố cho cache.
- Test: `Phase4Test`.
- Chưa làm: tóm tắt bằng model rẻ, định tuyến model theo tác vụ, lưu hệ số hiệu chỉnh giữa các phiên.

## 0.5.0-p3 (Giai đoạn 3: bám sát sự thật)
- `Index.kt`: chỉ mục khai báo đa ngôn ngữ (Kotlin, Java, JS/TS, Python, Go, Rust, C#, Swift...) gồm hàm, lớp, đối tượng, giao diện, kiểu, hằng. Dựng khi cần, tự làm mới sau mỗi lần sửa hoặc hoàn tác.
- Công cụ mới: `find_symbol` (kèm gợi ý tên gần giống khi không thấy), `edit_file` (cặp chuỗi cũ và mới, duy nhất hoặc `replace_all`, giữ CRLF), `write_file`, `delete_file`. Các thay đổi đi qua cùng cơ chế hoàn tác và kiểm tra cú pháp như khối `<edit>`.
- Quy tắc đọc trước khi sửa: `edit_file` và ghi đè bằng `write_file` bị từ chối nếu file chưa được đọc trong phiên. Khi `old_string` không khớp, trả về đoạn gần giống nhất và số dòng.
- `Ground.kt`: sau câu trả lời cuối, tự đối chiếu đường dẫn (khớp theo tên file nếu thiếu thư mục), `file:dòng` có chứa định danh được nêu, định danh trong dấu backtick có trong chỉ mục hoặc nội dung, và đoạn code trích dẫn có khớp file. Bỏ qua câu mang tính đề xuất, URL và lượt đã sửa file.
- Có tham chiếu sai thì chạy đúng một vòng sửa, ép dùng công cụ. Vẫn sai thì hiện cảnh báo "Chưa xác minh được trong dự án" kèm danh sách, không để câu sai nằm lại.
- Chế độ nhẹ (Groq) giữ `find_symbol`, `edit_file`, `write_file` trong 7 công cụ gửi đi.
- Gỡ chữ của hàm không tồn tại khỏi test và tài liệu để zip Zyqo dùng làm dự án thử cho kịch bản nghiệm thu 2 không bị nhiễu.
- Test: `Phase3Test`.
- Quyết định: không kích hoạt truy xuất chỉ vì "lượt đó không gọi công cụ"; thay vào đó dùng kết quả đối chiếu thật làm điều kiện, vì tham chiếu đúng thì không cần ép thêm một vòng.

## 0.5.0-p2.1 (Preambles và tiến độ)
- Prompt: yêu cầu model viết một câu ngắn nói sẽ làm gì trước lần gọi công cụ đầu, và một dòng ngắn giữa các lượt (cả chế độ gốc lẫn văn bản).
- Dòng thời gian tiến độ trong tin nhắn: mỗi bước công cụ hiện "› đang chạy", rồi "✓ Tìm "x" · 3 kết quả" hoặc "✕ lỗi". Có thêm bước "Tìm sẵn trong dự án", "Áp dụng n thay đổi".
- Tiến độ được lưu cùng tin nhắn (`Msg.steps`), mở lại app vẫn xem được.
- Test: `ProgressTest`.

## 0.5.0-p2 (Giai đoạn 2: tool calling gốc)
- `Native.kt`: adapter tool calling gốc cho Anthropic (`tool_use`, `input_json_delta`, `tool_choice any`), OpenAI-compatible gồm Groq và OpenRouter (`tool_calls` theo `index`, `tool_choice required`), Gemini (`functionCall`, `functionResponse`, `toolConfig ANY`).
- Gemini: giữ nguyên các `parts` của lượt model (kèm `thoughtSignature`) và gửi lại đúng nguyên văn.
- `ChatVm`: vòng lặp công cụ gốc. Công cụ local, MCP và web (`search`, `fetch`) được khai báo theo schema. Lượt đầu ép gọi công cụ khi câu hỏi nhắc đến tên hàm, đường dẫn hay ý định tìm kiếm.
- Tìm sẵn tự động: ứng dụng chạy `grep` thật cho tên định danh trong câu hỏi rồi đính kết quả vào tin nhắn làm bằng chứng, tên không tồn tại cho ra "Không thấy kết quả".
- Router: `runChat` dùng chung cơ chế failover, TPM và đếm ngược. Model lỗi định dạng công cụ được thử lại một lần, sau đó ghi nhận vào sổ năng lực và quay về giao thức văn bản cũ, giữ nguyên lịch sử.
- Đổi nhà cung cấp giữa chừng: nếu lịch sử có dữ liệu gốc của model khác thì được làm phẳng thành văn bản.
- `Fail.TOOLS` mới; sổ năng lực được lưu bền.
- Test: `Phase2Test` với fixture stream cho cả 3 nhà cung cấp.
- Chưa làm: kiểm tra khả năng gọi công cụ ngay khi thêm khóa (hiện học dần khi dùng), nén kết quả công cụ cũ (Giai đoạn 4).

## 0.5.0-p1 (Giai đoạn 1: nền móng)
- Thêm `LlmApi` (interface), `LlmClient` cài đặt nó; `Router` nhận `LlmApi` nên test được bằng bản giả.
- `Tokens.kt`: ước lượng token, đọc `Limit/Used/Requested` từ lỗi hạn mức, dịch lỗi sang tiếng Việt, `TpmGate` (cửa sổ trượt 60 giây, học giới hạn từ lỗi, lưu bền).
- `Router`: chờ có đếm ngược (tối đa 30 giây), chuyển khóa khi có nhiều khóa và chờ dài, co ngữ cảnh khi yêu cầu lớn hơn giới hạn phút, không hiện lỗi thô.
- Chế độ nhẹ: khóa có ngân sách thấp (Groq) không nhúng nội dung file vào prompt hệ thống, trừ dự án rất nhỏ.
- `Canon.kt`, `Caps.kt`: mô hình tin nhắn chuẩn và sổ năng lực model (chưa nối vào adapter, dành cho Giai đoạn 2).
- `Debug.kt` và màn Nhật ký gỡ lỗi trong Cài đặt, khóa API được ẩn. Hiển thị phiên bản app.
- Test: `Phase1Test`, thêm phụ thuộc test `org.json:json`.
- Migration: thêm khóa lưu trữ `limits`, không đổi định dạng dữ liệu cũ.
