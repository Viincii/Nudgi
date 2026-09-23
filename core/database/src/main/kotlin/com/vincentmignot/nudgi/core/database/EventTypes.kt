package com.vincentmignot.nudgi.core.database

// Values of `events.event_type`. They live next to the schema so that every module writing or
// reading events shares one taxonomy, whichever of them owns the logic behind a given type.

const val EVENT_TYPE_APP_FOREGROUND = "app_foreground"
const val EVENT_TYPE_APP_BACKGROUND = "app_background"
