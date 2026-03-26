class WalletSummary {
  final int userId;
  final String fullName;
  final double walletBalance;
  final String membershipPlan;

  WalletSummary({
    required this.userId,
    required this.fullName,
    required this.walletBalance,
    required this.membershipPlan,
  });

  factory WalletSummary.fromJson(Map<String, dynamic> json) {
    final bal = json['walletBalance'];
    return WalletSummary(
      userId: (json['userId'] as num).toInt(),
      fullName: (json['fullName'] ?? '') as String,
      walletBalance: bal is num
          ? bal.toDouble()
          : double.tryParse('$bal') ?? 0.0,
      membershipPlan: (json['membershipPlan'] ?? 'NONE') as String,
    );
  }
}
