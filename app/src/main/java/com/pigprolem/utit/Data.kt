package com.pigprolem.utit

sealed class Step { abstract val say: String }
data class Learn(override val say: String, val code: String, val out: List<String>) : Step()
data class Ex(override val say: String, val code: String, val opts: List<String>, val ans: Int,
              val out: List<String>, val why: String, val pred: Boolean = false) : Step()
data class Bug(override val say: String, val lines: List<String>, val bad: Int, val why: String) : Step()
data class Level(val file: String, val title: String, val sub: String, val kind: String, val steps: List<Step>)

val LEVELS = listOf(
    Level("bai_01.py", "In chữ ra màn hình", "Lệnh print", "Bài học", listOf(
        Learn("Code là danh sách lệnh bằng chữ để ra lệnh cho máy tính, giống heo nghe lệnh \"ăn đi!\". Lệnh đầu tiên là print, nghĩa là \"in ra\": bảo máy hiện chữ lên màn hình.",
            "print(\"Ụt ịt\")", listOf("Ụt ịt")),
        Learn("Chữ muốn in phải nằm trong dấu nháy \"...\". Thiếu nháy thì máy tưởng đó là tên một thứ khác và bị rối. Mỗi lệnh print cho ra một dòng riêng.",
            "print(\"Heo đói\")\nprint(\"Cho heo ăn đi!\")", listOf("Heo đói", "Cho heo ăn đi!")),
        Ex("Điền lệnh để máy in ra chữ Xin chào.", "___(\"Xin chào\")", listOf("print", "say", "show"), 0,
            listOf("Xin chào"), "print nghĩa là in ra. say và show không có trong Python."),
        Ex("Chọn cách viết đúng để in ra chữ Ụt ịt.", "print(___)", listOf("\"Ụt ịt\"", "Ụt ịt", "(Ụt ịt)"), 0,
            listOf("Ụt ịt"), "Chữ phải nằm trong dấu nháy kép."),
        Ex("Đoán xem máy sẽ in ra gì?", "print(\"Heo\")\nprint(\"Đói\")", listOf("Heo\nĐói", "HeoĐói", "Heo Đói"), 0,
            listOf("Heo", "Đói"), "Mỗi print in ra một dòng riêng.", true)
    )),
    Level("bai_02.py", "Biến là cái máng", "Lưu đồ vào tên", "Bài học", listOf(
        Learn("Biến giống cái máng dán nhãn. ten = \"Heo\" nghĩa là bỏ chữ Heo vào máng tên ten. print(ten) là lấy đồ trong máng ra in. Lưu ý ten không có nháy vì nó là tên máng, không phải chữ.",
            "ten = \"Heo\"\nprint(ten)", listOf("Heo")),
        Learn("Máng đổi đồ được: lấy số 3 ra, cộng thêm 2, rồi bỏ 5 vào lại. Số thì không cần dấu nháy.",
            "so_heo = 3\nso_heo = so_heo + 2\nprint(so_heo)", listOf("5")),
        Ex("Chọn dấu để bỏ số 5 vào máng tên heo.", "heo ___ 5\nprint(heo)", listOf("=", "==", ":="), 0,
            listOf("5"), "Một dấu = nghĩa là bỏ vào máng. Hai dấu == là so sánh, bài sau mới học."),
        Ex("In ra thứ trong máng tuoi, không phải chữ \"tuoi\".", "tuoi = 3\nprint(___)", listOf("tuoi", "\"tuoi\"", "Tuoi"), 0,
            listOf("3"), "Tên máng không có nháy. Có nháy thì thành chữ, và Python phân biệt hoa thường."),
        Ex("Đoán xem máy sẽ in ra gì?", "a = 3\na = a + 2\nprint(a)", listOf("3", "5", "a + 2"), 1,
            listOf("5"), "a đang là 3, cộng 2 thành 5 rồi bỏ lại vào a.", true)
    )),
    Level("bat_bug.py", "Bắt bug", "Tìm dòng bị lỗi", "Bắt bug", listOf(
        Learn("Bug là lỗi trong code. Lập trình viên giỏi không phải người không bao giờ sai, mà là người tìm ra lỗi nhanh. Heo sẽ đưa code có đúng 1 dòng lỗi, bạn chạm vào dòng đó. Ví dụ này thiếu dấu nháy đóng nên máy báo lỗi.",
            "print(\"Heo đói)", listOf("SyntaxError: thiếu dấu nháy đóng")),
        Bug("Dòng nào bị lỗi?", listOf("ten = \"Heo\"", "print(ten)", "print(\"Xin chào)"), 2,
            "Dòng 3 mở nháy nhưng quên đóng nháy."),
        Bug("Dòng nào bị lỗi?", listOf("so = 5", "print(So)"), 1,
            "Python phân biệt hoa thường. Máng tên là so, không phải So."),
        Bug("Dòng nào bị lỗi?", listOf("a = 2", "b = 3", "pritn(a)"), 2,
            "Gõ sai chữ print thành pritn nên máy không biết lệnh này.")
    ))
)
