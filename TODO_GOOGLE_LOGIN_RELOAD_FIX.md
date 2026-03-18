# TODO fix nhanh: Google Login bị reload không vào dashboard

## Mục tiêu
- Sửa vòng lặp sau Google OAuth2 (đăng nhập xong quay lại login hoặc bị đá về login ngay).
- Bám Tech Key #10 (Middleware/Filter - Authentication).
- Tuân thủ quy tắc: chỉ thêm, không xóa logic cũ.

## Kết quả quét hiện tại (điểm nghi ngờ chính)
1. Frontend đang tự kiểm tra JWT ở client bằng cách parse payload trong `frontend/js/auth.js`.
2. Hàm parse JWT hiện dùng `atob` trực tiếp cho Base64URL, chưa xử lý đủ padding.
3. Nếu parse lỗi -> token bị coi là invalid -> `frontend/js/main.js` redirect về login với `reason=expired`.
4. Dashboard hiện parse query OAuth2 ở 2 nơi: script inline trong `frontend/dashboard.html` và hàm hydrate trong `frontend/js/auth.js`.
5. Login button Google trong `frontend/login.html` đang có href hard-code, dù JS đã override bằng config.

## TODO ưu tiên P0 (fix ngay)
1. Harden parse JWT ở frontend
- File mẹ: `frontend/js/auth.js`
- File con (bổ sung helper): decode Base64URL chuẩn (thêm padding `=` theo modulo 4) trước khi `atob`.
- Cách làm: thêm helper mới, giữ hàm cũ dưới dạng comment để trace.
- Kỳ vọng: không còn false-negative token khi OAuth2 redirect trả JWT hợp lệ.

2. Chuẩn hóa 1 nguồn xử lý OAuth2 query params
- File mẹ: `frontend/js/auth.js` (hàm `hydrateUserFromOAuth2QueryParams`)
- File con: `frontend/dashboard.html` (script inline OAuth2)
- Cách làm: giữ script inline cũ nhưng comment là legacy; chuyển dashboard chỉ gọi hàm chung từ auth.js.
- Kỳ vọng: không race-condition hoặc overwrite localStorage không nhất quán.

3. Đồng nhất URL OAuth2 từ config (tránh hard-code)
- File mẹ: `frontend/login.html`
- File con: `frontend/js/config.js`, `frontend/js/auth.js`
- Cách làm: href fallback vẫn giữ, nhưng ưu tiên config runtime; bổ sung note rõ môi trường local/staging.
- Kỳ vọng: đổi môi trường không bị lệch host gây redirect sai.

## TODO ưu tiên P1 (ổn định luồng)
4. Thêm log quan sát OAuth2 redirect và JWT state
- File mẹ: `backend/src/main/java/com/parking/smartparking/config/OAuth2SuccessHandler.java`
- File con: `frontend/js/main.js`
- Cách làm: thêm log an toàn (không in full token), thêm marker khi redirect vì token invalid.
- Kỳ vọng: debug nhanh nếu còn loop.

5. Kiểm tra cấu hình redirect-uri trên Google Cloud Console
- File liên quan: `backend/src/main/resources/application.properties`
- Giá trị backend callback đang dùng: `{baseUrl}/login/oauth2/code/{registrationId}`.
- Cần bảo đảm URI thực tế đã whitelist đúng trong Google Console: `http://localhost:8080/login/oauth2/code/google`.

## TODO ưu tiên P2 (hậu kiểm)
6. Bổ sung test checklist manual cho QA
- Case 1: user mới login Google lần đầu -> vào dashboard, localStorage có token.
- Case 2: user cũ login Google -> vào dashboard, role giữ nguyên.
- Case 3: token hết hạn -> redirect login đúng 1 lần, không loop.
- Case 4: chạy frontend bằng `localhost` và `127.0.0.1` đều đăng nhập được.

## Tiêu chí done
- Google login thành công, không reload vòng lặp.
- Sau redirect, API có `Authorization: Bearer ...` hợp lệ.
- Không phá login thường bằng email/password.
- Không xóa logic cũ; phần thay thế đều có comment legacy.
