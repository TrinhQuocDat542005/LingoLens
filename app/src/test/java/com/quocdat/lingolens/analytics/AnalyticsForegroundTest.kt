package com.quocdat.lingolens.analytics

import org.junit.Assert.*
import org.junit.Test

class AnalyticsForegroundTest {
    @Test fun `rotation and repeated start share source while background creates next foreground`() {
        var now=10L;val tracker=AnalyticsForegroundTracker{now++}
        val first=tracker.started();assertEquals(first,tracker.current())
        tracker.stopped(true);assertEquals(first,tracker.started())
        tracker.stopped(false);assertNull(tracker.current());val next=tracker.started()
        assertNotEquals(first.id,next.id);assertTrue(next.startedAt>first.startedAt)
    }
    @Test fun `multiple active activities only finish foreground when last stops`() {
        val tracker=AnalyticsForegroundTracker{100L};val first=tracker.started();assertEquals(first,tracker.started())
        tracker.stopped(false);assertEquals(first,tracker.current());tracker.stopped(false);assertNull(tracker.current())
    }
}
