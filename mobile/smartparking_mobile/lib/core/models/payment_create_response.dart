class PaymentCreateResponse {
  final String provider;
  final String orderId;
  final String paymentUrl;

  PaymentCreateResponse({
    required this.provider,
    required this.orderId,
    required this.paymentUrl,
  });

  factory PaymentCreateResponse.fromJson(Map<String, dynamic> json) {
    return PaymentCreateResponse(
      provider: (json['provider'] ?? '').toString(),
      orderId: (json['orderId'] ?? '').toString(),
      paymentUrl: (json['paymentUrl'] ?? '').toString(),
    );
  }
}
