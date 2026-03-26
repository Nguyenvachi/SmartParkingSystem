class ParkingSlot {
  final int id;
  final String slotName;
  final String type;
  final String status;
  final double pricePerHour;
  final String? branchCode;

  ParkingSlot({
    required this.id,
    required this.slotName,
    required this.type,
    required this.status,
    required this.pricePerHour,
    this.branchCode,
  });

  factory ParkingSlot.fromJson(Map<String, dynamic> json) {
    final price = json['pricePerHour'];
    return ParkingSlot(
      id: (json['id'] as num).toInt(),
      slotName: (json['slotName'] ?? '') as String,
      type: (json['type'] ?? '') as String,
      status: (json['status'] ?? '') as String,
      pricePerHour: price is num
          ? price.toDouble()
          : double.tryParse('$price') ?? 0.0,
      branchCode: json['branchCode'] as String?,
    );
  }
}
