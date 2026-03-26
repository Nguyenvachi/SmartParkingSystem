class ApiConfig {
  /// Override at build/run time:
  /// `--dart-define=API_BASE_URL=http://10.0.2.2:8080/api`
  static const String baseUrl = String.fromEnvironment(
    'API_BASE_URL',
    defaultValue: 'http://10.0.2.2:8080/api',
  );
}
