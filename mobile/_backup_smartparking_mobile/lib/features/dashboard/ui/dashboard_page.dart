import 'package:flutter/material.dart';

import '../../../core/http/api_client.dart';
import '../../../core/models/parking_slot.dart';

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
      setState(() => _slots = list);
    } on ApiException catch (e) {
      setState(() => _error = e.message);
    } catch (_) {
      setState(() => _error = 'Không tải được danh sách slot.');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
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
      child: ListView.separated(
        padding: const EdgeInsets.all(12),
        itemCount: _slots.length,
        separatorBuilder: (_, __) => const Divider(height: 1),
        itemBuilder: (context, index) {
          final s = _slots[index];
          return ListTile(
            title: Text('${s.slotName} (${s.type})'),
            subtitle: Text(
              'Status: ${s.status} | ${s.pricePerHour.toStringAsFixed(0)} VND/giờ',
            ),
          );
        },
      ),
    );
  }
}
