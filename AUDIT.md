# Kiểm toán Giai đoạn 0 (bản 0.4.0)

Trạng thái xác minh: chủ dự án báo `assembleDebug` thành công trên CI. Chưa có kết quả job `test`. Mọi mục dưới đây là kết quả đọc code và grep, chưa chạy test.

## Đã xác minh bằng grep
- `detectProvider`: `Data.kt:73` (định nghĩa), `ChatVm.kt:157`, `SecureStore.kt:80`, cộng `ProvidersTest.kt`.
- Tên hàm "khởi tạo nhà cung cấp" mà một câu trả lời cũ bịa ra: không tồn tại ở bất kỳ đâu.

## Lỗi và nợ kỹ thuật (đọc code)
1. `ChatVm.send`: lời gọi công cụ là thẻ văn bản `<call>`. Không ép gọi ở lượt đầu, không đối chiếu câu trả lời. Không có thẻ nào thì câu trả lời được nhận nguyên.
2. `systemPrompt` + `ProjectData.snapshot(budget)`: nhét nội dung file vào prompt hệ thống, tới 12.000 ký tự với Groq. Phần chữ cố định (BASE, ENGINEERING, PROTOCOL, WEB, TOOLS) khoảng 3.000 ký tự, chưa tính kỹ năng và danh mục công cụ. Gửi lại ở mỗi vòng.
3. `Router.react`: lỗi RATE với chờ trên 8 giây thì chỉ cho cặp khóa-model nghỉ rồi chuyển model khác. Khóa Groq đơn lẻ cùng hạn mức phút nên thử các model kế tiếp vô ích, cuối cùng ném lại lỗi cũ. `ChatVm.finish` hiện `e.message`, chưa dịch.
4. `Router` phụ thuộc trực tiếp `LlmClient` (lớp final), không thay bằng bản giả được. Chặn test failover. Cần `LlmApi` ở Giai đoạn 1.
5. Ngân sách ngữ cảnh tính bằng ký tự (`Provider.budget`, `trimTurns`).
6. `Ui.styled`/`spans`: chỉ xử lý `**` và backtick. `*nghiêng*` hiện nguyên dấu sao.
7. `verifyText`: chỉ cân bằng ngoặc, JSON, XML, dòng viết tắt, dấu xung đột. Không đối chiếu đường dẫn hay định danh.
8. `persist` nuốt mọi ngoại lệ (`catch` rỗng).
9. `security-crypto` bản alpha.

## Test thêm ở đợt này
- `GroundTruthTest` (kỳ vọng pass): cố định đáp án chuẩn của kịch bản nghiệm thu 1 và 2, dùng `LocalTools.grep` trên chính mã nguồn.
- `Phase0ReproTest`:
  - phân loại lỗi Groq 8K TPM (kỳ vọng pass);
  - ước lượng token của prompt Groq với dự án 50 file (kỳ vọng FAIL ở 0.4.0);
  - prompt Groq không nhúng nội dung file (kỳ vọng FAIL ở 0.4.0).
- Chưa viết được test failover, hạn mức, bịa tham chiếu vì cần `LlmApi` và chỉ mục mã (Giai đoạn 1 và 3).
