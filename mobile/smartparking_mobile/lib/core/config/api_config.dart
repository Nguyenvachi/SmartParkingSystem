class ApiConfig {
  /// Override at build/run time:
  /// - Production (default): `https://smartparking.id.vn/api`
  /// - Local Android emulator example: `--dart-define=API_BASE_URL=http://10.0.2.2:8080/api`
  static const String baseUrl = String.fromEnvironment(
    'API_BASE_URL',
    defaultValue: 'https://smartparking.id.vn/api',
  );

  /// Google OAuth2 Web Client ID used as server audience when exchanging ID token to backend.
  ///
  /// Override at build/run time:
  /// `--dart-define=GOOGLE_SERVER_CLIENT_ID=<web-client-id>.apps.googleusercontent.com`
  static const String googleServerClientId = String.fromEnvironment(
    'GOOGLE_SERVER_CLIENT_ID',
    // NOTE: This must be the **Web** OAuth client ID (server), not the Android client ID.
    defaultValue:
        '304407612783-r665qoimgstfgfajipjq6lu1ir0u395v.apps.googleusercontent.com',
  );
}
