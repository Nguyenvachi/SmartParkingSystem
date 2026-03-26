import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

class AppTheme {
  // Synced from frontend/css/style.css (:root)
  static const Color brandInk = Color(0xFF0C2340);
  static const Color brandInkSoft = Color(0xFF314A67);
  static const Color brandAccent = Color(0xFF0F766E);
  static const Color brandAccentStrong = Color(0xFF115E59);
  static const Color brandBackground = Color(0xFFF6F7FB);
  static const Color brandLine = Color(0x1A0C2340); // rgba(12, 35, 64, 0.10)

  static const Color errorColor = Color(0xFFDC3545);
  static const Color successColor = Color(0xFF28A745);

  static ThemeData get theme {
    final textTheme = GoogleFonts.manropeTextTheme().apply(
      bodyColor: brandInk,
      displayColor: brandInk,
    );

    final heading = GoogleFonts.soraTextTheme().apply(
      bodyColor: brandInk,
      displayColor: brandInk,
    );

    final rounded16 = RoundedRectangleBorder(
      borderRadius: BorderRadius.circular(16),
      side: const BorderSide(color: brandLine),
    );

    final inputBorder = OutlineInputBorder(
      borderRadius: BorderRadius.circular(16),
      borderSide: const BorderSide(color: brandLine),
    );

    return ThemeData(
      useMaterial3: true,
      primaryColor: brandAccent,
      scaffoldBackgroundColor: brandBackground,
      colorScheme: const ColorScheme.light(
        primary: brandAccent,
        secondary: brandInkSoft,
        surface: brandBackground,
        error: errorColor,
        onPrimary: Colors.white,
        onSecondary: Colors.white,
        onSurface: brandInk,
        onError: Colors.white,
        brightness: Brightness.light,
      ),
      appBarTheme: AppBarTheme(
        backgroundColor: const Color(0xFF0F172A),
        foregroundColor: Colors.white,
        surfaceTintColor: Colors.transparent,
        elevation: 0,
        titleTextStyle: heading.titleLarge?.copyWith(
          color: Colors.white,
          fontWeight: FontWeight.w800,
        ),
        iconTheme: const IconThemeData(color: Colors.white),
      ),
      cardTheme: CardThemeData(
        color: Colors.white,
        elevation: 0,
        shape: rounded16,
      ),
      dividerTheme: const DividerThemeData(color: brandLine, thickness: 1),
      textTheme: textTheme.copyWith(
        displayLarge: heading.displayLarge,
        displayMedium: heading.displayMedium,
        displaySmall: heading.displaySmall,
        headlineLarge: heading.headlineLarge,
        headlineMedium: heading.headlineMedium,
        headlineSmall: heading.headlineSmall,
        titleLarge: heading.titleLarge,
        titleMedium: heading.titleMedium,
        titleSmall: heading.titleSmall,
        bodyMedium: textTheme.bodyMedium?.copyWith(color: brandInkSoft),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: Colors.white,
        border: inputBorder,
        enabledBorder: inputBorder,
        focusedBorder: inputBorder.copyWith(
          borderSide: const BorderSide(color: brandAccent, width: 1.4),
        ),
        errorBorder: inputBorder.copyWith(
          borderSide: const BorderSide(color: errorColor),
        ),
        focusedErrorBorder: inputBorder.copyWith(
          borderSide: const BorderSide(color: errorColor, width: 1.4),
        ),
        contentPadding:
            const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
      ),
      filledButtonTheme: FilledButtonThemeData(
        style: FilledButton.styleFrom(
          backgroundColor: brandAccent,
          foregroundColor: Colors.white,
          shape:
              RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
          padding: const EdgeInsets.symmetric(vertical: 14, horizontal: 18),
          textStyle:
              textTheme.labelLarge?.copyWith(fontWeight: FontWeight.w800),
        ),
      ),
      outlinedButtonTheme: OutlinedButtonThemeData(
        style: OutlinedButton.styleFrom(
          foregroundColor: brandInk,
          side: const BorderSide(color: brandLine),
          shape:
              RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
          padding: const EdgeInsets.symmetric(vertical: 14, horizontal: 18),
          textStyle:
              textTheme.labelLarge?.copyWith(fontWeight: FontWeight.w800),
        ),
      ),
      navigationBarTheme: NavigationBarThemeData(
        backgroundColor: Colors.white,
        indicatorColor: brandAccent.withValues(alpha: 0.12),
        labelTextStyle: WidgetStatePropertyAll(
          textTheme.labelMedium?.copyWith(fontWeight: FontWeight.w700),
        ),
        iconTheme: WidgetStateProperty.resolveWith((states) {
          final color = states.contains(WidgetState.selected)
              ? brandAccent
              : brandInkSoft;
          return IconThemeData(color: color);
        }),
      ),
    );
  }
}
