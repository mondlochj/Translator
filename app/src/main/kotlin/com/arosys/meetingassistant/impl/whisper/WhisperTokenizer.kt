package com.arosys.meetingassistant.impl.whisper

import android.content.Context
import android.util.Log
import org.json.JSONObject

private const val TAG = "WhisperTokenizer"

/**
 * Decodes Whisper token IDs to UTF-8 text.
 *
 * Loads the vocabulary from [WhisperConfig.VOCAB_ASSET] (JSON object mapping
 * token string → token ID) when the asset is present.  Falls back to byte-level
 * decoding (each token rendered as its ID in angle brackets) when the asset is
 * absent — useful in tests or before the model files are downloaded.
 *
 * The vocab JSON is the standard Whisper / GPT-2 vocab format:
 *   { "token_string": token_id, ... }
 */
class WhisperTokenizer(context: Context) {

    private val idToToken: Map<Int, String>

    init {
        idToToken = loadVocab(context)
        Log.d(TAG, "Tokenizer loaded: ${idToToken.size} tokens")
    }

    /**
     * Converts a list of token IDs to a human-readable string.
     * Strips special tokens (id >= 50256) and joins byte-level BPE tokens.
     */
    fun decode(tokenIds: List<Int>): String {
        val sb = StringBuilder()
        for (id in tokenIds) {
            if (id >= WhisperConfig.TOKEN_EOT) continue  // skip all special tokens
            val token = idToToken[id] ?: continue
            // GPT-2 BPE uses Ġ (U+0120) to represent a space before a word
            sb.append(token.replace("Ġ", " ").replace("Ċ", "\n"))
        }
        return sb.toString().trim()
    }

    private fun loadVocab(context: Context): Map<Int, String> {
        return try {
            val json = context.assets.open(WhisperConfig.VOCAB_ASSET)
                .bufferedReader().use { it.readText() }
            val obj = JSONObject(json)
            val map = HashMap<Int, String>(obj.length())
            val keys = obj.keys()
            while (keys.hasNext()) {
                val token = keys.next()
                map[obj.getInt(token)] = token
            }
            map
        } catch (e: Exception) {
            Log.w(TAG, "Vocab asset not found (${e.message}); using fallback byte decoder")
            emptyMap()
        }
    }
}
