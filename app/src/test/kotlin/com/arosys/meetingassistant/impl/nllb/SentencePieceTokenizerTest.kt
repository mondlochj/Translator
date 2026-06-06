package com.arosys.meetingassistant.impl.nllb

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [SentencePieceTokenizer].
 *
 * Uses [SentencePieceTokenizer.forTesting] to inject a minimal controlled
 * vocabulary so these tests run on the JVM without an Android Context or
 * asset files.
 *
 * Token ID assignments in the test vocab:
 *   0=<s>  1=<pad>  2=</s>  3=<unk>
 *   100=▁Hola   101=▁mundo   102=▁el   103=▁cronograma
 *   104=quick   105=▁fix
 *   106=<0x41> ('A'), 107=<0x4E> ('N')
 *   256047=__eng_Latn__   256057=__spa_Latn__
 */
class SentencePieceTokenizerTest {

    private lateinit var tokenizer: SentencePieceTokenizer

    // Minimal vocabulary — mirrors the real NLLB-200 token format
    private val id2t = mapOf(
        0    to "<s>",
        1    to "<pad>",
        2    to "</s>",
        3    to "<unk>",
        100  to "▁Hola",
        101  to "▁mundo",
        102  to "▁el",
        103  to "▁cronograma",
        104  to "quick",
        105  to "▁fix",
        106  to "<0x41>",
        107  to "<0x4E>",
        256047 to "__eng_Latn__",
        256057 to "__spa_Latn__",
    )
    private val t2id = id2t.entries.associate { (k, v) -> v to k }

    @Before
    fun setup() {
        tokenizer = SentencePieceTokenizer.forTesting(id2t, t2id)
    }

    // -------------------------------------------------------------------------
    // isLoaded
    // -------------------------------------------------------------------------

    @Test
    fun `isLoaded is true when maps are non-empty`() {
        assertTrue(tokenizer.isLoaded)
    }

    @Test
    fun `isLoaded is false when maps are empty`() {
        val empty = SentencePieceTokenizer.forTesting(emptyMap(), emptyMap())
        assertFalse(empty.isLoaded)
    }

    // -------------------------------------------------------------------------
    // decode — happy paths
    // -------------------------------------------------------------------------

    @Test
    fun `decode converts boundary marker to space`() {
        // ▁Hola ▁mundo → " Hola mundo" → trim → "Hola mundo"
        assertEquals("Hola mundo", tokenizer.decode(listOf(100, 101)))
    }

    @Test
    fun `decode skips bos eos pad unk tokens`() {
        assertEquals("", tokenizer.decode(listOf(0, 1, 2, 3)))
    }

    @Test
    fun `decode skips language tokens (id >= 256001)`() {
        // lang token 256047 should be skipped
        assertEquals("Hola mundo", tokenizer.decode(listOf(256047, 100, 101)))
    }

    @Test
    fun `decode handles byte-level fallback token`() {
        // <0x41> = 'A', <0x4E> = 'N'
        assertEquals("AN", tokenizer.decode(listOf(106, 107)))
    }

    @Test
    fun `decode handles mixed regular and byte tokens`() {
        // ▁Hola <0x41> → "Hola A" (note: trim, no leading space on first ▁)
        assertEquals("Hola A", tokenizer.decode(listOf(100, 106)))
    }

    @Test
    fun `decode returns empty for empty list`() {
        assertEquals("", tokenizer.decode(emptyList()))
    }

    @Test
    fun `decode handles tokens without boundary marker`() {
        // "quick" has no ▁ prefix — appended directly
        assertEquals("Hola quick", tokenizer.decode(listOf(100, 104)))
    }

    @Test
    fun `decode returns empty when tokenizer not loaded`() {
        val empty = SentencePieceTokenizer.forTesting(emptyMap(), emptyMap())
        assertEquals("", empty.decode(listOf(100, 101)))
    }

    // -------------------------------------------------------------------------
    // encode — happy paths
    // -------------------------------------------------------------------------

    @Test
    fun `encode wraps tokens with lang token and EOS`() {
        val ids = tokenizer.encode("Hola", NLLBConfig.TOKEN_LANG_SPA)
        // [TOKEN_LANG_SPA, 100, TOKEN_EOS] = [256057, 100, 2]
        assertArrayEquals(longArrayOf(256057L, 100L, 2L), ids)
    }

    @Test
    fun `encode handles multiple words`() {
        val ids = tokenizer.encode("Hola mundo", NLLBConfig.TOKEN_LANG_SPA)
        assertArrayEquals(longArrayOf(256057L, 100L, 101L, 2L), ids)
    }

    @Test
    fun `encode returns only lang token plus EOS for blank text`() {
        val ids = tokenizer.encode("", NLLBConfig.TOKEN_LANG_SPA)
        assertArrayEquals(longArrayOf(256057L, 2L), ids)
    }

    @Test
    fun `encode returns empty array when not loaded`() {
        val empty = SentencePieceTokenizer.forTesting(emptyMap(), emptyMap())
        assertArrayEquals(LongArray(0), empty.encode("Hola"))
    }

    @Test
    fun `encode uses TARGET lang token when specified`() {
        val ids = tokenizer.encode("Hola", NLLBConfig.TOKEN_LANG_ENG)
        assertArrayEquals(longArrayOf(256047L, 100L, 2L), ids)
    }

    // -------------------------------------------------------------------------
    // encode/decode roundtrip
    // -------------------------------------------------------------------------

    @Test
    fun `decode reverses encode for simple sentence`() {
        val original = "Hola mundo"
        val encoded = tokenizer.encode(original, NLLBConfig.TOKEN_LANG_SPA)
        // Drop lang token (first) and EOS (last), decode remainder
        val decoded = tokenizer.decode(encoded.drop(1).dropLast(1).map { it.toInt() })
        assertEquals(original, decoded)
    }
}
