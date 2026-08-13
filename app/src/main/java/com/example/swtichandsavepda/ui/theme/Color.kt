package com.example.swtichandsavepda.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Brand palette ported from the Switch&Save EPOS app (`switchandsavepos`).
 *
 * Values mirror the POS `values/colors.xml` "theme8" (GRADIENT) scheme, which is
 * the POS default on first install — a blue→emerald brand gradient over a cool
 * slate neutral ramp. Names are kept close to the POS resource names so the two
 * apps stay traceable to a single source of truth.
 */

// ── Brand (theme8 / GRADIENT) ────────────────────────────────────────────────
val BrandPrimary = Color(0xFF2563EB)      // theme8_primary
val BrandPrimaryDark = Color(0xFF1D4ED8)  // theme8_primary_dark
val BrandPrimaryLight = Color(0xFFEEF6FF) // theme8_primary_light
val BrandAccent = Color(0xFF10B981)       // theme8_accent (emerald)
val BrandAccentDark = Color(0xFF047857)

// ── Neutral / slate ramp ─────────────────────────────────────────────────────
val Slate900 = Color(0xFF0F172A) // dashboard_text_heading, dialog_text_primary
val Slate700 = Color(0xFF334155) // login welcome subtitle
val Slate500 = Color(0xFF64748B) // dashboard_text_secondary
val Slate400 = Color(0xFF94A3B8) // dashboard_text_tertiary
val Slate200 = Color(0xFFE2E8F0) // dashboard_status_pill_stroke
val Slate100 = Color(0xFFF1F5F9) // dashboard_tile_hint_bg
val Slate50 = Color(0xFFF8FAFC)  // dashboard_status_pill_bg

// ── Surfaces ─────────────────────────────────────────────────────────────────
val CanvasLight = Color(0xFFF8FBFF)    // dashboard_page_bg / settings_page_bg
val SurfaceLight = Color(0xFFFFFFFF)   // dashboard_tile_bg
val StrokeCard = Color(0xFFD9E4F0)     // settings_card_stroke
val StrokeControl = Color(0xFFC9D7E6)  // settings_control_stroke
val StrokeSoft = Color(0xFFE5EDF7)     // login contact table dividers

// ── Login brand panel gradient (login_brand_panel_bg) ────────────────────────
val BrandPanelTop = Color(0xFFEAF7FF)
val BrandPanelMid = Color(0xFFEEF8FF)
val BrandPanelBottom = Color(0xFFF7FCFF)

// ── Status ───────────────────────────────────────────────────────────────────
val StatusSuccess = Color(0xFF16A34A) // checkout_success
val StatusWarning = Color(0xFFEA580C) // checkout_warning
val StatusDanger = Color(0xFFDC2626)  // dialog_danger
val DangerBg = Color(0xFFFEF2F2)      // settings_danger_bg
val DangerStroke = Color(0xFFFECACA)  // settings_danger_stroke
val DangerText = Color(0xFFB91C1C)    // settings_danger_text
val SuccessBg = Color(0xFFF0FDF4)     // delivery_flow_validation_bg
val SuccessStroke = Color(0xFFB7E4C7) // delivery_flow_validation_stroke

// ── Pastel icon wells + glyph tints (POS dash_sq_* / dash_icon_*) ────────────
val WellBlue = Color(0xFFE8F4FD)
val WellBlueFg = Color(0xFF1565C0)
val WellGreen = Color(0xFFE8F5E9)
val WellGreenFg = Color(0xFF2E7D32)
val WellOrange = Color(0xFFFFF4E6)
val WellOrangeFg = Color(0xFFE65100)
val WellTeal = Color(0xFFE6F7F5)
val WellTealFg = Color(0xFF00695C)
val WellRose = Color(0xFFFCE7EF)
val WellRoseFg = Color(0xFFC2185B)

// ── Tinted status capsules (POS dash_tag_*) ──────────────────────────────────
val TagDangerBg = Color(0xFFFFEBEE)
val TagDangerFg = Color(0xFFC62828)
val TagWarningBg = Color(0xFFFFE0B2)
val TagWarningFg = Color(0xFFE65100)
val TagSuccessBg = Color(0xFFDCFCE7)
val TagSuccessFg = Color(0xFF166534)
val TagNeutralBg = Color(0xFFF1F5F9)
val TagNeutralFg = Color(0xFF475569)

// ── Dark shell (dashboard_dark_* in POS) ─────────────────────────────────────
val DarkBg = Color(0xFF12151C)
val DarkSurface = Color(0xFF1E232D)
val DarkSurfaceElevated = Color(0xFF252B36)
val DarkTextPrimary = Color(0xFFF1F5F9)
val DarkTextMuted = Color(0xFF94A3B8)
val DarkDivider = Color(0xFF2D3544)
