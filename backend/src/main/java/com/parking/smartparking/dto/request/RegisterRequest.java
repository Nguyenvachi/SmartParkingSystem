package com.parking.smartparking.dto.request;

// import com.parking.smartparking.entity.User.Role; // [FIX 1 - SECURITY] Import Role bị tắt cùng với field bên dưới
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * DTO (Data Transfer Object) cho API Đăng ký
 *
 * Quy tắc Enterprise: KHÔNG dùng Entity để nhận data từ Client Lý do: - Tránh
 * lộ thông tin nhạy cảm (password, createdAt...) - Kiểm soát chặt chẽ dữ liệu
 * đầu vào (Validation)
 *
 * Validation Annotations: - @NotBlank: Không được null hoặc rỗng - @Email: Phải
 * đúng format email - @Size: Giới hạn độ dài
 */
@Data
public class RegisterRequest {

    @NotBlank(message = "Họ tên không được để trống")
    @Size(max = 100, message = "Họ tên không được quá 100 ký tự")
    private String fullName;

    @NotBlank(message = "Email không được để trống")
    @Email(message = "Email không đúng định dạng")
    @Size(max = 100, message = "Email không được quá 100 ký tự")
    private String email;

    @NotBlank(message = "Mật khẩu không được để trống")
    @Size(min = 6, message = "Mật khẩu phải có ít nhất 6 ký tự")
    private String password;

    /**
     * [FIX 1 - SECURITY] Role field bị DISABLED để chặn Privilege Escalation
     * Attack. Client KHÔNG được phép tự đặt ROLE_ADMIN khi đăng ký. Server sẽ
     * luôn tự gán ROLE_USER trong AuthService.register().
     *
     * Kịch bản tấn công đã bị chặn: POST /api/auth/register { "role":
     * "ROLE_ADMIN" } -> Bị bỏ qua hoàn toàn
     */
    // private Role role = Role.ROLE_USER; // [DISABLED - Bảo mật: Server tự gán Role]
}
