package com.app.chat.feature.chat.ui

import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.app.chat.R

class ImageViewerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_IMAGE_BASE64 = "extra_image_base64"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Pantalla completa, fondo negro
        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        setContentView(R.layout.activity_image_viewer)

        val iv = findViewById<ImageView>(R.id.ivFull)
        val btnClose = findViewById<ImageButton>(R.id.btnClose)

        val b64 = intent.getStringExtra(EXTRA_IMAGE_BASE64).orEmpty()
        if (b64.isNotEmpty()) {
            try {
                val bytes = Base64.decode(b64, Base64.DEFAULT)
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                iv.setImageBitmap(bmp)
            } catch (_: Exception) {
                // No crash; deja vacío si falla
            }
        }

        // Cerrar con el botón o tocando la imagen
        btnClose.setOnClickListener { finish() }
        iv.setOnClickListener { finish() }

        // Esconde system UI (gesto atrás sigue funcionando)
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    }
}
