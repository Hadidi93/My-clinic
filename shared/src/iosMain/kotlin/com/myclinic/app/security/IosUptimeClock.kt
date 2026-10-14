package com.myclinic.app.security

import kotlinx.cinterop.ExperimentalForeignApi
import platform.posix.CLOCK_MONOTONIC
import platform.posix.clock_gettime_nsec_np

/** On iPhone, CLOCK_MONOTONIC keeps counting while the phone sleeps. */
@OptIn(ExperimentalForeignApi::class)
val IosUptimeClock = UptimeClock { (clock_gettime_nsec_np(CLOCK_MONOTONIC.toUInt()) / 1_000_000u).toLong() }
