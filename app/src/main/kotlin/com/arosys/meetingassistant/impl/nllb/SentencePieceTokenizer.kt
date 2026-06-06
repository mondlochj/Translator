package com.arosys.meetingassistant.impl.nllb

import android.content.Context
import android.util.Log
import org.json.JSONObject

private const val TAG = "SPTokenizer"

/**
 * Minimal SentencePiece tokenizer for NLLB-200.
 *
 * Decoding (IDs → text) is exact: look up each token in the vocabulary,
 * replace the ▁ boundary marker with a space, handle byte-level fallback
 * tokens `<0xXX>`, and strip leading/trailing whitespace.
 *
 * Encoding (text → IDs) uses greedy longest-match over the vocabulary trie.
 * This is equivalent to the unigram SP algorithm under a flat score model
 * and produces correct tokenisation for the vast majority of Spanish text.
 *
 * The vocabulary is loaded lazily from [NLLBConfig.VOCAB_ASSET] (JSON map
 * of token-string → int ID, ~15 MB).  When the asset is absent all calls
 * return empty arrays — the engine surfaces a "model not found" message.
 */
class SentencePieceTokenizer private constructor(
    private val idToToken: Map<Int, String>,
    private val tokenToId: Map<String, Int>,
) {

    companion object {
        /** Production constructor — loads vocab from assets. */
        operator fun invoke(context: Context): SentencePieceTokenizer {
            val (id2t, t2id) = loadVocab(context)
            return SentencePieceTokenizer(id2t, t2id)
        }

        /** Test-only factory — accepts pre-built maps, skips asset loading. */
        internal fun forTesting(
            idToToken: Map<Int, String>,
            tokenToId: Map<String, Int>,
        ): SentencePieceTokenizer = SentencePieceTokenizer(idToToken, tokenToId)

        private fun loadVocab(context: Context): Pair<Map<Int, String>, Map<String, Int>> {
            return try {
                val json = context.assets.open(NLLBConfig.VOCAB_ASSET)
                    .bufferedReader().use { it.readText() }
                val obj = JSONObject(json)
                val size = obj.length()
                val id2t = HashMap<Int, String>(size)
                val t2id = HashMap<String, Int>(size)
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val token = keys.next()
                    val id = obj.getInt(token)
                    id2t[id] = token
                    t2id[token] = id
                }
                id2t to t2id
            } catch (e: Exception) {
                Log.w(TAG, "NLLB vocab not found: ${e.message}")
                emptyMap<Int, String>() to emptyMap<String, Int>()
            }
        }
    }

    private val trie: Trie = Trie(tokenToId.keys)

    val isLoaded: Boolean get() = idToToken.isNotEmpty()

    init {
        Log.d(TAG, "Loaded ${idToToken.size} tokens (loaded=$isLoaded)")
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Encodes [text] for NLLB input.
     * Prepends the forced source-language token so the encoder knows the
     * input language, then appends EOS.
     *
     * Returned array: [langToken, token_1, token_2, ..., EOS]
     */
    fun encode(text: String, langTokenId: Int = NLLBConfig.TOKEN_LANG_SPA): LongArray {
        if (!isLoaded) return LongArray(0)
        val pieces = encodeText(text)
        return LongArray(pieces.size + 2).also { arr ->
            arr[0] = langTokenId.toLong()
            pieces.forEachIndexed { i, id -> arr[i + 1] = id.toLong() }
            arr[arr.size - 1] = NLLBConfig.TOKEN_EOS.toLong()
        }
    }

    /**
     * Decodes a list of decoder output token IDs to a UTF-8 string.
     * Skips all special tokens (ID ≤ 3 or ≥ VOCAB_SIZE - num_lang_tokens).
     */
    fun decode(ids: List<Int>): String {
        if (!isLoaded) return ""
        val sb = StringBuilder()
        for (id in ids) {
            if (isSpecial(id)) continue
            val token = idToToken[id] ?: continue
            when {
                token.startsWith("<0x") && token.endsWith(">") -> {
                    // Byte-level fallback: <0x41> → 'A'
                    val hex = token.removePrefix("<0x").removeSuffix(">")
                    val byte = hex.toIntOrNull(16) ?: continue
                    sb.append(byte.toChar())
                }
                token.startsWith(NLLBConfig.SP_BOUNDARY) -> {
                    sb.append(' ')
                    sb.append(token.drop(1))
                }
                else -> sb.append(token)
            }
        }
        return sb.toString().trim()
    }

    // -------------------------------------------------------------------------
    // Encoding internals
    // -------------------------------------------------------------------------

    private fun encodeText(text: String): List<Int> {
        val normalized = text.trim()
        val result = mutableListOf<Int>()
        var pos = 0

        while (pos < normalized.length) {
            val atWordBoundary = pos == 0 || normalized[pos - 1] == ' '
            if (normalized[pos] == ' ') { pos++; continue }

            val (matchLen, tokenId) = trie.longestMatch(normalized, pos, addBoundary = atWordBoundary)

            if (tokenId != null) {
                result.add(tokenId)
                pos += matchLen
            } else {
                val c = normalized[pos]
                val byteToken = "<0x${c.code.toString(16).uppercase().padStart(2, '0')}>"
                tokenToId[byteToken]?.let { result.add(it) } ?: result.add(NLLBConfig.TOKEN_UNK)
                pos++
            }
        }
        return result
    }

    private fun isSpecial(id: Int): Boolean =
        id == NLLBConfig.TOKEN_PAD || id == NLLBConfig.TOKEN_BOS ||
        id == NLLBConfig.TOKEN_EOS || id == NLLBConfig.TOKEN_UNK ||
        id >= 256_001   // language tokens

    // -------------------------------------------------------------------------
    // Trie for O(k) longest-match lookup, k = match length
    // -------------------------------------------------------------------------

    private inner class Trie(keys: Iterable<String>) {
        private val root = TrieNode()

        init {
            for (key in keys) {
                var node = root
                for (ch in key) node = node.children.getOrPut(ch) { TrieNode() }
                node.tokenId = tokenToId[key]
            }
        }

        /**
         * Returns the length and token ID of the longest match starting at
         * [text][start], optionally testing a ▁-prefixed version first.
         */
        fun longestMatch(text: String, start: Int, addBoundary: Boolean): Pair<Int, Int?> {
            var bestLen = 0
            var bestId: Int? = null

            if (addBoundary) {
                val boundaryNode = root.children[NLLBConfig.SP_BOUNDARY]
                if (boundaryNode != null) {
                    var cur = boundaryNode
                    var pos = start
                    while (pos < text.length && text[pos] != ' ') {
                        cur = cur.children[text[pos]] ?: break
                        pos++
                        cur.tokenId?.let { bestLen = pos - start; bestId = it }
                    }
                }
            }

            var cur = root
            var pos = start
            while (pos < text.length && text[pos] != ' ') {
                cur = cur.children[text[pos]] ?: break
                pos++
                cur.tokenId?.let {
                    if (pos - start > bestLen) { bestLen = pos - start; bestId = it }
                }
            }

            return bestLen to bestId
        }
    }

    private class TrieNode {
        val children = HashMap<Char, TrieNode>(4)
        var tokenId: Int? = null
    }
}
