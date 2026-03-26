import 'package:flutter/material.dart';

import '../../../core/http/api_client.dart';
import '../../../core/models/auth_response.dart';
import '../../../core/storage/token_storage.dart';

class LoginPage extends StatefulWidget {
  final VoidCallback onLoggedIn;

  const LoginPage({super.key, required this.onLoggedIn});

  @override
  State<LoginPage> createState() => _LoginPageState();
}

class _LoginPageState extends State<LoginPage> {
  final _api = ApiClient();
  final _tokenStorage = TokenStorage();

  final _emailCtrl = TextEditingController();
  final _passCtrl = TextEditingController();

  bool _loading = false;

  Future<void> _login() async {
    final email = _emailCtrl.text.trim();
    final password = _passCtrl.text;

    if (email.isEmpty || password.isEmpty) {
      _toast('Vui lòng nhập email và mật khẩu.');
      return;
    }

    setState(() => _loading = true);
    try {
      final data = await _api.post(
        '/auth/login',
        auth: false,
        body: {'email': email, 'password': password},
      );

      final auth = AuthResponse.fromJson((data as Map).cast<String, dynamic>());
      await _tokenStorage.saveSession(
        token: auth.token,
        email: auth.email,
        fullName: auth.fullName,
        role: auth.role,
      );

      widget.onLoggedIn();
    } on ApiException catch (e) {
      _toast(e.message);
    } catch (e) {
      _toast('Đăng nhập thất bại.');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  void _toast(String msg) {
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(msg)));
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Đăng nhập')),
      body: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          children: [
            TextField(
              controller: _emailCtrl,
              decoration: const InputDecoration(labelText: 'Email'),
              keyboardType: TextInputType.emailAddress,
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _passCtrl,
              decoration: const InputDecoration(labelText: 'Mật khẩu'),
              obscureText: true,
            ),
            const SizedBox(height: 16),
            SizedBox(
              width: double.infinity,
              child: FilledButton(
                onPressed: _loading ? null : _login,
                child: _loading
                    ? const SizedBox(
                        height: 18,
                        width: 18,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : const Text('Đăng nhập'),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
