package com.mrzgaming.ezbox

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AlphaAnimation
import android.view.animation.OvershootInterpolator
import android.view.animation.ScaleAnimation
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.EZBox_SplashTheme)
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_splash)

        val content = findViewById<android.view.View>(android.R.id.content)

        val scaleUp = ScaleAnimation(
            0.8f, 1.0f, 0.8f, 1.0f,
            ScaleAnimation.RELATIVE_TO_SELF, 0.5f,
            ScaleAnimation.RELATIVE_TO_SELF, 0.5f
        ).apply {
            duration = 600
            interpolator = OvershootInterpolator(1.2f)
        }
        content.startAnimation(scaleUp)

        val fadeIn = AlphaAnimation(0f, 1f).apply { duration = 400 }
        fadeIn.startOffset = 300
        content.startAnimation(fadeIn)

        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }, 1200)
    }
}
