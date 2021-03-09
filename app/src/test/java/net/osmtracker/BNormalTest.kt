package net.osmtracker

import net.vicp.biggee.android.bOSMTracker.Core
import org.junit.Test
import java.util.*

class BNormalTest {
    @Test
    fun testDate() {
        val dec = Date(121, 11, 31)
        val dec1 = Date(121, 12, 31)
        println("$dec $dec1")
    }

    @Test
    fun testAreaRange() {
        val r = Core.AreaRang(0.0, 30.0, 10.0)
        println(r.metersPerLatitude / 3600)
        println(r.metersPerLongitude / 3600)
    }
}