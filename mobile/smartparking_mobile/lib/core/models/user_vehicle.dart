class UserVehicle {
  final int id;
  final String plateNumber;
  final String vehicleType;
  final String? color;

  UserVehicle({
    required this.id,
    required this.plateNumber,
    required this.vehicleType,
    this.color,
  });

  factory UserVehicle.fromJson(Map<String, dynamic> json) {
    return UserVehicle(
      id: (json['id'] as num).toInt(),
      plateNumber: (json['plateNumber'] ?? '') as String,
      vehicleType: (json['vehicleType'] ?? '') as String,
      color: json['color'] as String?,
    );
  }
}
