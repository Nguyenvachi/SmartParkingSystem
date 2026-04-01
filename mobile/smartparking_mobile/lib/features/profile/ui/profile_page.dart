import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../../core/http/api_client.dart';
import '../../../core/models/payment_create_response.dart';
import '../../../core/models/payment_order_status.dart';
import '../../../core/models/wallet_summary.dart';

class ProfilePage extends StatefulWidget {
  const ProfilePage({super.key});

  @override
  State<ProfilePage> createState() => _ProfilePageState();
}

class _ProfilePageState extends State<ProfilePage> with WidgetsBindingObserver {
  final _api = ApiClient();

  bool _loading = true;
  bool _creatingPayment = false;
  String? _error;
  WalletSummary? _summary;

  String? _pendingOrderId;
  bool _checkStatusOnResume = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _load();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed && _checkStatusOnResume) {
      _checkStatusOnResume = false;
      final orderId = _pendingOrderId;
      if (orderId != null && orderId.isNotEmpty) {
        _checkPaymentStatus(orderId);
      }
    }
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

  Future<void> _startTopUpFlow() async {
    if (_creatingPayment) return;

    final selection = await showDialog<_TopUpSelection>(
      context: context,
      builder: (ctx) {
        final amountCtrl = TextEditingController();
        return AlertDialog(
          title: const Text('Nạp tiền vào Ví'),
          content: TextField(
            controller: amountCtrl,
            keyboardType: TextInputType.number,
            inputFormatters: [FilteringTextInputFormatter.digitsOnly],
            decoration: const InputDecoration(
              labelText: 'Số tiền (VND)',
              hintText: 'Ví dụ: 50000',
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(ctx).pop(),
              child: const Text('Hủy'),
            ),
            FilledButton(
              onPressed: () {
                final amount = int.tryParse(amountCtrl.text.trim()) ?? 0;
                Navigator.of(ctx).pop(
                  _TopUpSelection(provider: 'momo', amount: amount),
                );
              },
              child: const Text('MoMo'),
            ),
            FilledButton(
              onPressed: () {
                final amount = int.tryParse(amountCtrl.text.trim()) ?? 0;
                Navigator.of(ctx).pop(
                  _TopUpSelection(provider: 'vnpay', amount: amount),
                );
              },
              child: const Text('VNPay'),
            ),
          ],
        );
      },
    );

    if (selection == null) return;
    if (selection.amount <= 0) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Số tiền không hợp lệ.')),
        );
      }
      return;
    }

    setState(() => _creatingPayment = true);
    try {
      final data = await _api.post(
        '/payments/topup/${selection.provider}',
        body: {
          'amount': selection.amount,
          'description': 'Top up from mobile',
        },
      );

      final payment = PaymentCreateResponse.fromJson(
        (data as Map).cast<String, dynamic>(),
      );

      if (payment.paymentUrl.isEmpty || payment.orderId.isEmpty) {
        throw ApiException('Không tạo được link thanh toán.');
      }

      _pendingOrderId = payment.orderId;

      final uri = Uri.tryParse(payment.paymentUrl);
      if (uri == null) {
        throw ApiException('Link thanh toán không hợp lệ.');
      }

      final ok = await launchUrl(uri, mode: LaunchMode.externalApplication);
      if (!ok) {
        throw ApiException('Không mở được cổng thanh toán.');
      }

      _checkStatusOnResume = true;
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text(
              'Đã mở cổng thanh toán. Sau khi thanh toán xong, quay lại app để cập nhật số dư.',
            ),
          ),
        );
      }
    } on ApiException catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text(e.message)));
      }
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Không thể tạo thanh toán.')),
        );
      }
    } finally {
      if (mounted) setState(() => _creatingPayment = false);
    }
  }

  Future<void> _checkPaymentStatus(String orderId) async {
    try {
      final data = await _api.get('/payments/orders/$orderId');
      final st = PaymentOrderStatus.fromJson(
        (data as Map).cast<String, dynamic>(),
      );

      if (!mounted) return;

      if (st.status == 'SUCCESS') {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              'Thanh toán thành công (${st.provider}). Đang cập nhật số dư…',
            ),
          ),
        );
        await _load();
      } else if (st.status == 'FAILED') {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              'Thanh toán thất bại: ${st.message ?? 'Không rõ lý do'}',
            ),
          ),
        );
      } else {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text(
              'Giao dịch đang chờ xác nhận. Bạn có thể bấm Làm mới để cập nhật.',
            ),
          ),
        );
      }
    } on ApiException catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text(e.message)));
    } catch (_) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
            content: Text('Không kiểm tra được trạng thái giao dịch.')),
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
              onPressed: _creatingPayment ? null : _startTopUpFlow,
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
                    'Sau khi thanh toán xong, quay lại app và kéo xuống để cập nhật số dư.',
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

class _TopUpSelection {
  final String provider;
  final int amount;

  const _TopUpSelection({
    required this.provider,
    required this.amount,
  });
}
