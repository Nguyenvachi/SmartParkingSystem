import 'package:flutter_secure_storage/flutter_secure_storage.dart';

class TokenStorage {
  static const _tokenKey = 'sp_jwt_token';
  static const _emailKey = 'sp_email';
  static const _fullNameKey = 'sp_full_name';
  static const _roleKey = 'sp_role';

  final FlutterSecureStorage _storage;

  TokenStorage({FlutterSecureStorage? storage})
    : _storage = storage ?? const FlutterSecureStorage();

  Future<void> saveSession({
    required String token,
    required String email,
    required String fullName,
    required String role,
  }) async {
    await _storage.write(key: _tokenKey, value: token);
    await _storage.write(key: _emailKey, value: email);
    await _storage.write(key: _fullNameKey, value: fullName);
    await _storage.write(key: _roleKey, value: role);
  }

  Future<String?> readToken() => _storage.read(key: _tokenKey);

  Future<Map<String, String?>> readSession() async {
    final token = await _storage.read(key: _tokenKey);
    final email = await _storage.read(key: _emailKey);
    final fullName = await _storage.read(key: _fullNameKey);
    final role = await _storage.read(key: _roleKey);

    return {'token': token, 'email': email, 'fullName': fullName, 'role': role};
  }

  Future<void> clear() async {
    await _storage.delete(key: _tokenKey);
    await _storage.delete(key: _emailKey);
    await _storage.delete(key: _fullNameKey);
    await _storage.delete(key: _roleKey);
  }
}
