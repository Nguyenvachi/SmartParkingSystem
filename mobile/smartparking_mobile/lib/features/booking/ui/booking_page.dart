import 'dart:convert';

import 'package:flutter/material.dart';

import '../../../core/http/api_client.dart';
import '../../../core/models/booking.dart';

class BookingPage extends StatefulWidget {
  const BookingPage({super.key});

  @override
  State<BookingPage> createState() => _BookingPageState();
}

class _BookingPageState extends State<BookingPage> {
  final _api = ApiClient();

  bool _loading = true;
  String? _error;
  List<Booking> _bookings = const [];

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });

    try {
      final data = await _api.get('/bookings');
      final list = (data as List)
          .map((e) => Booking.fromJson((e as Map).cast<String, dynamic>()))
          .toList();
      setState(() => _bookings = list);
    } on ApiException catch (e) {
      setState(() => _error = e.message);
    } catch (_) {
      setState(() => _error = 'Không tải được danh sách booking.');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _createBooking() async {
    final slotIdCtrl = TextEditingController();
    final plateCtrl = TextEditingController();

    final ok = await showDialog<bool>(
      context: context,
      builder: (context) {
        return AlertDialog(
          title: const Text('Tạo booking'),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              TextField(
                controller: slotIdCtrl,
                keyboardType: TextInputType.number,
                decoration: const InputDecoration(labelText: 'Slot ID'),
              ),
              const SizedBox(height: 8),
              TextField(
                controller: plateCtrl,
                decoration: const InputDecoration(
                  labelText: 'Biển số (optional, vd 29A12345)',
                ),
              ),
            ],
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context, false),
              child: const Text('Hủy'),
            ),
            FilledButton(
              onPressed: () => Navigator.pop(context, true),
              child: const Text('Đặt'),
            ),
          ],
        );
      },
    );

    if (ok != true) return;

    final slotId = int.tryParse(slotIdCtrl.text.trim());
    if (slotId == null) {
      _toast('Slot ID không hợp lệ.');
      return;
    }

    try {
      final body = {'slotId': slotId, 'vehiclePlate': plateCtrl.text.trim()};
      if ((body['vehiclePlate'] as String).isEmpty) {
        body.remove('vehiclePlate');
      }

      final data = await _api.post('/bookings', body: body);
      final booking = Booking.fromJson((data as Map).cast<String, dynamic>());
      _toast(data['message']?.toString() ?? 'Đặt chỗ thành công');

      if (booking.qrCodeBase64 != null && booking.qrCodeBase64!.isNotEmpty) {
        _openQr(booking);
      }

      await _load();
    } on ApiException catch (e) {
      _toast(e.message);
    } catch (_) {
      _toast('Tạo booking thất bại.');
    }
  }

  Future<void> _checkIn(int bookingId) async {
    try {
      final data = await _api.post('/bookings/$bookingId/checkin', body: {});
      _toast(data['message']?.toString() ?? 'Check-in thành công');
      await _load();
    } on ApiException catch (e) {
      _toast(e.message);
    } catch (_) {
      _toast('Check-in thất bại.');
    }
  }

  String _normalizeBase64(String raw) {
    final trimmed = raw.trim();
    final comma = trimmed.indexOf(',');
    if (trimmed.startsWith('data:') && comma != -1) {
      return trimmed.substring(comma + 1);
    }
    return trimmed;
  }

  void _openQr(Booking booking) {
    showDialog(
      context: context,
      builder: (context) {
        Widget qrWidget;
        try {
          final bytes = base64Decode(_normalizeBase64(booking.qrCodeBase64!));
          qrWidget = Image.memory(bytes, width: 240, height: 240);
        } catch (_) {
          qrWidget = const Text('Không đọc được QR từ dữ liệu trả về.');
        }

        return AlertDialog(
          title: Text('QR Booking #${booking.bookingId}'),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              qrWidget,
              const SizedBox(height: 12),
              Text(
                'Vui lòng Check-in tại cổng trong vòng 15 phút',
                style: TextStyle(
                  color: Colors.red.shade700,
                  fontWeight: FontWeight.w600,
                ),
                textAlign: TextAlign.center,
              ),
            ],
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context),
              child: const Text('Đóng'),
            ),
          ],
        );
      },
    );
  }

  void _toast(String msg) {
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(msg)));
  }

  @override
  Widget build(BuildContext context) {
    if (_loading) {
      return const Center(child: CircularProgressIndicator());
    }

    if (_error != null) {
      return Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(_error!),
            const SizedBox(height: 12),
            OutlinedButton(onPressed: _load, child: const Text('Thử lại')),
          ],
        ),
      );
    }

    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.all(12),
          child: SizedBox(
            width: double.infinity,
            child: FilledButton.icon(
              onPressed: _createBooking,
              icon: const Icon(Icons.add),
              label: const Text('Tạo booking'),
            ),
          ),
        ),
        const Divider(height: 1),
        Expanded(
          child: RefreshIndicator(
            onRefresh: _load,
            child: ListView.builder(
              padding: const EdgeInsets.all(12),
              itemCount: _bookings.length,
              itemBuilder: (context, index) {
                final b = _bookings[index];
                return Card(
                  margin: const EdgeInsets.only(bottom: 12),
                  child: ListTile(
                    title: Text('Booking #${b.bookingId} | ${b.slotName}'),
                    subtitle: Text(
                      'Status: ${b.status}${b.vehiclePlate != null ? ' | Plate: ${b.vehiclePlate}' : ''}',
                    ),
                    trailing: Wrap(
                      spacing: 8,
                      children: [
                        IconButton(
                          onPressed: (b.qrCodeBase64 != null &&
                                  b.qrCodeBase64!.isNotEmpty)
                              ? () => _openQr(b)
                              : null,
                          icon: const Icon(Icons.qr_code),
                        ),
                        IconButton(
                          onPressed: b.status == 'PENDING'
                              ? () => _checkIn(b.bookingId)
                              : null,
                          icon: const Icon(Icons.login),
                        ),
                      ],
                    ),
                  ),
                );
              },
            ),
          ),
        ),
      ],
    );
  }
}
