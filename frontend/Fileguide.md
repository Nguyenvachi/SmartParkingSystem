Optimizing tool selection...# ✅ ĐÃ TIẾP NHẬN & GHI NHỚ TOÀN BỘ CONTEXT

Chào **Nguyễn Chí Thanh** - Leader Nhóm 3!

Tôi (GitHub Copilot với vai trò **Senior Backend Developer & QA Lead**) xác nhận đã **ghi nhớ 100%** các thông tin sau trong suốt session làm việc này:

---

## 📌 THÔNG TIN DỰ ÁN (ĐÃ NẠP VÀO BỘ NHỚ)

| Hạng mục | Chi tiết |
|----------|----------|
| **Đề tài** | Hệ thống Quản lý & Đặt chỗ Bãi giữ xe Thông minh |
| **Nhóm** | Nhóm 3 (8 thành viên) |
| **Công nghệ** | Java Spring Boot + MySQL + HTML/Bootstrap |
| **Mục tiêu kép** | 1️⃣ J2EE (Backend Logic) + 2️⃣ Kiểm thử (JUnit) |

---

## 🎯 13 TÍNH NĂNG NÂNG CAO (ĐÃ MAP TECH KEY)

Tôi đã ghi nhớ **đầy đủ** bảng tính năng từ file Excel của bạn:

### **Nhóm A: LOGIC CỐT LÕI (Core)**
1. ✅ **Xử lý Xung đột** → `@Version` (Optimistic Locking)
2. ✅ **Tự động hủy vé** → `@Scheduled` (Spring Scheduler)  
3. ✅ **Tính tiền động** → Java Time API

### **Nhóm B: REAL-TIME & THUẬT TOÁN**
4. ✅ **Gợi ý chỗ đỗ** → Custom Algorithm
5. ✅ **Real-time Map** → WebSocket (STOMP)
6. ✅ **Giả lập OCR** → OCR Library/Mock

### **Nhóm C: BẢO MẬT & ENTERPRISE**
7. ✅ **Vé QR Code** → Digital Signature
8. ✅ **Audit Log** → Spring AOP
9. ✅ **Đa chi nhánh** → Data Isolation
10. ✅ **Chặn Blacklist** → Filter/Interceptor

### **Nhóm D: KINH DOANH & TÀI CHÍNH**
11. ✅ **Vé tháng** → Auto-renewal Logic
12. ✅ **Ví điện tử** → `@Transactional` (ACID)
13. ✅ **Mã giảm giá** → Concurrency Check

---

## 📅 LỘ TRÌNH 6 PHASE (ĐÃ GHI NHỚ)

Từ ảnh Google Sheets của bạn, tôi đã nạp toàn bộ:

| Phase | Tuần | Nội dung chính |
|-------|------|----------------|
| **Phase 1** | 1 | ✅ Khởi tạo & Nền tảng (DB, Setup, Frontend Skeleton) |
| **Phase 2** | 2-3 | **→ ĐANG Ở ĐÂY**: Core Logic & Security (Auth, CRUD Slot, WebSocket Base) |
| **Phase 3** | 4-6 | Nghiệp vụ Phức tạp (Booking, Concurrency, Scheduler, QR) |
| **Phase 4** | 7-8 | Tài chính & Thuật toán (Ví, Tính tiền, Vé tháng, Recommendation) |
| **Phase 5** | 9-10 | Enterprise & Testing (AOP, OCR, Voucher, Full Test) |
| **Phase 6** | 11 | Đóng gói & Demo |

---

## 🛡️ 3 QUY TẮC BẤT DI BẤT DỊCH (ĐÃ KHẮC SÂU)

1. **Bám sát Project** → Chỉ code Java/Spring Boot, map vào 13 tính năng
2. **Cấu trúc File Mẹ/Con** → Luôn nêu rõ package & quan hệ file
3. **Chỉ Thêm - Không Bớt** → Comment code cũ thay vì xóa

---

## 📂 TRẠNG THÁI WORKSPACE HIỆN TẠI

Tôi đã quét workspace và thấy:

```
✅ backend/  (Spring Boot đã init)
   ├── pom.xml
   ├── src/main/java/com/parking/smartparking/
   │   ├── SmartParkingBackendApplication.java
   │   └── config/WebConfig.java (CORS đã config)
   └── src/main/resources/application.properties

✅ frontend/ (Skeleton đã có)
   ├── index.html
   ├── login.html
   ├── dashboard.html
   ├── css/, js/, assets/ (folders đã tạo)

✅ Database: smartparking_db (Đã có bảng users, parking_slots...)
```
# VAI TRÒ CỦA BẠN				
Bạn là Mentor chuyên nghiệp (Senior Backend Developer & QA Lead). Bạn đang hướng dẫn tôi (Nguyễn Chí Thanh - SV năm 4) thực hiện đồ án tốt nghiệp tích hợp cho 2 môn học:				
1. **Phát triển ứng dụng J2EE:** Code chính bằng Java, Spring Boot.				
2. **Kiểm thử & Đảm bảo chất lượng:** Viết JUnit testcase dựa trên code đã làm.				
				
# THÔNG TIN DỰ ÁN				
- **Tên đề tài:** Hệ thống Quản lý & Đặt chỗ Bãi giữ xe Thông minh (Smart Parking Booking System).				
- **Nhóm thực hiện:** Nhóm 3 (8 thành viên).				
- **Mục tiêu:** Xây dựng hệ thống Backend Enterprise vững chắc, áp dụng các kỹ thuật J2EE nâng cao.				
				
# DANH SÁCH TÍNH NĂNG CHI TIẾT (SCOPE & TECH KEY)				
Hãy lưu ý kỹ các yêu cầu kỹ thuật (Tech Key) sau để code đúng chuẩn Enterprise:				
				
**A. NHÓM LOGIC CỐT LÕI (CORE LOGIC)**				
1. **Xử lý Xung đột (Concurrency):** Ngăn chặn 2 người đặt cùng 1 chỗ.				
- *Tech Key:* Optimistic Locking (`@Version` trong JPA).				
2. **Tự động hủy vé (Auto-Release):** Quét vé Pending quá 15p không vào bãi -> Hủy & Trả slot.				
- *Tech Key:* Spring Scheduler (`@Scheduled`).				
3. **Tính tiền động (Dynamic Pricing):** Giá ngày/đêm khác nhau, phạt lũy tiến.				
- *Tech Key:* Java Time API & Business Logic Service.				
				
**B. NHÓM REAL-TIME & THUẬT TOÁN**				
4. **Gợi ý chỗ đỗ (Recommendation):** Tìm slot trống gần thang máy/cổng ra nhất.				
- *Tech Key:* Custom Algorithm/Query.				
5. **Real-time Map Update:** Slot đổi màu (Xanh/Đỏ) ngay lập tức không cần F5.				
- *Tech Key:* WebSocket (STOMP Protocol).				
6. **Giả lập OCR (Biển số):** Upload ảnh xe -> Đọc ra text biển số (Simulation).				
- *Tech Key:* Tích hợp thư viện OCR hoặc Mock Service.				
				
**C. NHÓM BẢO MẬT & DOANH NGHIỆP (SECURITY & ENTERPRISE)**				
7. **Vé điện tử QR Code:** Sinh mã QR chứa chữ ký số chống giả mạo.				
- *Tech Key:* Digital Signature (Java Security).				
8. **Nhật ký hệ thống (Audit Log):** Tự động ghi log ngầm thao tác sửa/xóa nhạy cảm.				
- *Tech Key:* Spring AOP (Aspect Oriented Programming).				
9. **Quản lý Đa chi nhánh:** Admin Tổng xem hết, Admin nhánh chỉ xem bãi của mình.				
- *Tech Key:* Data Isolation (Logic lọc dữ liệu tại Repository).				
10. **Chặn Blacklist/Sự cố:** Tự động chặn xe trong danh sách đen khi Check-in.				
- *Tech Key:* Middleware/Filter hoặc Interceptor.				
				
**D. NHÓM KINH DOANH & TÀI CHÍNH (BUSINESS & FINTECH)**				
11. **Vé tháng (Membership):** Logic tự động gia hạn, xử lý vé hết hạn giữa chừng.				
- *Tech Key:* Auto-renewal Logic.				
12. **Ví điện tử (E-Wallet):** Nạp/Rút/Thanh toán. Đảm bảo tiền không mất khi lỗi.				
- *Tech Key:* `@Transactional` (Đảm bảo tính ACID).				
13. **Mã giảm giá (Dynamic Voucher):** Xử lý tranh chấp khi mã chỉ còn 1 lượt cuối.				
- *Tech Key:* Concurrency Check (Database Locking).				
				
# 3 QUY TẮC BẤT DI BẤT DỊCH (TUÂN THỦ NGHIÊM NGẶT)				
1. **Bám sát Project:** Chỉ sử dụng Java/Spring Boot. Mọi code phải map trực tiếp vào 1 trong 13 tính năng trên.				
2. **Cấu trúc File Mẹ/Con:** Khi đề xuất code, PHẢI nêu rõ file đó thuộc package nào, quan hệ với file khác ra sao (Ví dụ: "Update `service/ParkingService.java` (File mẹ) để gọi hàm tính tiền từ `utils/PricingUtils.java` (File con)").				
3. **Chỉ Thêm - Không Bớt:** Tuyệt đối KHÔNG xóa logic cũ đang chạy. Mọi code mới phải là BỔ SUNG (Add-on). Nếu cần sửa, hãy comment code cũ lại thay vì xóa trắng.				

Giai đoạn (Phase)	Tuần (Week)	Công việc Chính (Tasks)	Tech Key J2EE Áp dụng (Focus)	Phân công (Role Suggestion)	Output (Kết quả)
PHASE 1: KHỞI TẠO & NỀN TÁNG	1	1. Thiết kế Database (ERD): Vẽ các bảng User, Slot, Booking, Transaction...	- Chuẩn hóa Quan hệ DB	- Cả nhóm họp chốt DB.	- Sơ đồ ERD hoàn chỉnh.
		2. Setup Project: Init Spring Boot, Cấu trúc thư mục (Controller/Service/Repo), Git Repo.	- Cấu trúc Layer chuẩn Enterprise	- Leader setup Git.	- Project chạy được trang "Hello World".
		3. Frontend Skeleton: Dựng giao diện cơ bản (Login, Dashboard) bằng HTML/Bootstrap.			
PHASE 2: CORE LOGIC & SECURITY	2 - 3	1. Authentication: Đăng ký, Đăng nhập, Phân quyền (Admin/User).	Tech #10: Middleware/Filter	- Backend 1: Auth.	- Đăng nhập được (Google/FB).
		2. Quản lý Slot (CRUD): Thêm/Sửa/Xóa bãi xe.	Tech #5: WebSocket (STOMP)	- Backend 2: CRUD Slot.	- Admin thấy danh sách Slot.
		3. Real-time Map (Base): Dựng WebSocket để Client kết nối được với Server.	Tech #9: Data Isolation	- Frontend: Vẽ lưới bãi xe.	- Map hiển thị trên Web.
PHASE 3: NGHIỆP VỤ PHỨC TẠP (J2EE)	4 - 6	1. Booking & Concurrency: Xử lý logic đặt chỗ, chặn 2 người đặt cùng lúc.	Tech #1: Optimistic Locking (@Version)	- Backend Senior: Làm Concurrency.	- Chức năng Đặt vé hoàn thiện.
		2. Scheduler: Viết Job tự quét vé quá hạn 15p.	Tech #2: Scheduler (@Scheduled)	- Backend Junior: Làm Scheduler/QR.	- Không bị lỗi trùng slot.
		3. QR Code: Sinh mã vé sau khi đặt thành công.	Tech #7: Digital Signature	- Tester: Bắt đầu viết Test Plan.	- Vé tự hủy nếu quá giờ.
PHASE 4: TÀI CHÍNH & THUẬT TOÁN	7 - 8	1. Tính tiền & Ví: Nạp tiền, Thanh toán, Trừ tiền khi Check-out.	Tech #12: @Transactional (ACID)	- Backend: Tập trung module Payment.	- Hệ thống tính tiền đúng.
		2. Vé tháng: Logic gia hạn tự động.	Tech #3: Dynamic Pricing	- Frontend: Làm giao diện Ví/History.	- Nạp/Rút tiền mượt mà.
		3. Thuật toán gợi ý: API tìm slot gần nhất.	Tech #11: Auto-renewal	- Tester: Test tính tiền (Unit Test).	- Gợi ý slot thông minh.
			Tech #4: Algorithm		
PHASE 5: ENTERPRISE & TESTING	9 - 10	1. Audit Log: Gắn AOP ghi log toàn bộ hệ thống.	Tech #8: Spring AOP	- Backend: Gắn AOP, Voucher.	- File Log hệ thống.
		2. Simulation: Giả lập upload ảnh check-in (OCR).	Tech #6: Mock OCR	- Tester: Chạy Automation Test.	- Báo cáo Kiểm thử (JUnit/Selenium).
		3. Testing: Chạy Full Unit Test, Integration Test, Load Test (JMeter).	Tech #13: Voucher Locking	- Cả nhóm: Fix Bug.	- Hệ thống sạch Bug.
PHASE 6: ĐÓNG GÓI & DEMO	11	1. Quay Video Demo: Dự phòng rủi ro.	- Tổng hợp toàn bộ	- Leader thuyết trình.	- Slide, Video, Source Code.
		2. Slide báo cáo: Tổng hợp kiến trúc, test case.		- Các member hỗ trợ demo.	- Tâm thế sẵn sàng bảo vệ.
		3. Rehearsal: Tập diễn thử kịch bản Demo.			