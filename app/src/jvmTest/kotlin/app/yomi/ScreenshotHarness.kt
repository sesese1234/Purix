package app.yomi

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import app.yomi.designsystem.theme.YomiTheme
import app.yomi.model.AppLanguage
import app.yomi.model.AppearanceSettings
import org.jetbrains.skia.EncodedImageFormat
import java.io.File

/**
 * Renders composables straight to PNG without a window server.
 *
 * Compose Desktop can rasterise a scene off-screen, which makes it possible to
 * review every screen — light, dark and right-to-left — as part of the build
 * rather than by launching the app and squinting at it.
 */
object ScreenshotHarness {

    val outputDirectory: File = File("build/screenshots").apply { mkdirs() }

    fun render(
        name: String,
        width: Int = 1180,
        height: Int = 900,
        density: Float = 1f,
        appearance: AppearanceSettings = AppearanceSettings(),
        language: AppLanguage = AppLanguage.English,
        dark: Boolean = false,
        content: @Composable () -> Unit
    ): File {
        val scene = ImageComposeScene(
            width = width,
            height = height,
            density = Density(density)
        ) {
            YomiTheme(
                appearance = appearance,
                language = language,
                systemIsHebrew = language == AppLanguage.Hebrew,
                systemInDarkTheme = dark
            ) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    content()
                }
            }
        }

        return try {
            // Two frames: the first lays out, the second settles any animation
            // that starts from a non-final value.
            scene.render()
            val image = scene.render()
            val data = image.encodeToData(EncodedImageFormat.PNG)
                ?: error("Could not encode $name")
            val file = File(outputDirectory, "$name.png")
            file.writeBytes(data.bytes)
            file
        } finally {
            scene.close()
        }
    }
}
