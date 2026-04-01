import 'dart:convert';

import 'package:flutter/material.dart';

import '../../../core/http/api_client.dart';
import '../../../core/models/booking.dart';
import '../../../core/models/parking_slot.dart';
import '../../../core/models/wallet_summary.dart';

class DashboardPage extends StatefulWidget {
  const DashboardPage({super.key});

  @override
  State<DashboardPage> createState() => _DashboardPageState();
}

class _DashboardPageState extends State<DashboardPage> {
  final _api = ApiClient();

  bool _loading = true;
  String? _error;
  List<ParkingSlot> _slots = const [];

  WalletSummary? _wallet;

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
      final data = await _api.get('/slots');
      final list = (data as List)
          .map((e) => ParkingSlot.fromJson((e as Map).cast<String, dynamic>()))
          .toList();

      WalletSummary? wallet;
      try {
        final walletData = await _api.get('/wallet');
        wallet =
            WalletSummary.fromJson((walletData as Map).cast<String, dynamic>());
      } catch (_) {
        wallet = null;
      }

      setState(() {
        _slots = list;
        _wallet = wallet;
      });
    } on ApiException catch (e) {
      setState(() => _error = e.message);
    } catch (_) {
      setState(() => _error = 'Không tải được danh sách slot.');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  ({Color bg, Color border, Color text}) _statusColors(String status) {
    switch (status.toUpperCase()) {
      case 'AVAILABLE':
        return (
          bg: Colors.green.shade100,
          border: Colors.green.shade700,
          text: Colors.green.shade900,
        );
      case 'RESERVED':
      case 'PENDING':
        return (
          bg: Colors.orange.shade100,
          border: Colors.orange.shade700,
          text: Colors.orange.shade900,
        );
      case 'OCCUPIED':
      case 'CHECKED_IN':
        return (
          bg: Colors.red.shade100,
          border: Colors.red.shade700,
          text: Colors.red.shade900,
        );
      default:
        return (
          bg: Colors.grey.shade100,
          border: Colors.grey.shade500,
          text: Colors.grey.shade800,
        );
    }
  }

  Future<void> _openSlotSheet(ParkingSlot slot) async {
    if (slot.status.toUpperCase() != 'AVAILABLE') return;

    final confirm = await showModalBottomSheet<bool>(
      context: context,
      showDragHandle: true,
      isScrollControlled: false,
      builder: (context) {
        return SafeArea(
          child: Padding(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 16),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  slot.slotName,
                  style: Theme.of(context).textTheme.titleLarge,
                ),
                const SizedBox(height: 8),
                Text('Loại xe: ${slot.type}'),
                const SizedBox(height: 4),
                Text('Giá: ${slot.pricePerHour.toStringAsFixed(0)} VND/giờ'),
                const SizedBox(height: 16),
                SizedBox(
                  width: double.infinity,
                  child: FilledButton(
                    onPressed: () => Navigator.pop(context, true),
                    child: const Padding(
                      padding: EdgeInsets.symmetric(vertical: 12),
                      child: Text('Xác nhận Giữ chỗ'),
                    ),
                  ),
                ),
              ],
            ),
          ),
        );
      },
    );

    if (confirm == true) {
      await _createBookingForSlot(slot);
    }
  }

  Future<void> _createBookingForSlot(ParkingSlot slot) async {
    try {
      final data = await _api.post('/bookings', body: {'slotId': slot.id});
      final map = (data as Map).cast<String, dynamic>();
      final booking = Booking.fromJson(map);
      final msg = map['message'] is String
          ? (map['message'] as String)
          : 'Đặt chỗ thành công';
      _toast(msg);

      if (booking.qrCodeBase64 != null && booking.qrCodeBase64!.isNotEmpty) {
        _openQrDialog(booking);
      }

      await _load();
    } on ApiException catch (e) {
      _toast(e.message);
    } catch (_) {
      _toast('Tạo booking thất bại.');
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

  void _openQrDialog(Booking booking) {
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

    return RefreshIndicator(
      onRefresh: _load,
      child: LayoutBuilder(
        builder: (context, constraints) {
          final crossAxisCount = constraints.maxWidth >= 600 ? 3 : 2;

          return CustomScrollView(
            physics: const AlwaysScrollableScrollPhysics(),
            slivers: [
              SliverPadding(
                padding: const EdgeInsets.fromLTRB(12, 12, 12, 8),
                sliver: SliverToBoxAdapter(
                  child: Card(
                    child: Padding(
                      padding: const EdgeInsets.all(12),
                      child: Row(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          const CircleAvatar(child: Icon(Icons.person)),
                          const SizedBox(width: 12),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(
                                  _wallet?.fullName.isNotEmpty == true
                                      ? _wallet!.fullName
                                      : 'Tài khoản',
                                  style:
                                      Theme.of(context).textTheme.titleMedium,
                                ),
                                const SizedBox(height: 4),
                                Text(
                                  'Số dư ví: ${(_wallet?.walletBalance ?? 0).toStringAsFixed(0)} VND',
                                  style: Theme.of(context).textTheme.titleSmall,
                                ),
                                const SizedBox(height: 2),
                                Text(
                                  'Gói: ${_wallet?.membershipPlan ?? 'NONE'}',
                                  style: Theme.of(context).textTheme.bodySmall,
                                ),
                              ],
                            ),
                          ),
                          IconButton(
                            onPressed: _load,
                            icon: const Icon(Icons.refresh),
                            tooltip: 'Làm mới',
                          ),
                        ],
                      ),
                    ),
                  ),
                ),
              ),
              SliverPadding(
                padding: const EdgeInsets.fromLTRB(12, 0, 12, 12),
                sliver: SliverGrid(
                  delegate: SliverChildBuilderDelegate(
                    (context, index) {
                      final s = _slots[index];
                      final colors = _statusColors(s.status);

                      return InkWell(
                        onTap: s.status.toUpperCase() == 'AVAILABLE'
                            ? () => _openSlotSheet(s)
                            : null,
                        child: Container(
                          decoration: BoxDecoration(
                            color: colors.bg,
                            borderRadius: BorderRadius.circular(12),
                            border:
                                Border.all(color: colors.border, width: 1.2),
                          ),
                          padding: const EdgeInsets.all(12),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                s.slotName,
                                style: Theme.of(context)
                                    .textTheme
                                    .titleLarge
                                    ?.copyWith(
                                      color: colors.text,
                                      fontWeight: FontWeight.w800,
                                    ),
                              ),
                              const SizedBox(height: 6),
                              Text(
                                s.status,
                                style: Theme.of(context)
                                    .textTheme
                                    .bodySmall
                                    ?.copyWith(color: colors.text),
                              ),
                              const Spacer(),
                              Text(
                                s.type,
                                style: Theme.of(context)
                                    .textTheme
                                    .bodyMedium
                                    ?.copyWith(
                                      color: colors.text,
                                      fontWeight: FontWeight.w600,
                                    ),
                              ),
                              const SizedBox(height: 2),
                              Text(
                                '${s.pricePerHour.toStringAsFixed(0)} VND/giờ',
                                style: Theme.of(context)
                                    .textTheme
                                    .bodySmall
                                    ?.copyWith(color: colors.text),
                              ),
                            ],
                          ),
                        ),
                      );
                    },
                    childCount: _slots.length,
                  ),
                  gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
                    crossAxisCount: crossAxisCount,
                    mainAxisSpacing: 12,
                    crossAxisSpacing: 12,
                    childAspectRatio: 1.05,
                  ),
                ),
              ),
            ],
          );
        },
      ),
    );
  }
}
