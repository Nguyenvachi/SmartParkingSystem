class UserProfile {
  final int userId;
  final String fullName;
  final String email;
  final String? phoneNumber;
  final String? avatarUrl;
  final bool emailVerified;
  final String? authProvider;
  final bool notificationEmailEnabled;
  final bool notificationPushEnabled;

  UserProfile({
    required this.userId,
    required this.fullName,
    required this.email,
    this.phoneNumber,
    this.avatarUrl,
    required this.emailVerified,
    this.authProvider,
    required this.notificationEmailEnabled,
    required this.notificationPushEnabled,
  });

  factory UserProfile.fromJson(Map<String, dynamic> json) {
    return UserProfile(
      userId: (json['userId'] as num).toInt(),
      fullName: (json['fullName'] ?? '') as String,
      email: (json['email'] ?? '') as String,
      phoneNumber: json['phoneNumber'] as String?,
      avatarUrl: json['avatarUrl'] as String?,
      emailVerified: json['emailVerified'] == true,
      authProvider: json['authProvider'] as String?,
      notificationEmailEnabled: json['notificationEmailEnabled'] == true,
      notificationPushEnabled: json['notificationPushEnabled'] == true,
    );
  }
}
