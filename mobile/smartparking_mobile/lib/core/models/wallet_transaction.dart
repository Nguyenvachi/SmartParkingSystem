class WalletTransaction {
  final int id;
  final String type;
  final double amount;
  final double balanceAfter;
  final String? description;
  final DateTime? createdAt;

  WalletTransaction({
    required this.id,
    required this.type,
    required this.amount,
    required this.balanceAfter,
    this.description,
    this.createdAt,
  });

  factory WalletTransaction.fromJson(Map<String, dynamic> json) {
    final amountRaw = json['amount'];
    final balRaw = json['balanceAfter'];

    return WalletTransaction(
      id: (json['id'] as num).toInt(),
      type: (json['type'] ?? '') as String,
      amount: amountRaw is num
          ? amountRaw.toDouble()
          : double.tryParse('$amountRaw') ?? 0.0,
      balanceAfter:
          balRaw is num ? balRaw.toDouble() : double.tryParse('$balRaw') ?? 0.0,
      description: json['description'] as String?,
      createdAt: json['createdAt'] is String
          ? DateTime.tryParse(json['createdAt'] as String)
          : null,
    );
  }
}
