import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../../core/config/api_config.dart';
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

  Uri _frontendDashboardUri() {
    const explicitBase = String.fromEnvironment('FRONTEND_BASE_URL');
    if (explicitBase.trim().isNotEmpty) {
      final base = explicitBase.endsWith('/')
          ? explicitBase.substring(0, explicitBase.length - 1)
          : explicitBase;
      return Uri.parse('$base/dashboard');
    }

    final apiUri = Uri.parse(ApiConfig.baseUrl);
    final host = apiUri.host;
    final isLocalHost =
        host == 'localhost' || host == '127.0.0.1' || host == '10.0.2.2';

    final apiPort = apiUri.hasPort ? apiUri.port : 0;
    final scheme = apiUri.scheme.isNotEmpty ? apiUri.scheme : 'http';

    // Heuristic: local dev uses FE:3000, BE:8080. On VPS, FE often runs on 80.
    final int frontendPort;
    if (isLocalHost) {
      frontendPort = (apiPort == 8080 || apiPort == 0) ? 3000 : apiPort;
    } else {
      frontendPort = (apiPort == 8080) ? 80 : (apiPort == 0 ? 80 : apiPort);
    }

    return Uri(
      scheme: scheme,
      host: host,
      port: (frontendPort == 80 || frontendPort == 443) ? null : frontendPort,
      path: '/dashboard',
    );
  }

  Future<void> _openTopUpWeb() async {
    final uri = _frontendDashboardUri();
    final ok = await launchUrl(uri, mode: LaunchMode.externalApplication);
    if (!ok && mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Không mở được trình duyệt: $uri')),
      );
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
          Card(
            child: Padding(
              padding: const EdgeInsets.all(12),
              child: Row(
                children: [
                  const CircleAvatar(child: Icon(Icons.person)),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          s.fullName,
                          style: Theme.of(context).textTheme.titleMedium,
                        ),
                        const SizedBox(height: 2),
                        Text(
                          'Gói: ${s.membershipPlan}',
                          style: Theme.of(context).textTheme.bodySmall,
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 12),
          Card(
            child: Padding(
              padding: const EdgeInsets.all(12),
              child: Row(
                children: [
                  const Icon(Icons.account_balance_wallet),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          'Số dư ví',
                          style: Theme.of(context).textTheme.bodySmall,
                        ),
                        const SizedBox(height: 4),
                        Text(
                          '${s.walletBalance.toStringAsFixed(0)} VND',
                          style: Theme.of(context)
                              .textTheme
                              .titleLarge
                              ?.copyWith(fontWeight: FontWeight.w800),
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
          const SizedBox(height: 16),
          SizedBox(
            width: double.infinity,
            child: FilledButton.icon(
              onPressed: _openTopUpWeb,
              icon: const Icon(Icons.open_in_new),
              label: const Padding(
                padding: EdgeInsets.symmetric(vertical: 12),
                child: Text('Nạp tiền vào Ví (MoMo/VNPay)'),
              ),
            ),
          ),
          const SizedBox(height: 12),
          Card(
            child: Column(
              children: const [
                ListTile(
                  leading: Icon(Icons.info_outline),
                  title: Text('Gợi ý'),
                  subtitle: Text(
                    'Hiện tại nạp tiền thực hiện trên Web. Mobile sẽ tích hợp thanh toán sau.',
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
