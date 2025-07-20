package com.hamham.gpsmover.helpers

import android.view.HapticFeedbackConstants
import android.view.View

fun View.performHapticClick() {
    this.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
} 