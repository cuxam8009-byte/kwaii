# Pigprolem Utit

Ứng dụng Android học Python cho người mới bắt đầu, có chú heo làm bạn đồng hành. Kotlin và Jetpack Compose.

## Tải APK
Cách 1 (như hiện tại): để file `project.zip` ở thư mục gốc repo, workflow tự giải nén và build.
Cách 2: giải nén trực tiếp vào repo, workflow tự nhận ra `settings.gradle.kts`.
Vào tab Actions, chạy "Build APK", tải artifact `pigprolem-utit-apk`.
Workflow mới trong `.github/workflows/build-apk.yml` chạy thêm bài kiểm tra nội dung giáo trình trước khi build.

## Cấu trúc
- `Course.kt`: giáo trình, được sinh tự động, đừng sửa tay.
- `Data.kt`: kiểu dữ liệu bài học, chọn câu ôn tập, tính sao.
- `Game.kt`: tiến độ, chuỗi ngày, mục tiêu, cài đặt, lưu trữ.
- `Home.kt`: trang chính, màn kết quả, cài đặt, hộp xác nhận.
- `Lesson.kt`: màn học (xem code, điền chỗ trống, đoán kết quả, bắt bug, sắp xếp dòng).
- `Theme.kt`, `Pig.kt`, `Sfx.kt`: giao diện, chú heo, âm thanh.

## Sửa hoặc thêm bài học
Giáo trình nằm trong `tools/course_src.py`, mỗi ví dụ code đều được chạy thật để kiểm tra.
```
cd tools
python3 verify.py
python3 gen_kotlin.py ../app/src/main/java/com/pigprolem/utit/Course.kt
```
`verify.py` báo lỗi nếu: kết quả hiển thị không khớp kết quả chạy thật, đáp án sai có thể cũng cho kết quả đúng, dòng bug không phải dòng gây lỗi, hoặc bài sắp xếp có nhiều hơn một thứ tự đúng.
