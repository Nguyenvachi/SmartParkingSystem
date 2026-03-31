import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../../core/config/app_theme.dart';
import '../../../core/http/api_client.dart';

import 'reset_password_page.dart';

class ForgotPasswordPage extends StatefulWidget {
  const ForgotPasswordPage({super.key});

  @override
  State<ForgotPasswordPage> createState() => _ForgotPasswordPageState();
}

class _ForgotPasswordPageState extends State<ForgotPasswordPage> {
  final _api = ApiClient();
  final _emailCtrl = TextEditingController();

  bool _loading = false;
  String? _message;
  String? _demoToken;

  @override
  void dispose() {
    _emailCtrl.dispose();
    super.dispose();
  }

  void _toast(String msg) {
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(msg)));
  }

  Future<void> _submit() async {
    final email = _emailCtrl.text.trim();
    if (email.isEmpty) {
      _toast('Vui lòng nhập email.');
      return;
    }

    setState(() {
      _loading = true;
      _message = null;
      _demoToken = null;
    });

    try {
      final data = await _api.post(
        '/auth/forgot-password',
        auth: false,
        body: {'email': email},
      );

      final map = (data as Map).cast<String, dynamic>();
      setState(() {
        _message = (map['message'] as String?) ?? 'Đã gửi yêu cầu.';
        _demoToken = map['resetToken'] as String?;
      });
    } on ApiException catch (e) {
      _toast(e.message);
    } catch (_) {
      _toast('Không thể tạo yêu cầu reset mật khẩu.');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  void _goReset() {
    final email = _emailCtrl.text.trim();
    if (email.isEmpty) {
      _toast('Vui lòng nhập email.');
      return;
    }

    Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => ResetPasswordPage(
          email: email,
          demoToken: _demoToken,
        ),
      ),
    );
  }

  Future<void> _copyToken() async {
    if (_demoToken == null || _demoToken!.isEmpty) return;
    await Clipboard.setData(ClipboardData(text: _demoToken!));
    _toast('Đã copy token demo.');
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Quên mật khẩu')),
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
                        'Khôi phục tài khoản',
                        style: Theme.of(context)
                            .textTheme
                            .titleLarge
                            ?.copyWith(fontWeight: FontWeight.w800),
                      ),
                      const SizedBox(height: 6),
                      Text(
                        'Nhập email để tạo yêu cầu reset mật khẩu.',
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
                        textInputAction: TextInputAction.done,
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
                              : const Text('Tạo yêu cầu'),
                        ),
                      ),
                      if (_message != null) ...[
                        const SizedBox(height: 12),
                        Text(
                          _message!,
                          style: Theme.of(context)
                              .textTheme
                              .bodyMedium
                              ?.copyWith(fontWeight: FontWeight.w600),
                        ),
                      ],
                      if (_demoToken != null && _demoToken!.isNotEmpty) ...[
                        const SizedBox(height: 12),
                        Container(
                          width: double.infinity,
                          padding: const EdgeInsets.all(12),
                          decoration: BoxDecoration(
                            borderRadius: BorderRadius.circular(12),
                            border: Border.all(color: AppTheme.brandLine),
                          ),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              const Text(
                                'Token demo (dev)',
                                style: TextStyle(fontWeight: FontWeight.w700),
                              ),
                              const SizedBox(height: 6),
                              SelectableText(
                                _demoToken!,
                                style: const TextStyle(fontFamily: 'monospace'),
                              ),
                              const SizedBox(height: 6),
                              Row(
                                children: [
                                  TextButton.icon(
                                    onPressed: _copyToken,
                                    icon: const Icon(Icons.copy, size: 18),
                                    label: const Text('Copy'),
                                  ),
                                  const Spacer(),
                                  FilledButton(
                                    onPressed: _goReset,
                                    child: const Text('Đặt lại'),
                                  ),
                                ],
                              ),
                            ],
                          ),
                        ),
                      ] else ...[
                        const SizedBox(height: 12),
                        SizedBox(
                          width: double.infinity,
                          child: OutlinedButton(
                            onPressed: _loading ? null : _goReset,
                            child: const Text(
                                'Tôi đã có token → Đặt lại mật khẩu'),
                          ),
                        ),
                      ],
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
