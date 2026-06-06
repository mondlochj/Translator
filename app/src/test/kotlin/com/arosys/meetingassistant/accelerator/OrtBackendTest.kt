package com.arosys.meetingassistant.accelerator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class OrtBackendTest {

    @Test
    fun `display names are distinct`() {
        val names = ALL_BACKENDS.map { it.displayName }
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun `short names are distinct`() {
        val names = ALL_BACKENDS.map { it.shortName }
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun `Nnapi equality respects flags`() {
        val a = OrtBackend.Nnapi(NnapiFlags.USE_FP16)
        val b = OrtBackend.Nnapi(NnapiFlags.USE_FP16)
        val c = OrtBackend.Nnapi(NnapiFlags.NONE)
        assertEquals(a, b)
        assertNotEquals(a, c)
    }

    @Test
    fun `CPU_DISABLED flag can be applied without USE_FP16`() {
        val flags = NnapiFlags.CPU_DISABLED
        assertEquals(4, flags)
    }

    @Test
    fun `DEFAULT flags include USE_FP16`() {
        assertEquals(NnapiFlags.USE_FP16, NnapiFlags.DEFAULT)
    }

    @Test
    fun `ALL_BACKENDS contains CPU, XNNPACK, and NNAPI`() {
        assert(ALL_BACKENDS.any { it is OrtBackend.Cpu })
        assert(ALL_BACKENDS.any { it is OrtBackend.Xnnpack })
        assert(ALL_BACKENDS.any { it is OrtBackend.Nnapi })
    }
}
