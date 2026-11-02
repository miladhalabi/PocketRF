package com.pocketrf.ble.ui.theme

import androidx.compose.ui.graphics.Color

// One accent, locked across the whole app: the signal-green used in the launcher
// icon's radio-wave mark. Everything else is neutral slate, no gradients, no
// second accent competing for attention.
val SignalGreen = Color(0xFF4FD896)
val SignalGreenDark = Color(0xFF1F9A66) // same hue, darkened for light-mode contrast

val DarkBackground = Color(0xFF0B0E14)
val DarkSurface = Color(0xFF12161F)
val DarkSurfaceVariant = Color(0xFF1A202C)
val DarkOutline = Color(0xFF2B3340)
val DarkOnBackground = Color(0xFFE7EAF0)
val DarkOnSurfaceVariant = Color(0xFF9AA4B2)

val LightBackground = Color(0xFFFAFAF8)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFF0F1ED)
val LightOutline = Color(0xFFDCDFD8)
val LightOnBackground = Color(0xFF14171C)
val LightOnSurfaceVariant = Color(0xFF585F6B)

val StatusAmber = Color(0xFFD9A441) // in-progress connection states
val StatusRed = Color(0xFFE5604D) // errors / disconnected-with-problem

// Per-event-type accents for the activity log, kept desaturated so the single
// brand accent (SignalGreen) still reads as the "important" color on screen.
val EventDecoded = SignalGreen
val EventRaw = Color(0xFF7C8699)
val EventSpectrum = Color(0xFF6FA8D9)
val EventSystem = Color(0xFFB98FD9)
