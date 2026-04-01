class PaymentOrderStatus {
  final String provider;
  final String orderId;
  final String status;
  final double amount;
  final String? message;

  PaymentOrderStatus({
    required this.provider,
    required this.orderId,
    required this.status,
    required this.amount,
    required this.message,
  });

  factory PaymentOrderStatus.fromJson(Map<String, dynamic> json) {
    final amountRaw = json['amount'];
    final amountNum = amountRaw is num
        ? amountRaw.toDouble()
        : double.tryParse((amountRaw ?? '0').toString()) ?? 0;

    return PaymentOrderStatus(
      provider: (json['provider'] ?? '').toString(),
      orderId: (json['orderId'] ?? '').toString(),
      status: (json['status'] ?? '').toString(),
      amount: amountNum,
      message: json['message']?.toString(),
    );
  }
}
