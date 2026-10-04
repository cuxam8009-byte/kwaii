package com.pigprolem.utit

val UNITS: List<CUnit> = listOf(
    CUnit("Làm quen", "Những lệnh đầu tiên", listOf(
        Level("bai_01.py", "In chữ ra màn hình", "Lệnh print", "Bài học", listOf(
            Learn("Code là danh sách lệnh bằng chữ để ra lệnh cho máy tính, giống heo nghe lệnh “ăn đi!”. Lệnh đầu tiên là print, nghĩa là “in ra”: bảo máy hiện chữ lên màn hình.", "print(\"Ụt ịt\")", listOf("Ụt ịt")),
            Learn("Chữ muốn in phải nằm trong dấu nháy “...”. Mỗi lệnh print cho ra một dòng riêng.", "print(\"Heo đói\")\nprint(\"Cho heo ăn đi!\")", listOf("Heo đói", "Cho heo ăn đi!")),
            Ex("Điền lệnh để máy in ra chữ Xin chào.", "___(\"Xin chào\")", listOf("print", "say", "show"), 0, listOf("Xin chào"), "print nghĩa là in ra. say và show không có trong Python.", false),
            Ex("Chọn cách viết đúng để in ra chữ Ụt ịt.", "print(___)", listOf("\"Ụt ịt\"", "Ụt ịt", "(Ụt ịt)"), 0, listOf("Ụt ịt"), "Chữ phải nằm trong dấu nháy kép.", false),
            Ex("Đoán xem máy sẽ in ra gì?", "print(\"Heo\")\nprint(\"Đói\")", listOf("Heo\nĐói", "HeoĐói", "Heo Đói"), 0, listOf("Heo", "Đói"), "Mỗi print in ra một dòng riêng.", true),
            Ex("Đoán xem máy sẽ in ra gì? Chú ý dấu nháy.", "print(\"3 + 2\")", listOf("5", "3 + 2", "32"), 1, listOf("3 + 2"), "Cái gì nằm trong dấu nháy thì máy in y nguyên, không tính toán.", true),
            Order("Xếp các dòng để máy in ra đúng như kết quả mong muốn bên dưới.", listOf("print(\"Heo đói\")", "print(\"Cho heo ăn\")", "print(\"Heo no\")"), listOf("Heo đói", "Cho heo ăn", "Heo no"), "Máy chạy từ trên xuống dưới, dòng nào đứng trước thì in trước.")
        )),
        Level("bai_02.py", "Biến là cái máng", "Lưu đồ vào tên", "Bài học", listOf(
            Learn("Biến giống cái máng dán nhãn. ten = “Heo” nghĩa là bỏ chữ Heo vào máng tên ten. print(ten) là lấy đồ trong máng ra in. Lưu ý ten không có nháy vì nó là tên máng, không phải chữ.", "ten = \"Heo\"\nprint(ten)", listOf("Heo")),
            Learn("Máng đổi đồ được: lấy số 3 ra, cộng thêm 2, rồi bỏ 5 vào lại. Số thì không cần dấu nháy.", "so_heo = 3\nso_heo = so_heo + 2\nprint(so_heo)", listOf("5")),
            Ex("Chọn dấu để bỏ số 5 vào máng tên heo.", "heo ___ 5\nprint(heo)", listOf("=", "==", ":="), 0, listOf("5"), "Một dấu = nghĩa là bỏ vào máng. Hai dấu == là so sánh, sẽ học ở bài sau.", false),
            Ex("In ra thứ trong máng tuoi, không phải chữ “tuoi”.", "tuoi = 3\nprint(___)", listOf("tuoi", "\"tuoi\"", "Tuoi"), 0, listOf("3"), "Tên máng không có nháy. Có nháy thì thành chữ, và Python phân biệt hoa thường.", false),
            Ex("Đoán xem máy sẽ in ra gì?", "a = 3\na = a + 2\nprint(a)", listOf("3", "5", "a + 2"), 1, listOf("5"), "a đang là 3, cộng 2 thành 5 rồi bỏ lại vào a.", true),
            Learn("Tên máng chỉ dùng chữ không dấu, số và gạch dưới. Không có dấu cách, không bắt đầu bằng số. Hãy đặt tên dễ hiểu, như so_bap hay ten_heo.", "ten_heo = \"Mập\"\nheo2 = 2\nprint(ten_heo)\nprint(heo2)", listOf("Mập", "2")),
            Ex("Chọn tên máng đặt đúng.", "___ = 5\nprint(so_bap)", listOf("so bap", "so_bap", "2so_bap"), 1, listOf("5"), "Tên máng không được có dấu cách và không được bắt đầu bằng số. Dùng gạch dưới để nối.", false),
            Order("Xếp các dòng để máy tạo máng ten, in nó ra, rồi in thêm tiếng heo.", listOf("ten = \"Mập\"", "print(ten)", "print(\"Ụt ịt\")"), listOf("Mập", "Ụt ịt"), "Phải bỏ đồ vào máng trước rồi mới lấy ra in được.")
        )),
        Level("bat_bug_1.py", "Bắt bug", "Tìm dòng bị lỗi", "Bắt bug", listOf(
            Learn("Bug là lỗi trong code. Lập trình viên giỏi không phải người không bao giờ sai, mà là người tìm ra lỗi nhanh. Heo sẽ đưa code có đúng 1 dòng lỗi, bạn chạm vào dòng đó. Ví dụ này thiếu dấu nháy đóng nên máy báo lỗi.", "print(\"Heo đói)", listOf("SyntaxError: thiếu dấu nháy đóng")),
            Bug("Dòng nào bị lỗi?", listOf("ten = \"Heo\"", "print(ten)", "print(\"Xin chào)"), 2, "Dòng 3 mở nháy nhưng quên đóng nháy."),
            Bug("Dòng nào bị lỗi?", listOf("so = 5", "print(So)"), 1, "Python phân biệt hoa thường. Máng tên là so, không phải So."),
            Bug("Dòng nào bị lỗi?", listOf("a = 2", "b = 3", "pritn(a)"), 2, "Gõ sai chữ print thành pritn nên máy không biết lệnh này."),
            Bug("Dòng nào bị lỗi?", listOf("ten = Heo", "print(ten)"), 0, "Heo không có nháy nên Python tưởng đó là tên một máng chưa tồn tại. Chữ phải nằm trong nháy."),
            Bug("Dòng nào bị lỗi?", listOf("heo = 3", "print(heo", "print(\"xong\")"), 1, "Dòng 2 mở ngoặc ( nhưng quên đóng ngoặc ).")
        )),
    )),
    CUnit("Số và chữ", "Tính toán và ghép chữ", listOf(
        Level("bai_04.py", "Heo biết tính toán", "Các phép tính", "Bài học", listOf(
            Learn("Python tính toán như máy tính bỏ túi: + cộng, - trừ, * nhân, / chia. Để ý phép chia luôn cho ra số có dấu phẩy.", "print(3 + 2)\nprint(10 - 4)\nprint(6 * 7)\nprint(8 / 2)", listOf("5", "6", "42", "4.0")),
            Learn("Nhân chia làm trước, cộng trừ làm sau, giống môn toán. Muốn đổi thứ tự thì dùng ngoặc.", "print(2 + 3 * 4)\nprint((2 + 3) * 4)", listOf("14", "20")),
            Learn("// chia lấy phần nguyên, % chia lấy phần dư. Chia 7 bắp cho 2 heo: mỗi heo được 3 bắp, còn dư 1 bắp.", "print(7 // 2)\nprint(7 % 2)", listOf("3", "1")),
            Ex("Điền dấu để heo nhân đôi số bắp.", "bap = 4\nprint(bap ___ 2)", listOf("*", "+", "x"), 0, listOf("8"), "Dấu nhân trong Python là *. Chữ x không phải dấu nhân.", false),
            Ex("Đoán xem máy sẽ in ra gì?", "print(10 - 2 * 3)", listOf("24", "4", "16"), 1, listOf("4"), "Nhân làm trước: 2 * 3 = 6, rồi 10 - 6 = 4.", true),
            Ex("Đoán xem máy sẽ in ra gì?", "print(9 % 4)", listOf("2", "1", "5"), 1, listOf("1"), "9 chia 4 được 2, dư 1. Dấu % cho ra phần dư.", true),
            Ex("Chia 9 bắp cho 2 heo. Mỗi heo được mấy bắp nguyên? Điền phép tính.", "print(9 ___ 2)", listOf("//", "/", "%"), 0, listOf("4"), "// chia lấy phần nguyên. / cho ra 4.5 còn % cho ra phần dư.", false),
            Order("Xếp các dòng để máy tạo máng bap bằng 6, nhân đôi, rồi in ra.", listOf("bap = 6", "bap = bap * 2", "print(bap)"), listOf("12"), "Phải có bap trước, nhân đôi, rồi mới in ra kết quả.")
        )),
        Level("bai_05.py", "Ghép chữ", "Chữ cũng tính toán được", "Bài học", listOf(
            Learn("Chữ ghép lại được bằng dấu +. Muốn có khoảng trống thì thêm dấu cách trong nháy.", "ho = \"Heo\"\nten = \"Mập\"\nprint(ho + ten)\nprint(ho + \" \" + ten)", listOf("HeoMập", "Heo Mập")),
            Learn("Nhân chữ với một số để lặp lại chữ đó. Hàm len() đếm xem chữ có bao nhiêu ký tự.", "print(\"Ụ\" * 4)\nprint(len(\"Heo\"))", listOf("ỤỤỤỤ", "3")),
            Learn("Gắn chữ f trước dấu nháy, rồi đặt tên máng trong ngoặc nhọn { } để chèn vào giữa câu.", "ten = \"Mập\"\ntuoi = 2\nprint(f\"{ten} được {tuoi} tuổi\")", listOf("Mập được 2 tuổi")),
            Ex("Điền dấu để nối hai chữ thành Heo Mập.", "print(\"Heo\" ___ \" Mập\")", listOf("+", "-", "*"), 0, listOf("Heo Mập"), "Dấu + nối chữ với chữ. Dấu - không dùng được với chữ.", false),
            Ex("Đoán xem máy sẽ in ra gì?", "print(\"Ụ\" * 3)", listOf("ỤỤỤ", "Ụ3", "Ụ Ụ Ụ"), 0, listOf("ỤỤỤ"), "Chữ nhân với 3 là lặp chữ đó 3 lần, không có khoảng trống.", true),
            Ex("Đoán xem máy sẽ in ra gì?", "ten = \"Mập\"\nprint(len(ten))", listOf("3", "Mập", "ten"), 0, listOf("3"), "Chữ Mập có 3 ký tự. len đếm ký tự chứ không in chữ.", true),
            Ex("Điền để máy chèn tên máng vào câu chào.", "ten = \"Mập\"\nprint(___)", listOf("f\"Chào {ten}\"", "\"Chào {ten}\"", "f\"Chào ten\""), 0, listOf("Chào Mập"), "Phải có chữ f ở trước nháy, và tên máng nằm trong { }.", false),
            Order("Xếp các dòng để máy tạo chữ Mập, thêm dấu ! vào cuối rồi in ra.", listOf("ten = \"Mập\"", "ten = ten + \"!\"", "print(ten)"), listOf("Mập!"), "Tạo máng trước, ghép thêm dấu !, rồi mới in.")
        )),
    )),
    CUnit("Ra quyết định", "Heo chọn đường đi", listOf(
        Level("bai_06.py", "Đúng hay sai", "So sánh và True, False", "Bài học", listOf(
            Learn("Máy so sánh được hai thứ và trả lời True (đúng) hoặc False (sai). Dấu so sánh: == bằng, != khác, > lớn hơn, < nhỏ hơn.", "print(3 > 2)\nprint(3 == 5)\nprint(3 != 5)", listOf("True", "False", "True")),
            Learn("Nhớ kỹ: một dấu = là bỏ vào máng, hai dấu == là hỏi “có bằng nhau không?”. Chữ cũng so sánh được, nhưng hoa thường là khác nhau.", "bap = 5\nprint(bap == 5)\nprint(\"heo\" == \"Heo\")", listOf("True", "False")),
            Learn(">= là lớn hơn hoặc bằng, <= là nhỏ hơn hoặc bằng.", "print(5 >= 5)\nprint(4 <= 3)", listOf("True", "False")),
            Ex("Đoán xem máy sẽ in ra gì?", "print(10 > 7)", listOf("True", "False", "10"), 0, listOf("True"), "10 lớn hơn 7 nên câu trả lời là True.", true),
            Ex("Điền dấu để câu trả lời là True.", "print(4 ___ 4)", listOf("==", "!=", ">"), 0, listOf("True"), "4 bằng 4 nên dùng ==. != và > đều cho ra False.", false),
            Ex("Đoán xem máy sẽ in ra gì?", "heo = 3\nprint(heo == 4)", listOf("True", "False", "3"), 1, listOf("False"), "heo là 3, không bằng 4 nên là False.", true),
            Ex("Điền dấu để máy nói rằng 2 khác 3.", "print(2 ___ 3)", listOf("!=", "==", "="), 0, listOf("True"), "!= nghĩa là khác. == cho ra False, còn một dấu = không dùng để so sánh.", false),
            Bug("Dòng nào bị lỗi?", listOf("bap = 5", "print(bap = 5)"), 1, "Muốn so sánh phải dùng hai dấu ==. Một dấu = là bỏ vào máng nên không đặt trong print được.")
        )),
        Level("bai_07.py", "Heo chọn đường", "if và else", "Bài học", listOf(
            Learn("if nghĩa là “nếu”. Nếu điều kiện đúng thì máy chạy phần thụt vào (lùi vào 4 dấu cách) bên dưới. Nhớ dấu hai chấm : ở cuối dòng if.", "bap = 5\nif bap > 3:\n    print(\"Đủ ăn rồi\")", listOf("Đủ ăn rồi")),
            Learn("Nếu điều kiện sai thì máy bỏ qua phần thụt vào. else nghĩa là “nếu không”, chạy khi if sai.", "bap = 1\nif bap > 3:\n    print(\"Đủ ăn\")\nelse:\n    print(\"Đói quá\")", listOf("Đói quá")),
            Ex("Điền dấu để if đúng cú pháp.", "bap = 5\nif bap > 3___\n    print(\"Ăn đi\")", listOf(":", ";", "."), 0, listOf("Ăn đi"), "Cuối dòng if phải có dấu hai chấm :.", false),
            Ex("Đoán xem máy sẽ in ra gì?", "tuoi = 2\nif tuoi >= 3:\n    print(\"Heo lớn\")\nelse:\n    print(\"Heo nhỏ\")", listOf("Heo lớn", "Heo nhỏ", "Cả hai"), 1, listOf("Heo nhỏ"), "tuoi là 2, không lớn hơn hoặc bằng 3 nên máy chạy phần else.", true),
            Ex("Điền dấu để heo nhỏ hơn 3 tuổi thì in Heo con.", "tuoi = 2\nif tuoi ___ 3:\n    print(\"Heo con\")", listOf("<", ">", "=="), 0, listOf("Heo con"), "2 nhỏ hơn 3 nên dùng dấu <.", false),
            Ex("Đoán xem máy sẽ in ra gì? Chú ý dòng cuối không thụt vào.", "bap = 5\nif bap > 9:\n    print(\"A\")\nprint(\"B\")", listOf("A", "B", "A\nB"), 1, listOf("B"), "Dòng print(\"B\") không thụt vào nên không thuộc if, máy luôn chạy nó.", true),
            Order("Xếp các dòng thành chương trình hoàn chỉnh. Chú ý dòng thụt vào.", listOf("bap = 2", "if bap > 3:", "    print(\"Nhiều\")", "else:", "    print(\"Ít\")"), listOf("Ít"), "if đứng trước, phần thụt vào đứng ngay dưới nó, else đứng sau phần của if."),
            Bug("Dòng nào bị lỗi?", listOf("bap = 5", "if bap > 3", "    print(\"Đủ\")"), 1, "Dòng if thiếu dấu hai chấm : ở cuối.")
        )),
        Level("bai_08.py", "Nhiều lựa chọn", "elif, and, or, not", "Bài học", listOf(
            Learn("elif nghĩa là “nếu không thì nếu”. Dùng khi có nhiều lựa chọn. Máy kiểm tra từ trên xuống và dừng ở nhánh đúng đầu tiên.", "bap = 5\nif bap > 8:\n    print(\"Rất no\")\nelif bap > 3:\n    print(\"Vừa đủ\")\nelse:\n    print(\"Đói\")", listOf("Vừa đủ")),
            Learn("and nghĩa là “và”: cả hai điều kiện đều phải đúng. or nghĩa là “hoặc”: chỉ cần một điều kiện đúng.", "tuoi = 2\nbap = 5\nprint(tuoi < 3 and bap > 3)\nprint(tuoi > 5 or bap > 3)", listOf("True", "True")),
            Learn("not đảo ngược kết quả: True thành False và ngược lại.", "print(not True)\nprint(not 3 > 5)", listOf("False", "True")),
            Ex("Đoán xem máy sẽ in ra gì?", "bap = 2\nif bap > 8:\n    print(\"A\")\nelif bap > 3:\n    print(\"B\")\nelse:\n    print(\"C\")", listOf("A", "B", "C"), 2, listOf("C"), "bap = 2 không lớn hơn 8, cũng không lớn hơn 3, nên máy chạy else.", true),
            Ex("Đoán xem máy sẽ in ra gì?", "print(True and False)", listOf("True", "False", "and"), 1, listOf("False"), "and cần cả hai đều đúng. Có một False nên kết quả là False.", true),
            Ex("Heo vào chuồng khi trời tối HOẶC trời mưa. Điền từ còn thiếu.", "troi_toi = False\nmua = True\nprint(troi_toi ___ mua)", listOf("or", "and", "not"), 0, listOf("True"), "Chỉ cần một trong hai đúng là được, nên dùng or.", false),
            Ex("Đoán xem máy sẽ in ra gì?", "tuoi = 4\nif tuoi < 3:\n    print(\"Con\")\nelif tuoi < 6:\n    print(\"Nhỡ\")\nelse:\n    print(\"Lớn\")", listOf("Con", "Nhỡ", "Lớn"), 1, listOf("Nhỡ"), "4 không nhỏ hơn 3 nhưng nhỏ hơn 6, nên máy dừng ở nhánh elif.", true),
            Order("Xếp các dòng thành chương trình có elif hoàn chỉnh.", listOf("bap = 4", "if bap > 8:", "    print(\"Nhiều\")", "elif bap > 2:", "    print(\"Vừa\")"), listOf("Vừa"), "Máy kiểm tra if trước, rồi mới tới elif. Mỗi nhánh có phần thụt vào của riêng nó."),
            Bug("Dòng nào bị lỗi?", listOf("bap = 5", "if bap > 8:", "    print(\"Nhiều\")", "else if bap > 3:", "    print(\"Vừa\")"), 3, "Python không có else if. Phải viết liền thành elif.")
        )),
        Level("bat_bug_2.py", "Bắt bug 2", "Lỗi hay gặp với if", "Bắt bug", listOf(
            Learn("Ba lỗi hay gặp với if: quên dấu hai chấm, thụt lề sai, và dùng một dấu = thay vì hai dấu ==. Ví dụ này dùng sai dấu so sánh.", "if 5 = 5:\n    print(\"Bằng\")", listOf("SyntaxError: dùng = thay vì ==")),
            Bug("Dòng nào bị lỗi?", listOf("tuoi = 3", "if tuoi == 3", "    print(\"Ba tuổi\")"), 1, "Thiếu dấu hai chấm : ở cuối dòng if."),
            Bug("Dòng nào bị lỗi?", listOf("bap = 6", "if bap > 3:", "print(\"Nhiều\")"), 2, "Dòng nằm dưới if phải thụt vào 4 dấu cách."),
            Bug("Dòng nào bị lỗi?", listOf("tuoi = 2", "if tuoi = 2:", "    print(\"Hai\")"), 1, "Trong if phải so sánh bằng hai dấu ==, không phải một dấu =."),
            Bug("Dòng nào bị lỗi?", listOf("bap = 1", "if bap > 3:", "    print(\"No\")", "    else:", "        print(\"Đói\")"), 3, "else phải thẳng hàng với if, không được thụt vào."),
            Bug("Dòng nào bị lỗi?", listOf("bap = 4", "if bap > 3:", "    print(Bap)"), 2, "Máng tên là bap, viết hoa thành Bap là máng khác chưa tồn tại.")
        )),
    )),
    CUnit("Vòng lặp", "Làm đi làm lại", listOf(
        Level("bai_10.py", "Lặp đi lặp lại", "for và range", "Bài học", listOf(
            Learn("Muốn làm một việc nhiều lần thì đừng gõ lại. Dùng for. range(3) nghĩa là đếm 0, 1, 2, tức là lặp 3 lần.", "for i in range(3):\n    print(\"Ụt ịt\")", listOf("Ụt ịt", "Ụt ịt", "Ụt ịt")),
            Learn("Biến i nhận lần lượt từng số trong range. Máy đếm từ 0 chứ không phải từ 1.", "for i in range(3):\n    print(i)", listOf("0", "1", "2")),
            Learn("range(1, 4) đếm từ 1 đến 3, không tính 4. Dùng một máng để cộng dồn số bắp.", "tong = 0\nfor i in range(1, 4):\n    tong = tong + i\nprint(tong)", listOf("6")),
            Ex("Đoán xem máy sẽ in ra gì?", "for i in range(2):\n    print(\"Heo\")", listOf("Heo", "Heo\nHeo", "Heo\nHeo\nHeo"), 1, listOf("Heo", "Heo"), "range(2) lặp 2 lần nên in 2 dòng.", true),
            Ex("Điền số để máy in ra 0 1 2 3.", "for i in range(___):\n    print(i)", listOf("3", "4", "5"), 1, listOf("0", "1", "2", "3"), "range(4) đếm 0, 1, 2, 3. Số trong ngoặc là số lần lặp.", false),
            Ex("Điền số để máy đếm từ 1 đến 3.", "for i in range(___, 4):\n    print(i)", listOf("0", "1", "3"), 1, listOf("1", "2", "3"), "range(1, 4) bắt đầu từ 1 và dừng trước 4.", false),
            Ex("Đoán xem máy sẽ in ra gì?", "tong = 0\nfor i in range(1, 5):\n    tong = tong + i\nprint(tong)", listOf("10", "15", "4"), 0, listOf("10"), "Cộng 1 + 2 + 3 + 4 = 10.", true),
            Order("Xếp các dòng để máy đếm 0 1 2 rồi báo Xong.", listOf("for i in range(3):", "    print(i)", "print(\"Xong\")"), listOf("0", "1", "2", "Xong"), "Dòng thụt vào nằm trong vòng lặp. Dòng Xong thẳng hàng với for nên chỉ chạy một lần sau khi lặp xong.")
        )),
        Level("bai_11.py", "Lặp đến khi nào", "Vòng lặp while", "Bài học", listOf(
            Learn("while nghĩa là “trong khi”. Máy lặp mãi cho tới khi điều kiện sai. Nhớ thay đổi biến bên trong, nếu không máy sẽ lặp vô tận!", "bap = 3\nwhile bap > 0:\n    print(bap)\n    bap = bap - 1\nprint(\"Hết bắp\")", listOf("3", "2", "1", "Hết bắp")),
            Learn("for dùng khi biết trước số lần lặp. while dùng khi chưa biết sẽ lặp bao nhiêu lần.", "no = 0\nwhile no < 10:\n    no = no + 4\nprint(no)", listOf("12")),
            Ex("Đoán xem máy sẽ in ra gì?", "n = 1\nwhile n < 4:\n    print(n)\n    n = n + 1", listOf("1\n2\n3", "1\n2\n3\n4", "1"), 0, listOf("1", "2", "3"), "n chạy 1, 2, 3. Khi n bằng 4 thì điều kiện n < 4 sai nên dừng.", true),
            Ex("Điền dấu để vòng lặp dừng lại, không chạy mãi.", "n = 3\nwhile n > 0:\n    print(n)\n    n = n ___ 1", listOf("-", "+", "*"), 0, listOf("3", "2", "1"), "Phải giảm n xuống để điều kiện n > 0 có lúc sai. Cộng hoặc nhân sẽ khiến vòng lặp chạy mãi.", false),
            Ex("Đoán xem máy sẽ in ra gì?", "bap = 5\nwhile bap > 2:\n    bap = bap - 2\nprint(bap)", listOf("1", "3", "2"), 0, listOf("1"), "5 trừ 2 được 3, vẫn lớn hơn 2 nên trừ tiếp thành 1. Lúc đó dừng và in 1.", true),
            Order("Xếp các dòng để máy đếm ngược 3 2 1.", listOf("n = 3", "while n > 0:", "    print(n)", "    n = n - 1"), listOf("3", "2", "1"), "In trước rồi mới giảm n, nên máy in được 3 trước tiên."),
            Bug("Dòng nào bị lỗi?", listOf("n = 3", "while n > 0", "    print(n)", "    n = n - 1"), 1, "Dòng while thiếu dấu hai chấm : ở cuối.")
        )),
        Level("bat_bug_3.py", "Bắt bug 3", "Lỗi trong vòng lặp", "Bắt bug", listOf(
            Learn("Với vòng lặp, lỗi hay gặp là quên dấu hai chấm, thụt lề sai, hoặc viết sai range. Ví dụ này thiếu dấu hai chấm sau for.", "for i in range(3)\n    print(i)", listOf("SyntaxError: thiếu dấu hai chấm")),
            Bug("Dòng nào bị lỗi?", listOf("for i in range 3:", "    print(i)"), 0, "range phải có ngoặc tròn: range(3)."),
            Bug("Dòng nào bị lỗi?", listOf("for i in range(3):", "print(i)"), 1, "Dòng nằm trong vòng lặp phải thụt vào 4 dấu cách."),
            Bug("Dòng nào bị lỗi?", listOf("tong = 0", "for i in range(1, 4):", "    tong = tong + i", "print(Tong)"), 3, "Máng tên là tong, không phải Tong. Python phân biệt hoa thường."),
            Bug("Dòng nào bị lỗi?", listOf("n = 3", "while n > 0:", "    print(n)", "     n = n - 1"), 3, "Hai dòng trong vòng lặp phải thụt vào bằng nhau. Dòng này thụt thừa một dấu cách."),
            Bug("Dòng nào bị lỗi?", listOf("bap = 3", "while bap > 0:", "    print(bap)", "    bap = bap -"), 3, "Phép trừ chưa có số phía sau nên chưa hoàn chỉnh.")
        )),
    )),
    CUnit("Danh sách và hàm", "Công cụ của lập trình viên", listOf(
        Level("bai_13.py", "Danh sách", "list", "Bài học", listOf(
            Learn("Danh sách (list) là một dãy ngăn xếp hàng, đặt trong dấu [ ]. Mỗi ngăn có số thứ tự, và số thứ tự bắt đầu từ 0.", "heo = [\"Mập\", \"Ú\", \"Bông\"]\nprint(heo[0])\nprint(heo[2])", listOf("Mập", "Bông")),
            Learn("len() cho biết danh sách có bao nhiêu phần tử. append() thêm một phần tử vào cuối danh sách.", "heo = [\"Mập\", \"Ú\"]\nheo.append(\"Bông\")\nprint(len(heo))\nprint(heo)", listOf("3", "['Mập', 'Ú', 'Bông']")),
            Learn("for chạy được qua từng phần tử của danh sách, không cần dùng range.", "heo = [\"Mập\", \"Ú\", \"Bông\"]\nfor ten in heo:\n    print(\"Chào \" + ten)", listOf("Chào Mập", "Chào Ú", "Chào Bông")),
            Ex("Đoán xem máy sẽ in ra gì?", "bap = [5, 8, 2]\nprint(bap[1])", listOf("5", "8", "2"), 1, listOf("8"), "Số thứ tự bắt đầu từ 0, nên bap[1] là phần tử thứ hai, tức là 8.", true),
            Ex("In ra phần tử cuối (thứ ba) của danh sách.", "bap = [5, 8, 2]\nprint(bap[___])", listOf("2", "3", "1"), 0, listOf("2"), "Ba phần tử có số thứ tự 0, 1, 2. Phần tử cuối là bap[2].", false),
            Ex("Đoán xem máy sẽ in ra gì?", "bap = [1, 2]\nbap.append(3)\nprint(len(bap))", listOf("2", "3", "4"), 1, listOf("3"), "Thêm một phần tử thì danh sách từ 2 thành 3 phần tử.", true),
            Ex("Điền tên lệnh để thêm Ú vào danh sách.", "heo = [\"Mập\"]\nheo.___(\"Ú\")\nprint(heo)", listOf("append", "add", "push"), 0, listOf("['Mập', 'Ú']"), "Lệnh thêm vào cuối danh sách của Python là append.", false),
            Order("Xếp các dòng để tạo danh sách rỗng, thêm Mập rồi Ú, rồi in ra.", listOf("heo = []", "heo.append(\"Mập\")", "heo.append(\"Ú\")", "print(heo)"), listOf("['Mập', 'Ú']"), "Phải tạo danh sách trước, thêm theo đúng thứ tự, rồi mới in."),
            Bug("Dòng nào bị lỗi?", listOf("heo = [\"Mập\", \"Ú\"]", "print(heo[2])"), 1, "Danh sách chỉ có hai phần tử, số thứ tự 0 và 1. Không có heo[2].")
        )),
        Level("bai_14.py", "Hàm", "def và return", "Bài học", listOf(
            Learn("Hàm là một nhóm lệnh được đặt tên để dùng lại nhiều lần. def nghĩa là “định nghĩa”. Định nghĩa xong thì gọi tên hàm để chạy.", "def chao():\n    print(\"Ụt ịt, xin chào!\")\n\nchao()\nchao()", listOf("Ụt ịt, xin chào!", "Ụt ịt, xin chào!")),
            Learn("Hàm nhận đồ vào trong ngoặc (gọi là tham số) và trả kết quả ra bằng return.", "def gap_doi(so):\n    return so * 2\n\nprint(gap_doi(4))\nprint(gap_doi(10))", listOf("8", "20")),
            Learn("print chỉ in ra màn hình. return đưa giá trị về cho nơi gọi hàm để dùng tiếp.", "def cong(a, b):\n    return a + b\n\nkq = cong(2, 3)\nprint(kq + 1)", listOf("6")),
            Ex("Điền từ khóa để tạo hàm.", "___ chao():\n    print(\"Hi\")\n\nchao()", listOf("def", "func", "fn"), 0, listOf("Hi"), "Từ khóa tạo hàm của Python là def.", false),
            Ex("Đoán xem máy sẽ in ra gì?", "def gap_ba(n):\n    return n * 3\n\nprint(gap_ba(2))", listOf("5", "6", "23"), 1, listOf("6"), "gap_ba(2) trả về 2 * 3 = 6.", true),
            Ex("Hàm cần trả kết quả về. Điền từ còn thiếu.", "def binh_phuong(n):\n    ___ n * n\n\nprint(binh_phuong(5))", listOf("return", "print", "give"), 0, listOf("25"), "return trả giá trị về cho nơi gọi hàm.", false),
            Ex("Đoán xem máy sẽ in ra gì?", "def hello(ten):\n    return \"Chào \" + ten\n\nprint(hello(\"Mập\"))", listOf("Chào Mập", "Chào ten", "hello"), 0, listOf("Chào Mập"), "Máy thay ten bằng Mập rồi ghép với chữ Chào.", true),
            Order("Xếp các dòng để định nghĩa hàm gấp đôi rồi gọi nó với số 5.", listOf("def gap_doi(so):", "    return so * 2", "print(gap_doi(5))"), listOf("10"), "Phải định nghĩa hàm trước rồi mới gọi được nó."),
            Bug("Dòng nào bị lỗi?", listOf("def chao():", "print(\"Hi\")", "chao()"), 1, "Dòng nằm trong hàm phải thụt vào 4 dấu cách.")
        )),
        Level("thu_thach.py", "Thử thách cuối", "Ôn lại mọi thứ đã học", "Thử thách", listOf(
            Learn("Chặng cuối! Heo sẽ hỏi lại mọi thứ bạn đã học: máng, phép tính, if, vòng lặp, danh sách và hàm. Đây là chương trình dùng đủ các thứ đó, bạn đọc hiểu được rồi đấy.", "def dem_bap(ds):\n    tong = 0\n    for so in ds:\n        tong = tong + so\n    return tong\n\nbap = [3, 4, 5]\nprint(dem_bap(bap))", listOf("12")),
            Ex("Đoán xem máy sẽ in ra gì?", "ten = \"Mập\"\nprint(f\"Heo {ten}\")", listOf("Heo Mập", "Heo ten", "Heo {ten}"), 0, listOf("Heo Mập"), "f-string chèn giá trị của máng ten vào câu.", true),
            Ex("Đoán xem máy sẽ in ra gì?", "print(7 // 2 + 7 % 2)", listOf("4", "3", "5"), 0, listOf("4"), "7 // 2 là 3, 7 % 2 là 1, cộng lại được 4.", true),
            Ex("Đoán xem máy sẽ in ra gì?", "bap = 7\nif bap > 5 and bap < 9:\n    print(\"Vừa\")\nelse:\n    print(\"Lệch\")", listOf("Vừa", "Lệch", "Không in gì"), 0, listOf("Vừa"), "7 lớn hơn 5 và nhỏ hơn 9, cả hai điều kiện đúng.", true),
            Ex("Đoán xem máy sẽ in ra gì?", "for i in range(1, 4):\n    print(i * 2)", listOf("2\n4\n6", "1\n2\n3", "2\n4\n6\n8"), 0, listOf("2", "4", "6"), "i chạy 1, 2, 3 và mỗi lần in gấp đôi: 2, 4, 6.", true),
            Ex("In ra Bông, phần tử cuối của danh sách.", "heo = [\"Mập\", \"Ú\", \"Bông\"]\nprint(heo[___])", listOf("2", "3", "1"), 0, listOf("Bông"), "Ba phần tử có số thứ tự 0, 1, 2. Bông nằm ở số 2.", false),
            Ex("Hàm cần trả kết quả về. Điền từ còn thiếu.", "def tru_mot(n):\n    ___ n - 1\n\nprint(tru_mot(10))", listOf("return", "print", "def"), 0, listOf("9"), "return đưa kết quả về cho nơi gọi hàm.", false),
            Bug("Dòng nào bị lỗi?", listOf("so = 3", "while so > 0:", "    print(so)", "    so = so - 1", "print(Xong)"), 4, "Xong không có nháy nên máy tưởng là tên một máng chưa tồn tại."),
            Bug("Dòng nào bị lỗi?", listOf("heo = [\"Mập\", \"Ú\"]", "for ten in heo", "    print(ten)"), 1, "Dòng for thiếu dấu hai chấm : ở cuối."),
            Order("Xếp các dòng để định nghĩa hàm cộng thêm 1, gọi nó với số 4 rồi in kết quả.", listOf("def dem(n):", "    return n + 1", "x = dem(4)", "print(x)"), listOf("5"), "Định nghĩa hàm trước, gọi hàm để lấy kết quả, rồi in kết quả ra.")
        )),
    )),
)
