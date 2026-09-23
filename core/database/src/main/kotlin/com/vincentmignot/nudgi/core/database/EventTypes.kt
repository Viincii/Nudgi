package com.vincentmignot.nudgi.core.database

// Values of `events.event_type`. They live next to the schema so that every module writing or
// reading events shares one taxonomy, whichever of them owns the logic behind a given type.

const val EVENT_TYPE_APP_FOREGROUND = "app_foreground"
const val EVENT_TYPE_APP_BACKGROUND = "app_background"

// A nudge decision is either shown or suppressed; response and outcome refer back to it by the
// `nudge_id` in their metadata. See NudgeMetadata in core:nudge for the metadata of each type.
const val EVENT_TYPE_NUDGE_SHOWN = "nudge_shown"
const val EVENT_TYPE_NUDGE_SUPPRESSED = "nudge_suppressed"
const val EVENT_TYPE_NUDGE_RESPONSE = "nudge_response"
const val EVENT_TYPE_NUDGE_OUTCOME = "nudge_outcome"
