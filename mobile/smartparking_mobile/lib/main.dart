import 'package:flutter/material.dart';

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
      theme: ThemeData(useMaterial3: true),
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
      const ProfilePage(),
    ];

    return Scaffold(
      appBar: AppBar(
        title: const Text('SmartParking'),
        actions: [
          IconButton(onPressed: _logout, icon: const Icon(Icons.logout)),
        ],
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
