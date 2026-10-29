package com.xcluice.jetstrike.engine

import android.opengl.GLES20
import android.util.Log

/**
 * GL context bookkeeping. [epoch] bumps whenever Android gives us a fresh EGL context (first start, or after the app
 * was backgrounded), so every GPU object (meshes, programs) knows it must re-upload itself.
 */
object GL {
    @JvmField var epoch = 0
    const val TAG = "JS3D"

    fun check(tag: String) {
        val e = GLES20.glGetError()
        if (e != GLES20.GL_NO_ERROR) Log.e(TAG, "$tag: GL error 0x${Integer.toHexString(e)}")
    }
}
