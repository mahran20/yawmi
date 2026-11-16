package app.yawmi

import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions

/**
 * "Photograph your sink" instead of scanning a code (experimental).
 *
 * Runs entirely on the phone: the camera preview plus Google ML Kit's bundled image labeler,
 * which names what it sees ("Sink", "Tap", "Tile"...). No photo is stored or sent anywhere;
 * only the label words are kept. Setup takes two photos of the same spot and keeps the labels
 * both share; the alarm then stops for a photo that shows enough of those same labels.
 */
class PhotoCheck(private val activity: AppCompatActivity, private val preview: PreviewView) {
    private var capture: ImageCapture? = null
    private val labeler = ImageLabeling.getClient(
        ImageLabelerOptions.Builder().setConfidenceThreshold(MIN_CONFIDENCE).build()
    )

    fun start(onError: () -> Unit) {
        val future = ProcessCameraProvider.getInstance(activity)
        future.addListener({
            try {
                val provider = future.get()
                val pv = Preview.Builder().build().also { it.setSurfaceProvider(preview.surfaceProvider) }
                val ic = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
                provider.unbindAll()
                provider.bindToLifecycle(activity, CameraSelector.DEFAULT_BACK_CAMERA, pv, ic)
                capture = ic
            } catch (e: Exception) {
                onError()
            }
        }, ContextCompat.getMainExecutor(activity))
    }

    /** Takes a photo and returns what's in it, most confident first; null if it failed. */
    fun shoot(onLabels: (List<String>?) -> Unit) {
        val ic = capture ?: return onLabels(null)
        ic.takePicture(ContextCompat.getMainExecutor(activity), object : ImageCapture.OnImageCapturedCallback() {
            @OptIn(ExperimentalGetImage::class)
            override fun onCaptureSuccess(image: ImageProxy) {
                val media = image.image
                if (media == null) {
                    image.close()
                    onLabels(null)
                    return
                }
                labeler.process(InputImage.fromMediaImage(media, image.imageInfo.rotationDegrees))
                    .addOnSuccessListener { found ->
                        onLabels(found.sortedByDescending { it.confidence }.map { it.text }.filter { it !in GENERIC }.take(10))
                    }
                    .addOnFailureListener { onLabels(null) }
                    .addOnCompleteListener { image.close() }
            }

            override fun onError(exception: ImageCaptureException) {
                onLabels(null)
            }
        })
    }

    fun close() {
        labeler.close()
    }

    companion object {
        private const val MIN_CONFIDENCE = 0.55f

        /** Words that fit almost any room, so they can't tell the bathroom from the bedroom. */
        private val GENERIC = setOf("Room", "Interior design", "Darkness", "Light", "Pattern")

        /** The fewest labels a reference needs to be worth trusting. */
        const val MIN_REFERENCE = 2

        /** Labels both setup photos agree on. */
        fun reference(first: List<String>, second: List<String>): List<String> = first.filter { it in second }

        /** A photo passes if it shows at least 2 of the reference labels (3 when the reference has 4 or more). */
        fun matches(reference: List<String>, photo: List<String>): Boolean {
            if (reference.size < MIN_REFERENCE) return false
            val need = if (reference.size >= 4) 3 else 2
            return photo.count { it in reference } >= need
        }
    }
}
