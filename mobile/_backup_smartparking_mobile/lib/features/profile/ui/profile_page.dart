import 'package:flutter/material.dart';

import '../../../core/http/api_client.dart';
import '../../../core/models/wallet_summary.dart';

class ProfilePage extends StatefulWidget {
  const ProfilePage({super.key});

  @override
  State<ProfilePage> createState() => _ProfilePageState();
}

class _ProfilePageState extends State<ProfilePage> {
  final _api = ApiClient();

  bool _loading = true;
  String? _error;
  WalletSummary? _summary;

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
      final data = await _api.get('/wallet');
      setState(
        () => _summary = WalletSummary.fromJson(
          (data as Map).cast<String, dynamic>(),
        ),
      );
    } on ApiException catch (e) {
      setState(() => _error = e.message);
    } catch (_) {
      setState(() => _error = 'Không tải được ví.');
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

    final s = _summary;
    if (s == null) {
      return const Center(child: Text('Chưa có dữ liệu ví.'));
    }

    return RefreshIndicator(
      onRefresh: _load,
      child: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          ListTile(
            leading: const Icon(Icons.person),
            title: Text(s.fullName),
            subtitle: Text('Plan: ${s.membershipPlan}'),
          ),
          const Divider(),
          ListTile(
            leading: const Icon(Icons.account_balance_wallet),
            title: const Text('Số dư ví'),
            subtitle: Text('${s.walletBalance.toStringAsFixed(0)} VND'),
          ),
          const SizedBox(height: 12),
          const Text(
            'Gợi ý: Nạp tiền có thể làm trên Web (MoMo/VNPay) hoặc mở rộng payment trong Mobile sau.',
          ),
        ],
      ),
    );
  }
}
