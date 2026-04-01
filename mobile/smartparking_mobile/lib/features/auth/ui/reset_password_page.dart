import 'package:flutter/material.dart';

import '../../../core/config/app_theme.dart';
import '../../../core/http/api_client.dart';

class ResetPasswordPage extends StatefulWidget {
  final String email;
  final String? demoToken;

  const ResetPasswordPage({
    super.key,
    required this.email,
    this.demoToken,
  });

  @override
  State<ResetPasswordPage> createState() => _ResetPasswordPageState();
}

class _ResetPasswordPageState extends State<ResetPasswordPage> {
  final _api = ApiClient();

  late final TextEditingController _emailCtrl;
  final _tokenCtrl = TextEditingController();
  final _newPassCtrl = TextEditingController();
  final _confirmCtrl = TextEditingController();

  bool _loading = false;
  bool _showNew = false;
  bool _showConfirm = false;

  @override
  void initState() {
    super.initState();
    _emailCtrl = TextEditingController(text: widget.email);
    if (widget.demoToken != null && widget.demoToken!.isNotEmpty) {
      _tokenCtrl.text = widget.demoToken!;
    }
  }

  @override
  void dispose() {
    _emailCtrl.dispose();
    _tokenCtrl.dispose();
    _newPassCtrl.dispose();
    _confirmCtrl.dispose();
    super.dispose();
  }

  void _toast(String msg) {
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(msg)));
  }

  Future<void> _submit() async {
    final email = _emailCtrl.text.trim();
    final token = _tokenCtrl.text.trim();
    final newPassword = _newPassCtrl.text;
    final confirmPassword = _confirmCtrl.text;

    if (email.isEmpty ||
        token.isEmpty ||
        newPassword.isEmpty ||
        confirmPassword.isEmpty) {
      _toast('Vui lòng nhập đầy đủ thông tin.');
      return;
    }

    if (newPassword != confirmPassword) {
      _toast('Mật khẩu xác nhận không khớp.');
      return;
    }

    setState(() => _loading = true);
    try {
      final data = await _api.post(
        '/auth/reset-password',
        auth: false,
        body: {
          'email': email,
          'token': token,
          'newPassword': newPassword,
          'confirmPassword': confirmPassword,
        },
      );

      final map = (data as Map).cast<String, dynamic>();
      final message =
          (map['message'] as String?) ?? 'Đặt lại mật khẩu thành công.';
      _toast(message);

      if (mounted) {
        Navigator.of(context).popUntil((route) => route.isFirst);
      }
    } on ApiException catch (e) {
      _toast(e.message);
    } catch (_) {
      _toast('Đặt lại mật khẩu thất bại.');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Đặt lại mật khẩu')),
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(16),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 420),
              child: Card(
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(20, 20, 20, 18),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'Tạo mật khẩu mới',
                        style: Theme.of(context)
                            .textTheme
                            .titleLarge
                            ?.copyWith(fontWeight: FontWeight.w800),
                      ),
                      const SizedBox(height: 6),
                      Text(
                        'Nhập token và mật khẩu mới để hoàn tất.',
                        style: Theme.of(context)
                            .textTheme
                            .bodyMedium
                            ?.copyWith(color: AppTheme.brandInkSoft),
                      ),
                      const SizedBox(height: 18),
                      TextField(
                        controller: _emailCtrl,
                        decoration: const InputDecoration(labelText: 'Email'),
                        keyboardType: TextInputType.emailAddress,
                        textInputAction: TextInputAction.next,
                      ),
                      const SizedBox(height: 12),
                      TextField(
                        controller: _tokenCtrl,
                        decoration: const InputDecoration(labelText: 'Token'),
                        textInputAction: TextInputAction.next,
                      ),
                      const SizedBox(height: 12),
                      TextField(
                        controller: _newPassCtrl,
                        decoration: InputDecoration(
                          labelText: 'Mật khẩu mới',
                          suffixIcon: IconButton(
                            onPressed: () =>
                                setState(() => _showNew = !_showNew),
                            icon: Icon(_showNew
                                ? Icons.visibility_off
                                : Icons.visibility),
                          ),
                        ),
                        obscureText: !_showNew,
                        textInputAction: TextInputAction.next,
                      ),
                      const SizedBox(height: 12),
                      TextField(
                        controller: _confirmCtrl,
                        decoration: InputDecoration(
                          labelText: 'Xác nhận mật khẩu',
                          suffixIcon: IconButton(
                            onPressed: () =>
                                setState(() => _showConfirm = !_showConfirm),
                            icon: Icon(
                              _showConfirm
                                  ? Icons.visibility_off
                                  : Icons.visibility,
                            ),
                          ),
                        ),
                        obscureText: !_showConfirm,
                        onSubmitted: (_) => _loading ? null : _submit(),
                      ),
                      const SizedBox(height: 16),
                      SizedBox(
                        width: double.infinity,
                        child: FilledButton(
                          onPressed: _loading ? null : _submit,
                          child: _loading
                              ? const SizedBox(
                                  height: 18,
                                  width: 18,
                                  child: CircularProgressIndicator(
                                    strokeWidth: 2,
                                    color: Colors.white,
                                  ),
                                )
                              : const Text('Xác nhận'),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
