import 'package:flutter/material.dart';

import '../../../core/config/app_theme.dart';
import '../../../core/http/api_client.dart';
import '../../../core/models/auth_response.dart';
import '../../../core/storage/token_storage.dart';

class RegisterPage extends StatefulWidget {
  final VoidCallback onLoggedIn;

  const RegisterPage({super.key, required this.onLoggedIn});

  @override
  State<RegisterPage> createState() => _RegisterPageState();
}

class _RegisterPageState extends State<RegisterPage> {
  final _api = ApiClient();
  final _tokenStorage = TokenStorage();

  final _nameCtrl = TextEditingController();
  final _emailCtrl = TextEditingController();
  final _passCtrl = TextEditingController();

  bool _loading = false;
  bool _showPassword = false;

  @override
  void dispose() {
    _nameCtrl.dispose();
    _emailCtrl.dispose();
    _passCtrl.dispose();
    super.dispose();
  }

  void _toast(String msg) {
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(msg)));
  }

  Future<void> _register() async {
    final fullName = _nameCtrl.text.trim();
    final email = _emailCtrl.text.trim();
    final password = _passCtrl.text;

    if (fullName.isEmpty || email.isEmpty || password.isEmpty) {
      _toast('Vui lòng nhập đầy đủ họ tên, email và mật khẩu.');
      return;
    }

    setState(() => _loading = true);
    try {
      final data = await _api.post(
        '/auth/register',
        auth: false,
        body: {'fullName': fullName, 'email': email, 'password': password},
      );

      final auth = AuthResponse.fromJson((data as Map).cast<String, dynamic>());
      if (auth.role != 'ROLE_USER') {
        _toast('App mobile chỉ dành cho khách (ROLE_USER).');
        return;
      }

      await _tokenStorage.saveSession(
        token: auth.token,
        email: auth.email,
        fullName: auth.fullName,
        role: auth.role,
      );

      widget.onLoggedIn();
    } on ApiException catch (e) {
      _toast(e.message);
    } catch (_) {
      _toast('Đăng ký thất bại.');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Tạo tài khoản')),
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
                        'Đăng ký Smart Parking',
                        style: Theme.of(context)
                            .textTheme
                            .titleLarge
                            ?.copyWith(fontWeight: FontWeight.w800),
                      ),
                      const SizedBox(height: 6),
                      Text(
                        'Dành cho khách (ROLE_USER).',
                        style: Theme.of(context)
                            .textTheme
                            .bodyMedium
                            ?.copyWith(color: AppTheme.brandInkSoft),
                      ),
                      const SizedBox(height: 18),
                      TextField(
                        controller: _nameCtrl,
                        decoration: const InputDecoration(labelText: 'Họ tên'),
                        textInputAction: TextInputAction.next,
                      ),
                      const SizedBox(height: 12),
                      TextField(
                        controller: _emailCtrl,
                        decoration: const InputDecoration(labelText: 'Email'),
                        keyboardType: TextInputType.emailAddress,
                        textInputAction: TextInputAction.next,
                      ),
                      const SizedBox(height: 12),
                      TextField(
                        controller: _passCtrl,
                        decoration: InputDecoration(
                          labelText: 'Mật khẩu',
                          suffixIcon: IconButton(
                            onPressed: () =>
                                setState(() => _showPassword = !_showPassword),
                            icon: Icon(
                              _showPassword
                                  ? Icons.visibility_off
                                  : Icons.visibility,
                            ),
                          ),
                        ),
                        obscureText: !_showPassword,
                        onSubmitted: (_) => _loading ? null : _register(),
                      ),
                      const SizedBox(height: 16),
                      SizedBox(
                        width: double.infinity,
                        child: FilledButton(
                          onPressed: _loading ? null : _register,
                          child: _loading
                              ? const SizedBox(
                                  height: 18,
                                  width: 18,
                                  child: CircularProgressIndicator(
                                    strokeWidth: 2,
                                    color: Colors.white,
                                  ),
                                )
                              : const Text('Tạo tài khoản'),
                        ),
                      ),
                      const SizedBox(height: 10),
                      SizedBox(
                        width: double.infinity,
                        child: TextButton(
                          onPressed:
                              _loading ? null : () => Navigator.pop(context),
                          child: const Text('Đã có tài khoản? Đăng nhập'),
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
