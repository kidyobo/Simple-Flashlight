package com.simplemobiletools.flashlight.activities

import android.content.Intent
import android.graphics.PorterDuff
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.SeekBar
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.LICENSE_KOTLIN
import com.simplemobiletools.commons.helpers.LICENSE_OTTO
import com.simplemobiletools.commons.helpers.PERMISSION_CAMERA
import com.simplemobiletools.flashlight.BuildConfig
import com.simplemobiletools.flashlight.R
import com.simplemobiletools.flashlight.extensions.config
import com.simplemobiletools.flashlight.helpers.BusProvider
import com.simplemobiletools.flashlight.helpers.MyCameraImpl
import com.simplemobiletools.flashlight.models.Events
import com.squareup.otto.Bus
import com.squareup.otto.Subscribe
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : SimpleActivity() {
    private val MAX_STROBO_DELAY = 2000
    private val MIN_STROBO_DELAY = 30

    private var mBus: Bus? = null
    private var mCameraImpl: MyCameraImpl? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mBus = BusProvider.instance
        changeIconColor(R.color.translucent_white, bright_display_btn)
        changeIconColor(R.color.translucent_white, stroboscope_btn)

        bright_display_btn.setOnClickListener {
            startActivity(Intent(applicationContext, BrightDisplayActivity::class.java))
        }

        toggle_btn.setOnClickListener {
            mCameraImpl!!.toggleFlashlight()
        }

        setupStroboscope()
    }

    override fun onResume() {
        super.onResume()
        mCameraImpl!!.handleCameraSetup()

        bright_display_btn.beVisibleIf(config.brightDisplay)
        stroboscope_btn.beVisibleIf(config.stroboscope)
        if (!config.stroboscope) {
            mCameraImpl!!.stopStroboscope()
            stroboscope_bar.beInvisible()
        }
        updateTextColors(main_holder)
    }

    override fun onStart() {
        super.onStart()
        mBus!!.register(this)

        if (mCameraImpl == null) {
            setupCameraImpl()
        }
    }

    override fun onStop() {
        super.onStop()
        mBus!!.unregister(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseCamera()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.settings -> launchSettings()
            R.id.about -> launchAbout()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun launchSettings() {
        startActivity(Intent(applicationContext, SettingsActivity::class.java))
    }

    private fun launchAbout() {
        startAboutActivity(R.string.app_name, LICENSE_KOTLIN or LICENSE_OTTO, BuildConfig.VERSION_NAME)
    }

    private fun setupCameraImpl() {
        mCameraImpl = MyCameraImpl.newInstance(this)
        mCameraImpl!!.enableFlashlight()
    }

    private fun setupStroboscope() {
        stroboscope_btn.setOnClickListener {
            toggleStroboscope()
        }

        stroboscope_bar.max = MAX_STROBO_DELAY - MIN_STROBO_DELAY
        stroboscope_bar.progress = stroboscope_bar.max / 2
        stroboscope_bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, b: Boolean) {
                val frequency = stroboscope_bar.max - progress + MIN_STROBO_DELAY
                mCameraImpl?.stroboFrequency = frequency
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {

            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {

            }
        })
    }

    private fun toggleStroboscope() {
        // use the old Camera API for stroboscope, the new Camera Manager is way too slow
        if (isNougatPlus()) {
            cameraPermissionGranted()
        } else {
            handlePermission(PERMISSION_CAMERA) {
                if (it) {
                    cameraPermissionGranted()
                } else {
                    toast(R.string.camera_permission)
                }
            }
        }
    }

    private fun cameraPermissionGranted() {
        if (mCameraImpl!!.toggleStroboscope()) {
            stroboscope_bar.beInvisibleIf(stroboscope_bar.visibility == View.VISIBLE)
            changeIconColor(if (stroboscope_bar.visibility == View.VISIBLE) R.color.color_primary else R.color.translucent_white, stroboscope_btn)
        }
    }

    private fun releaseCamera() {
        mCameraImpl?.releaseCamera()
        mCameraImpl = null
    }

    @Subscribe
    fun stateChangedEvent(event: Events.StateChanged) {
        if (event.isEnabled) {
            enableFlashlight()
        } else {
            disableFlashlight()
        }
    }

    private fun enableFlashlight() {
        changeIconColor(R.color.color_primary, toggle_btn)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        changeIconColor(R.color.translucent_white, stroboscope_btn)
        stroboscope_bar.beInvisible()
    }

    private fun disableFlashlight() {
        changeIconColor(R.color.translucent_white, toggle_btn)
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun changeIconColor(colorId: Int, imageView: ImageView?) {
        val appColor = resources.getColor(colorId)
        imageView!!.background.mutate().setColorFilter(appColor, PorterDuff.Mode.SRC_IN)
    }

    @Subscribe
    fun cameraUnavailable(event: Events.CameraUnavailable) {
        toast(R.string.camera_error)
        disableFlashlight()
    }
}
