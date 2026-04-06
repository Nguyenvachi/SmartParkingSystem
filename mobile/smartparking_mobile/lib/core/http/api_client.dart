import 'dart:convert';

import 'package:http/http.dart' as http;

import '../config/api_config.dart';
import '../storage/token_storage.dart';

class ApiException implements Exception {
  final String message;
  ApiException(this.message);

  @override
  String toString() => message;
}

class ApiClient {
  final TokenStorage _tokenStorage;
  final http.Client _http;

  ApiClient({TokenStorage? tokenStorage, http.Client? httpClient})
      : _tokenStorage = tokenStorage ?? TokenStorage(),
        _http = httpClient ?? http.Client();

  Uri _uri(String path) {
    final base = ApiConfig.baseUrl;
    final normalizedBase =
        base.endsWith('/') ? base.substring(0, base.length - 1) : base;
    final normalizedPath = path.startsWith('/') ? path : '/$path';
    return Uri.parse('$normalizedBase$normalizedPath');
  }

  Future<Map<String, String>> _headers({bool auth = true}) async {
    final headers = <String, String>{'Content-Type': 'application/json'};

    if (auth) {
      final token = await _tokenStorage.readToken();
      if (token != null && token.isNotEmpty) {
        headers['Authorization'] = 'Bearer $token';
      }
    }

    return headers;
  }

  Future<dynamic> get(String path, {bool auth = true}) async {
    final res = await _http.get(
      _uri(path),
      headers: await _headers(auth: auth),
    );
    return _handle(res);
  }

  Future<dynamic> post(String path, {Object? body, bool auth = true}) async {
    final res = await _http.post(
      _uri(path),
      headers: await _headers(auth: auth),
      body: jsonEncode(body ?? {}),
    );
    return _handle(res);
  }

  Future<dynamic> put(String path, {Object? body, bool auth = true}) async {
    final res = await _http.put(
      _uri(path),
      headers: await _headers(auth: auth),
      body: jsonEncode(body ?? {}),
    );
    return _handle(res);
  }

  Future<dynamic> delete(String path, {bool auth = true}) async {
    final res = await _http.delete(
      _uri(path),
      headers: await _headers(auth: auth),
    );
    return _handle(res);
  }

  dynamic _handle(http.Response res) {
    dynamic data;
    try {
      data = jsonDecode(res.body);
    } catch (_) {
      data = null;
    }

    if (res.statusCode >= 200 && res.statusCode < 300) {
      return data;
    }

    final message = (data is Map && data['message'] is String)
        ? (data['message'] as String)
        : 'HTTP ${res.statusCode}';

    throw ApiException(message);
  }
}
