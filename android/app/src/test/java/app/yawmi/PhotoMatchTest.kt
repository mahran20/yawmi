package app.yawmi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoMatchTest {
    @Test fun referenceKeepsWhatBothPhotosShow() {
        assertEquals(listOf("Sink", "Tap"), PhotoCheck.reference(listOf("Sink", "Tap", "Mirror"), listOf("Tap", "Sink", "Towel")))
    }

    @Test fun needsTwoMatchesForASmallReference() {
        val ref = listOf("Sink", "Tap", "Tile")
        assertTrue(PhotoCheck.matches(ref, listOf("Tap", "Sink", "Hand")))
        assertFalse(PhotoCheck.matches(ref, listOf("Tile", "Bed", "Pillow")))
    }

    @Test fun needsThreeMatchesForALargeReference() {
        val ref = listOf("Sink", "Tap", "Tile", "Mirror", "Plumbing fixture")
        assertFalse(PhotoCheck.matches(ref, listOf("Sink", "Tap", "Bed")))
        assertTrue(PhotoCheck.matches(ref, listOf("Sink", "Tap", "Mirror")))
    }

    @Test fun aWeakReferenceNeverPasses() {
        assertFalse(PhotoCheck.matches(listOf("Sink"), listOf("Sink")))
        assertFalse(PhotoCheck.matches(emptyList(), listOf("Sink", "Tap")))
    }
}
