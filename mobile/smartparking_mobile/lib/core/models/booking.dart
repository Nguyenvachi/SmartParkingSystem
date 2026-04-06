class Booking {
  final int bookingId;
  final int slotId;
  final String slotName;
  final String status;
  final String? vehiclePlate;
  final String? qrCodeBase64;
  final String? appliedVoucherCode;

  Booking({
    required this.bookingId,
    required this.slotId,
    required this.slotName,
    required this.status,
    this.vehiclePlate,
    this.qrCodeBase64,
    this.appliedVoucherCode,
  });

  factory Booking.fromJson(Map<String, dynamic> json) {
    return Booking(
      bookingId: (json['bookingId'] as num).toInt(),
      slotId: (json['slotId'] as num).toInt(),
      slotName: (json['slotName'] ?? '') as String,
      status: (json['status'] ?? '') as String,
      vehiclePlate: json['vehiclePlate'] as String?,
      qrCodeBase64: json['qrCodeBase64'] as String?,
      appliedVoucherCode: json['appliedVoucherCode'] as String?,
    );
  }
}
