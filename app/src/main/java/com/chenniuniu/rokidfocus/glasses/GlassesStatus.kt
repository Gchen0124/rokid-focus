package com.chenniuniu.rokidfocus.glasses

enum class GlassesStatus {
    Idle,
    MissingCompanion,
    Authorizing,
    Connecting,
    Ready,
    ViewOpen,
    Error,
    ;

    val label: String
        get() = when (this) {
            Idle -> "Glasses idle"
            MissingCompanion -> "Install Rokid AI or Hi Rokid"
            Authorizing -> "Authorizing…"
            Connecting -> "Connecting glasses…"
            Ready -> "Link ready — opening HUD"
            ViewOpen -> "HUD on glasses"
            Error -> "Glasses error"
        }
}
