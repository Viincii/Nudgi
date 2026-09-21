# 2. Mascot drawn with Compose primitives instead of SVG assets

## Context

The mascot must be light, sharp at any size and easy to animate part by part (mouth, eyes, antenna). Jetpack Compose
does not render SVG files natively. The options were an SVG-to-`ImageVector` conversion, Lottie or a similar runtime,
or drawing directly with Compose.

## Decision

Draw Nudgi in code with `Canvas`, `Path` and Compose animation APIs (`core:mascot`). A mood maps to continuous look
parameters (`MascotFace`) that are animated with springs, plus an infinite idle transition for breathing and blinking
at random intervals.

The look is deliberately minimal: a flat single-color blob, two pill-shaped eyes and a tiny antenna, with no mouth.
Expressions come from the eyes (size, tilt, height) and a light body posture (stretch and lean). The direction is
inspired by the avatars of Bible Strong Avatar Lab, which is AGPL-3.0: only the general idea was borrowed, and Nudgi is
redrawn from scratch. No code or asset from that project may be copied into this MIT-licensed repository.

## Consequences

- No asset pipeline and no extra dependency; the whole mascot is a couple hundred lines.
- Expressions blend smoothly instead of swapping between pre-made frames.
- Glance widgets and notifications cannot host a Compose `Canvas`. When those surfaces arrive, render the same drawing
  to a bitmap per mood instead of duplicating it.
