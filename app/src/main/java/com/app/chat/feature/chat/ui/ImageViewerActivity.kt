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

/**
 * Activity de visor de imágenes a pantalla completa.
 *
 * Abre una imagen enviada por chat decodificando una cadena Base64.
 * - Modo inmersivo con fondo negro.
 * - Cierra al tocar el botón de cerrar o la propia imagen.
 */
class ImageViewerActivity : AppCompatActivity() {

    companion object {
        /** Clave del Intent para recibir la imagen como Base64. */
        const val EXTRA_IMAGE_BASE64 = "extra_image_base64"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Habilita dibujo bajo barras del sistema (layout sin límites) para efecto full-screen.
        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        setContentView(R.layout.activity_image_viewer)

        // Referencias a vistas del layout del visor
        val iv = findViewById<ImageView>(R.id.ivFull)
        val btnClose = findViewById<ImageButton>(R.id.btnClose)

        // Lee la cadena Base64 del Intent
        val b64 = intent.getStringExtra(EXTRA_IMAGE_BASE64).orEmpty()
        if (b64.isNotEmpty()) {
            // Decodifica Base64 -> bytes -> Bitmap y la muestra.
            // Si algo falla, se ignora silenciosamente para no crashear.
            try {
                val bytes = Base64.decode(b64, Base64.DEFAULT)
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                iv.setImageBitmap(bmp)
            } catch (_: Exception) {
                // Dejar sin imagen en caso de error de decodificación
            }
        }

        // Cierre del visor: tanto con botón dedicado como tocando la imagen
        btnClose.setOnClickListener { finish() }
        iv.setOnClickListener { finish() }

        // Activa modo inmersivo para ocultar la UI del sistema (la navegación por gestos sigue activa)
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    }
}
