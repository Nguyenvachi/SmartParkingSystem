import 'package:flutter/material.dart';

import 'core/config/app_theme.dart';
import 'core/storage/token_storage.dart';
import 'features/auth/ui/login_page.dart';
import 'features/booking/ui/booking_page.dart';
import 'features/dashboard/ui/dashboard_page.dart';
import 'features/profile/ui/profile_page.dart';

void main() {
  runApp(const SmartParkingApp());
}

class SmartParkingApp extends StatelessWidget {
  const SmartParkingApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'SmartParking',
      theme: AppTheme.theme,
      home: const RootRouter(),
    );
  }
}

class RootRouter extends StatefulWidget {
  const RootRouter({super.key});

  @override
  State<RootRouter> createState() => _RootRouterState();
}

class _RootRouterState extends State<RootRouter> {
  final _tokenStorage = TokenStorage();

  bool _loading = true;
  bool _hasToken = false;

  int _index = 0;

  @override
  void initState() {
    super.initState();
    _bootstrap();
  }

  Future<void> _bootstrap() async {
    final token = await _tokenStorage.readToken();
    setState(() {
      _hasToken = token != null && token.isNotEmpty;
      _loading = false;
    });
  }

  void _onLoggedIn() {
    setState(() {
      _hasToken = true;
      _index = 0;
    });
  }

  Future<void> _logout() async {
    await _tokenStorage.clear();
    setState(() {
      _hasToken = false;
      _index = 0;
    });
  }

  @override
  Widget build(BuildContext context) {
    if (_loading) {
      return const Scaffold(body: Center(child: CircularProgressIndicator()));
    }

    if (!_hasToken) {
      return LoginPage(onLoggedIn: _onLoggedIn);
    }

    final pages = <Widget>[
      const DashboardPage(),
      const BookingPage(),
      ProfilePage(onLogout: _logout),
    ];

    return Scaffold(
      appBar: AppBar(
        titleSpacing: 0,
        title: Row(
          children: [
            Container(
              width: 40,
              height: 40,
              margin: const EdgeInsets.only(right: 12),
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(12),
                gradient: const LinearGradient(
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                  colors: [AppTheme.brandAccent, Color(0xFF0B8C82)],
                ),
              ),
              child: const Icon(Icons.local_parking, color: Colors.white),
            ),
            const Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text('Smart Parking'),
                  SizedBox(height: 2),
                  Text(
                    'Hệ thống quản lý & đặt chỗ',
                    style: TextStyle(
                      fontSize: 12,
                      color: Color(0xB3FFFFFF),
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
      body: pages[_index],
      bottomNavigationBar: NavigationBar(
        selectedIndex: _index,
        onDestinationSelected: (value) => setState(() => _index = value),
        destinations: const [
          NavigationDestination(icon: Icon(Icons.map), label: 'Dashboard'),
          NavigationDestination(
            icon: Icon(Icons.confirmation_number),
            label: 'Booking',
          ),
          NavigationDestination(icon: Icon(Icons.person), label: 'Profile'),
        ],
      ),
    );
  }
}
