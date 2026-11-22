package dev.lolokeksu.legioncontrol

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class AudioSignalGeneratorTest {
    @Test fun allSupportedFrequenciesAndDurationsAreBounded() {
        for(hz in AudioSignalGenerator.frequencies) {
            val data=AudioSignalGenerator.generate("TONE","BOTH",hz,750)
            assertEquals(AudioSignalGenerator.frames(750)*2,data.size)
            assertTrue(data.any { it!=0.toShort() })
            assertTrue(data.maxOf { abs(it.toInt()) } <=
                (Short.MAX_VALUE*AudioSignalGenerator.AMPLITUDE).toInt()+1)
        }
        assertFalse(AudioSignalGenerator.isValid("TONE","LEFT",5000,1500))
        assertFalse(AudioSignalGenerator.isValid("TONE","ALTERNATING",440,1500))
        assertFalse(AudioSignalGenerator.isValid("CHANNEL_ID","BOTH",440,1500))
        assertFalse(AudioSignalGenerator.isValid("TONE","LEFT",440,5000))
    }

    @Test fun rightAndLeftChannelAreExactlyIsolated() {
        val left=AudioSignalGenerator.generate("TONE","LEFT",440,750)
        val right=AudioSignalGenerator.generate("TONE","RIGHT",440,750)
        assertTrue((left.indices step 2).any { left[it]!=0.toShort() })
        assertTrue((left.indices step 2).all { left[it+1]==0.toShort() })
        assertTrue((right.indices step 2).all { right[it]==0.toShort() })
        assertTrue((right.indices step 2).any { right[it+1]!=0.toShort() })
    }

    @Test fun stereoSignalIsIdenticalOnBothChannels() {
        val data=AudioSignalGenerator.generate("TONE","BOTH",880,750)
        for(i in 0 until data.size/2) assertEquals(data[i*2],data[i*2+1])
        assertEquals(0,data.first().toInt())
        assertEquals(0,data[data.lastIndex].toInt())
    }

    @Test fun alternatingSignalSwitchesChannelsHalfwayAndFades() {
        val pcm=AudioSignalGenerator.generate("CHANNEL_ID","ALTERNATING",440,3000)
        val halfway=pcm.size/4
        for(i in 0 until pcm.size/2) {
            if(i<halfway) assertEquals(0,pcm[i*2+1].toInt())
            else assertEquals(0,pcm[i*2].toInt())
        }
        assertTrue((0 until halfway).any { pcm[it*2]!=0.toShort() })
        assertTrue((halfway until pcm.size/2).any { pcm[it*2+1]!=0.toShort() })
        assertEquals(0,pcm[halfway*2].toInt())
        assertEquals(0,pcm[halfway*2+1].toInt())
    }

    @Test fun sweepIsDifferentFromSteadyToneAndHasSmoothEnds() {
        val sweep=AudioSignalGenerator.generate("SWEEP","BOTH",440,3000)
        val tone=AudioSignalGenerator.generate("TONE","BOTH",440,3000)
        assertFalse(sweep.contentEquals(tone))
        assertEquals(0,sweep[0].toInt())
        assertEquals(0,sweep[1].toInt())
        assertEquals(0,sweep[sweep.lastIndex].toInt())
        assertEquals(0,sweep[sweep.lastIndex-1].toInt())
    }

    @Test fun invalidRequestsAreRejectedBeforeAllocatingPlaybackBuffers() {
        assertThrows(IllegalArgumentException::class.java) {
            AudioSignalGenerator.generate("CHANNEL_ID","BOTH",440,1500)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AudioSignalGenerator.generate("TONE","LEFT",440,3001)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AudioSignalGenerator.generate("TONE","LEFT",440,1000,200_000)
        }
    }

    @Test fun generatedAmplitudeAlwaysStaysLow() {
        val pcm=AudioSignalGenerator.generate("SWEEP","BOTH",440,3000)
        assertTrue(pcm.maxOf { abs(it.toInt()) } < 4_000)
        assertTrue(AudioSignalGenerator.AMPLITUDE<=0.12)
    }
}
