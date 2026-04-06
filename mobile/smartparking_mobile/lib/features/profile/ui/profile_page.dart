import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../../core/http/api_client.dart';
import '../../../core/models/booking.dart';
import '../../../core/models/payment_create_response.dart';
import '../../../core/models/payment_order_status.dart';
import '../../../core/models/user_profile.dart';
import '../../../core/models/user_vehicle.dart';
import '../../../core/models/wallet_summary.dart';
import '../../../core/models/wallet_transaction.dart';

class ProfilePage extends StatefulWidget {
  final Future<void> Function() onLogout;

  const ProfilePage({super.key, required this.onLogout});

  @override
  State<ProfilePage> createState() => _ProfilePageState();
}

class _ProfilePageState extends State<ProfilePage> with WidgetsBindingObserver {
  final _api = ApiClient();

  bool _loading = true;
  bool _creatingPayment = false;
  String? _error;

  UserProfile? _profile;
  List<UserVehicle> _vehicles = const [];
  WalletSummary? _wallet;
  List<WalletTransaction> _transactions = const [];
  List<Booking> _bookings = const [];

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
      final results = await Future.wait([
        _api.get('/users/me'),
        _api.get('/vehicles'),
        _api.get('/wallet'),
        _api.get('/wallet/transactions'),
        _api.get('/bookings'),
      ]);

      final me = UserProfile.fromJson(
        (results[0] as Map).cast<String, dynamic>(),
      );
      final vehicles = (results[1] as List)
          .map((e) => UserVehicle.fromJson((e as Map).cast<String, dynamic>()))
          .toList();
      final wallet = WalletSummary.fromJson(
        (results[2] as Map).cast<String, dynamic>(),
      );
      final tx = (results[3] as List)
          .map((e) =>
              WalletTransaction.fromJson((e as Map).cast<String, dynamic>()))
          .toList();
      final bookings = (results[4] as List)
          .map((e) => Booking.fromJson((e as Map).cast<String, dynamic>()))
          .toList();

      setState(() {
        _profile = me;
        _vehicles = vehicles;
        _wallet = wallet;
        _transactions = tx;
        _bookings = bookings;
      });
    } on ApiException catch (e) {
      setState(() => _error = e.message);
    } catch (_) {
      setState(() => _error = 'Không tải được hồ sơ.');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _updateSettings({
    required bool emailEnabled,
    required bool pushEnabled,
  }) async {
    try {
      final data = await _api.put(
        '/users/me/settings',
        body: {
          'notificationEmailEnabled': emailEnabled,
          'notificationPushEnabled': pushEnabled,
        },
      );

      setState(() {
        _profile = UserProfile.fromJson(
          (data as Map).cast<String, dynamic>(),
        );
      });
    } on ApiException catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text(e.message)));
    } catch (_) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Không lưu được cài đặt.')));
    }
  }

  Future<void> _editPersonalInfo() async {
    final p = _profile;
    if (p == null) return;

    final fullNameCtrl = TextEditingController(text: p.fullName);
    final phoneCtrl = TextEditingController(text: p.phoneNumber ?? '');
    final avatarCtrl = TextEditingController(text: p.avatarUrl ?? '');

    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) {
        return AlertDialog(
          title: const Text('Cập nhật thông tin cá nhân'),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                TextField(
                  controller: fullNameCtrl,
                  decoration: const InputDecoration(labelText: 'Họ tên'),
                ),
                const SizedBox(height: 12),
                TextField(
                  controller: phoneCtrl,
                  decoration: const InputDecoration(labelText: 'Số điện thoại'),
                ),
                const SizedBox(height: 12),
                TextField(
                  controller: avatarCtrl,
                  decoration: const InputDecoration(labelText: 'Avatar URL'),
                ),
              ],
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(ctx).pop(false),
              child: const Text('Hủy'),
            ),
            FilledButton(
              onPressed: () => Navigator.of(ctx).pop(true),
              child: const Text('Lưu'),
            ),
          ],
        );
      },
    );

    if (ok != true) return;
    final full = fullNameCtrl.text.trim();
    if (full.isEmpty) {
      if (!mounted) return;
      ScaffoldMessenger.of(context)
          .showSnackBar(const SnackBar(content: Text('Họ tên không hợp lệ.')));
      return;
    }

    try {
      final data = await _api.put(
        '/users/me',
        body: {
          'fullName': full,
          'phoneNumber':
              phoneCtrl.text.trim().isEmpty ? null : phoneCtrl.text.trim(),
          'avatarUrl':
              avatarCtrl.text.trim().isEmpty ? null : avatarCtrl.text.trim(),
        },
      );
      setState(() {
        _profile = UserProfile.fromJson((data as Map).cast<String, dynamic>());
      });
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Đã cập nhật thông tin.')));
    } on ApiException catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text(e.message)));
    } catch (_) {
      if (!mounted) return;
      ScaffoldMessenger.of(context)
          .showSnackBar(const SnackBar(content: Text('Không cập nhật được.')));
    }
  }

  Future<void> _upsertVehicle({UserVehicle? existing}) async {
    final plateCtrl = TextEditingController(text: existing?.plateNumber ?? '');
    final colorCtrl = TextEditingController(text: existing?.color ?? '');

    String type = (existing?.vehicleType.isNotEmpty == true)
        ? existing!.vehicleType
        : 'MOTORBIKE';

    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) {
        return AlertDialog(
          title:
              Text(existing == null ? 'Thêm phương tiện' : 'Sửa phương tiện'),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                TextField(
                  controller: plateCtrl,
                  decoration: const InputDecoration(labelText: 'Biển số'),
                ),
                const SizedBox(height: 12),
                DropdownButtonFormField<String>(
                  initialValue: type,
                  items: const [
                    DropdownMenuItem(value: 'MOTORBIKE', child: Text('Xe máy')),
                    DropdownMenuItem(value: 'CAR', child: Text('Ô tô')),
                  ],
                  onChanged: (v) => type = v ?? type,
                  decoration: const InputDecoration(labelText: 'Loại xe'),
                ),
                const SizedBox(height: 12),
                TextField(
                  controller: colorCtrl,
                  decoration:
                      const InputDecoration(labelText: 'Màu (optional)'),
                ),
              ],
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(ctx).pop(false),
              child: const Text('Hủy'),
            ),
            FilledButton(
              onPressed: () => Navigator.of(ctx).pop(true),
              child: const Text('Lưu'),
            ),
          ],
        );
      },
    );

    if (ok != true) return;
    final plate = plateCtrl.text.trim();
    if (plate.isEmpty) {
      if (!mounted) return;
      ScaffoldMessenger.of(context)
          .showSnackBar(const SnackBar(content: Text('Biển số không hợp lệ.')));
      return;
    }

    try {
      if (existing == null) {
        final data = await _api.post(
          '/vehicles',
          body: {
            'plateNumber': plate,
            'vehicleType': type,
            'color':
                colorCtrl.text.trim().isEmpty ? null : colorCtrl.text.trim(),
          },
        );
        final created =
            UserVehicle.fromJson((data as Map).cast<String, dynamic>());
        setState(() => _vehicles = [created, ..._vehicles]);
      } else {
        final data = await _api.put(
          '/vehicles/${existing.id}',
          body: {
            'plateNumber': plate,
            'vehicleType': type,
            'color':
                colorCtrl.text.trim().isEmpty ? null : colorCtrl.text.trim(),
          },
        );
        final updated =
            UserVehicle.fromJson((data as Map).cast<String, dynamic>());
        setState(() {
          _vehicles =
              _vehicles.map((v) => v.id == updated.id ? updated : v).toList();
        });
      }
    } on ApiException catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text(e.message)));
    } catch (_) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Không lưu được phương tiện.')));
    }
  }

  Future<void> _deleteVehicle(UserVehicle v) async {
    try {
      await _api.delete('/vehicles/${v.id}');
      setState(() => _vehicles = _vehicles.where((e) => e.id != v.id).toList());
    } on ApiException catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text(e.message)));
    } catch (_) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Không xóa được phương tiện.')));
    }
  }

  Future<void> _changePassword() async {
    final p = _profile;
    if (p == null) return;

    final currentCtrl = TextEditingController();
    final newCtrl = TextEditingController();
    final confirmCtrl = TextEditingController();

    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) {
        return AlertDialog(
          title: const Text('Đổi mật khẩu'),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                TextField(
                  controller: currentCtrl,
                  obscureText: true,
                  decoration: InputDecoration(
                    labelText: p.authProvider == 'GOOGLE'
                        ? 'Mật khẩu hiện tại (bỏ qua nếu đăng nhập Google)'
                        : 'Mật khẩu hiện tại',
                  ),
                ),
                const SizedBox(height: 12),
                TextField(
                  controller: newCtrl,
                  obscureText: true,
                  decoration: const InputDecoration(labelText: 'Mật khẩu mới'),
                ),
                const SizedBox(height: 12),
                TextField(
                  controller: confirmCtrl,
                  obscureText: true,
                  decoration:
                      const InputDecoration(labelText: 'Xác nhận mật khẩu mới'),
                ),
              ],
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(ctx).pop(false),
              child: const Text('Hủy'),
            ),
            FilledButton(
              onPressed: () => Navigator.of(ctx).pop(true),
              child: const Text('Đổi'),
            ),
          ],
        );
      },
    );

    if (ok != true) return;

    if (newCtrl.text.trim().length < 6) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Mật khẩu mới phải từ 6 ký tự.')),
      );
      return;
    }
    if (newCtrl.text != confirmCtrl.text) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Xác nhận mật khẩu không khớp.')),
      );
      return;
    }

    try {
      await _api.post(
        '/users/me/change-password',
        body: {
          'currentPassword': currentCtrl.text,
          'newPassword': newCtrl.text,
          'confirmPassword': confirmCtrl.text,
        },
      );

      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Đổi mật khẩu thành công.')));
    } on ApiException catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text(e.message)));
    } catch (_) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Không đổi được mật khẩu.')));
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

    final p = _profile;
    final w = _wallet;
    if (p == null || w == null) {
      return const Center(child: Text('Chưa có dữ liệu hồ sơ.'));
    }

    final avatarUri = (p.avatarUrl != null && p.avatarUrl!.trim().isNotEmpty)
        ? Uri.tryParse(p.avatarUrl!.trim())
        : null;

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
                  CircleAvatar(
                    radius: 22,
                    backgroundImage: (avatarUri != null)
                        ? NetworkImage(avatarUri.toString())
                        : null,
                    child:
                        (avatarUri == null) ? const Icon(Icons.person) : null,
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          p.fullName,
                          style: Theme.of(context).textTheme.titleMedium,
                        ),
                        const SizedBox(height: 2),
                        Text(
                          p.email,
                          style: Theme.of(context).textTheme.bodySmall,
                        ),
                        if ((p.phoneNumber ?? '').trim().isNotEmpty) ...[
                          const SizedBox(height: 2),
                          Text(
                            p.phoneNumber!,
                            style: Theme.of(context).textTheme.bodySmall,
                          ),
                        ],
                      ],
                    ),
                  ),
                  IconButton(
                    onPressed: _editPersonalInfo,
                    icon: const Icon(Icons.edit),
                    tooltip: 'Cập nhật thông tin',
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 12),
          Card(
            child: Padding(
              padding: const EdgeInsets.all(12),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      const Icon(Icons.directions_car),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Text(
                          'Phương tiện của tôi',
                          style: Theme.of(context).textTheme.titleMedium,
                        ),
                      ),
                      IconButton(
                        onPressed: () => _upsertVehicle(),
                        icon: const Icon(Icons.add),
                        tooltip: 'Thêm phương tiện',
                      ),
                    ],
                  ),
                  const SizedBox(height: 8),
                  if (_vehicles.isEmpty)
                    Text(
                      'Chưa có phương tiện. Bấm + để thêm.',
                      style: Theme.of(context).textTheme.bodySmall,
                    )
                  else
                    ..._vehicles.map(
                      (v) => ListTile(
                        contentPadding: EdgeInsets.zero,
                        title: Text(v.plateNumber),
                        subtitle: Text(
                          '${v.vehicleType}${(v.color ?? '').trim().isNotEmpty ? ' • ${v.color}' : ''}',
                        ),
                        trailing: Wrap(
                          spacing: 4,
                          children: [
                            IconButton(
                              onPressed: () => _upsertVehicle(existing: v),
                              icon: const Icon(Icons.edit),
                              tooltip: 'Sửa',
                            ),
                            IconButton(
                              onPressed: () => _deleteVehicle(v),
                              icon: const Icon(Icons.delete_outline),
                              tooltip: 'Xóa',
                            ),
                          ],
                        ),
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
                          '${w.walletBalance.toStringAsFixed(0)} VND',
                          style: Theme.of(context)
                              .textTheme
                              .titleLarge
                              ?.copyWith(fontWeight: FontWeight.w800),
                        ),
                        const SizedBox(height: 2),
                        Text(
                          'Gói: ${w.membershipPlan}',
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
            child: Padding(
              padding: const EdgeInsets.all(12),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      const Icon(Icons.receipt_long),
                      const SizedBox(width: 8),
                      Text(
                        'Giao dịch gần đây',
                        style: Theme.of(context).textTheme.titleMedium,
                      ),
                    ],
                  ),
                  const SizedBox(height: 8),
                  if (_transactions.isEmpty)
                    Text(
                      'Chưa có giao dịch.',
                      style: Theme.of(context).textTheme.bodySmall,
                    )
                  else
                    ..._transactions.take(5).map(
                          (t) => ListTile(
                            contentPadding: EdgeInsets.zero,
                            title: Text(t.type),
                            subtitle: Text(t.description ?? ''),
                            trailing: Text(
                              t.amount.toStringAsFixed(0),
                              style:
                                  const TextStyle(fontWeight: FontWeight.w700),
                            ),
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
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      const Icon(Icons.history),
                      const SizedBox(width: 8),
                      Text(
                        'Lịch sử hoạt động',
                        style: Theme.of(context).textTheme.titleMedium,
                      ),
                    ],
                  ),
                  const SizedBox(height: 8),
                  if (_bookings.isEmpty)
                    Text(
                      'Chưa có booking.',
                      style: Theme.of(context).textTheme.bodySmall,
                    )
                  else
                    ..._bookings.take(5).map(
                          (b) => ListTile(
                            contentPadding: EdgeInsets.zero,
                            title:
                                Text('Booking #${b.bookingId} • ${b.slotName}'),
                            subtitle: Text(
                              'Status: ${b.status}${b.vehiclePlate != null && b.vehiclePlate!.isNotEmpty ? ' • Plate: ${b.vehiclePlate}' : ''}',
                            ),
                            trailing: IconButton(
                              onPressed: (b.qrCodeBase64 != null &&
                                      b.qrCodeBase64!.isNotEmpty)
                                  ? () => _openQr(b)
                                  : null,
                              icon: const Icon(Icons.qr_code),
                              tooltip: 'Xem QR',
                            ),
                          ),
                        ),
                  const SizedBox(height: 4),
                  Text(
                    'Xem đầy đủ ở tab Booking.',
                    style: Theme.of(context).textTheme.bodySmall,
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 12),
          Card(
            child: Padding(
              padding: const EdgeInsets.all(12),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      const Icon(Icons.security),
                      const SizedBox(width: 8),
                      Text(
                        'Cài đặt & bảo mật',
                        style: Theme.of(context).textTheme.titleMedium,
                      ),
                    ],
                  ),
                  const SizedBox(height: 8),
                  SwitchListTile(
                    contentPadding: EdgeInsets.zero,
                    title: const Text('Thông báo Email'),
                    value: p.notificationEmailEnabled,
                    onChanged: (v) => _updateSettings(
                      emailEnabled: v,
                      pushEnabled: p.notificationPushEnabled,
                    ),
                  ),
                  SwitchListTile(
                    contentPadding: EdgeInsets.zero,
                    title: const Text('Thông báo Push'),
                    value: p.notificationPushEnabled,
                    onChanged: (v) => _updateSettings(
                      emailEnabled: p.notificationEmailEnabled,
                      pushEnabled: v,
                    ),
                  ),
                  const Divider(height: 1),
                  ListTile(
                    contentPadding: EdgeInsets.zero,
                    leading: const Icon(Icons.lock_outline),
                    title: const Text('Đổi mật khẩu'),
                    trailing: const Icon(Icons.chevron_right),
                    onTap: _changePassword,
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 16),
          SizedBox(
            width: double.infinity,
            child: FilledButton(
              style: FilledButton.styleFrom(
                backgroundColor: Theme.of(context).colorScheme.error,
                foregroundColor: Theme.of(context).colorScheme.onError,
              ),
              onPressed: () => widget.onLogout(),
              child: const Padding(
                padding: EdgeInsets.symmetric(vertical: 12),
                child: Text('Đăng xuất'),
              ),
            ),
          ),
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
          final bytes =
              base64Decode(_normalizeBase64(booking.qrCodeBase64 ?? ''));
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
                  color: Theme.of(context).colorScheme.error,
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
}

class _TopUpSelection {
  final String provider;
  final int amount;

  const _TopUpSelection({
    required this.provider,
    required this.amount,
  });
}
