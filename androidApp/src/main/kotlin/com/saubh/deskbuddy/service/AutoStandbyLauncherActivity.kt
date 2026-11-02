package com.saubh.deskbuddy.service

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.saubh.deskbuddy.MainActivity

/** Non-exported trampoline for trusted dock and notification launches of the lock-screen standby. */
class AutoStandbyLauncherActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(MainActivity.internalAutoStandbyIntent(this))
        finish()
    }
}
