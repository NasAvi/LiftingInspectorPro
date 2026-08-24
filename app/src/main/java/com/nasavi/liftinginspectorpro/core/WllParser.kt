package com.nasavi.liftinginspectorpro.core

data class ParsedWll(val display: String, val ton: Double?, val angleDeg: Int?)

object WllParser {
    fun parse(input: String): ParsedWll {
        val text = input.trim()
        return if (text.contains('/')) {
            val parts = text.split('/')
            val ton = parts.getOrNull(0)?.trim()?.toDoubleOrNull()
            val angle = parts.getOrNull(1)?.replace("°", "")?.trim()?.toIntOrNull()
            ParsedWll(text, ton, angle)
        } else {
            ParsedWll(text, text.toDoubleOrNull(), null)
        }
    }
}

