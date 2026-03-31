import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:google_sign_in/google_sign_in.dart';

import '../../../core/config/api_config.dart';
import '../../../core/http/api_client.dart';
import '../../../core/models/auth_response.dart';
import '../../../core/storage/token_storage.dart';

import '../../../core/config/app_theme.dart';

import 'forgot_password_page.dart';
import 'register_page.dart';

class LoginPage extends StatefulWidget {
  final VoidCallback onLoggedIn;

  const LoginPage({super.key, required this.onLoggedIn});

  @override
  State<LoginPage> createState() => _LoginPageState();
}

class _LoginPageState extends State<LoginPage> {
  final _api = ApiClient();
  final _tokenStorage = TokenStorage();

  late final GoogleSignIn _googleSignIn;

  final _emailCtrl = TextEditingController();
  final _passCtrl = TextEditingController();

  bool _loading = false;
  bool _showPassword = false;

  @override
  void initState() {
    super.initState();
    _googleSignIn = GoogleSignIn(
      scopes: const ['email', 'profile', 'openid'],
      serverClientId: ApiConfig.googleServerClientId.isEmpty
          ? null
          : ApiConfig.googleServerClientId,
    );
  }

  @override
  void dispose() {
    _emailCtrl.dispose();
    _passCtrl.dispose();
    super.dispose();
  }

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
      _toast('Đăng nhập thất bại.');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _loginWithGoogle() async {
    setState(() => _loading = true);
    try {
      // Optional: clear any previous session to avoid stale accounts in dev.
      await _googleSignIn.signOut();
      final account = await _googleSignIn.signIn();
      if (account == null) {
        _toast('Bạn đã hủy đăng nhập Google.');
        return;
      }

      final authData = await account.authentication;
      final idToken = authData.idToken;

      if (idToken == null || idToken.isEmpty) {
        _toast(
            'Không lấy được Google ID token. Vui lòng kiểm tra cấu hình Google Sign-In.');
        return;
      }

      final data = await _api.post(
        '/auth/login/google',
        auth: false,
        body: {'idToken': idToken},
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
    } on PlatformException catch (e) {
      final msg = e.message;
      _toast(msg == null || msg.isEmpty
          ? 'Google Sign-In thất bại (${e.code}).'
          : 'Google Sign-In thất bại (${e.code}): $msg');
    } catch (e) {
      _toast('Đăng nhập Google thất bại: ${e.toString()}');
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
                      Row(
                        children: [
                          Container(
                            width: 44,
                            height: 44,
                            decoration: BoxDecoration(
                              borderRadius: BorderRadius.circular(12),
                              gradient: const LinearGradient(
                                begin: Alignment.topLeft,
                                end: Alignment.bottomRight,
                                colors: [
                                  AppTheme.brandAccent,
                                  Color(0xFF0B8C82)
                                ],
                              ),
                            ),
                            child: const Icon(
                              Icons.local_parking,
                              color: Colors.white,
                              size: 24,
                            ),
                          ),
                          const SizedBox(width: 12),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(
                                  'Smart Parking',
                                  style: Theme.of(context)
                                      .textTheme
                                      .titleLarge
                                      ?.copyWith(fontWeight: FontWeight.w800),
                                ),
                                const SizedBox(height: 2),
                                Text(
                                  'Đăng nhập để tiếp tục',
                                  style: Theme.of(context)
                                      .textTheme
                                      .bodyMedium
                                      ?.copyWith(color: AppTheme.brandInkSoft),
                                ),
                              ],
                            ),
                          ),
                        ],
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
                        onSubmitted: (_) => _loading ? null : _login(),
                      ),
                      const SizedBox(height: 6),
                      Align(
                        alignment: Alignment.centerRight,
                        child: TextButton(
                          onPressed: _loading
                              ? null
                              : () {
                                  Navigator.of(context).push(
                                    MaterialPageRoute(
                                      builder: (_) =>
                                          const ForgotPasswordPage(),
                                    ),
                                  );
                                },
                          child: const Text('Quên mật khẩu?'),
                        ),
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
                                  child: CircularProgressIndicator(
                                    strokeWidth: 2,
                                    color: Colors.white,
                                  ),
                                )
                              : const Text('Đăng nhập'),
                        ),
                      ),
                      const SizedBox(height: 12),
                      Row(
                        children: [
                          const Expanded(child: Divider()),
                          Padding(
                            padding: const EdgeInsets.symmetric(horizontal: 10),
                            child: Text(
                              'hoặc',
                              style: Theme.of(context)
                                  .textTheme
                                  .bodySmall
                                  ?.copyWith(color: AppTheme.brandInkSoft),
                            ),
                          ),
                          const Expanded(child: Divider()),
                        ],
                      ),
                      const SizedBox(height: 12),
                      SizedBox(
                        width: double.infinity,
                        child: OutlinedButton.icon(
                          onPressed: _loading ? null : _loginWithGoogle,
                          icon: const Icon(Icons.g_mobiledata, size: 22),
                          label: const Text('Đăng nhập bằng Google'),
                        ),
                      ),
                      const SizedBox(height: 10),
                      SizedBox(
                        width: double.infinity,
                        child: TextButton(
                          onPressed: _loading
                              ? null
                              : () {
                                  Navigator.of(context).push(
                                    MaterialPageRoute(
                                      builder: (_) => RegisterPage(
                                        onLoggedIn: widget.onLoggedIn,
                                      ),
                                    ),
                                  );
                                },
                          child: const Text('Chưa có tài khoản? Đăng ký'),
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
