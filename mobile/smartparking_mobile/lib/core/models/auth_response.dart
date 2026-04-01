class AuthResponse {
  final int userId;
  final String fullName;
  final String email;
  final String role;
  final String? branchCode;
  final String token;

  AuthResponse({
    required this.userId,
    required this.fullName,
    required this.email,
    required this.role,
    required this.token,
    this.branchCode,
  });

  factory AuthResponse.fromJson(Map<String, dynamic> json) {
    return AuthResponse(
      userId: (json['userId'] as num).toInt(),
      fullName: (json['fullName'] ?? '') as String,
      email: (json['email'] ?? '') as String,
      role: (json['role'] ?? 'ROLE_USER') as String,
      branchCode: json['branchCode'] as String?,
      token: (json['token'] ?? '') as String,
    );
  }
}
