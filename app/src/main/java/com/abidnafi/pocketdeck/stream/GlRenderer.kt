package com.abidnafi.pocketdeck.stream

import android.content.Context
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20
import android.os.Build
import com.limelight.preferences.GlPreferences

/**
 * The GPU name (GL_RENDERER). The Moonlight decoder uses it for device-specific decoder tweaks.
 * Read once per system build from an off-screen EGL context, then cached (same cache as upstream).
 */
object GlRenderer {
    fun get(context: Context): String {
        val prefs = GlPreferences.readPreferences(context)
        if (prefs.savedFingerprint == Build.FINGERPRINT && !prefs.glRenderer.isNullOrEmpty()) return prefs.glRenderer
        val renderer = runCatching { probe() }.getOrNull().orEmpty()
        if (renderer.isNotEmpty()) {
            prefs.glRenderer = renderer
            prefs.savedFingerprint = Build.FINGERPRINT
            prefs.writePreferences()
        }
        return renderer
    }

    private fun probe(): String? {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (display == EGL14.EGL_NO_DISPLAY) return null
        val version = IntArray(2)
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) return null
        try {
            val attribs = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_NONE,
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val count = IntArray(1)
            if (!EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, count, 0) || count[0] == 0) return null
            val config = configs[0]
            val ctx = EGL14.eglCreateContext(
                display, config, EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0,
            )
            val surface = EGL14.eglCreatePbufferSurface(display, config, intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0)
            try {
                if (!EGL14.eglMakeCurrent(display, surface, surface, ctx)) return null
                return GLES20.glGetString(GLES20.GL_RENDERER)
            } finally {
                EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                EGL14.eglDestroySurface(display, surface)
                EGL14.eglDestroyContext(display, ctx)
            }
        } finally {
            EGL14.eglTerminate(display)
        }
    }
}
