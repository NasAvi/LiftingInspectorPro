package com.nasavi.liftinginspectorpro.core

object AccessoryDescriptionBuilder {
    fun build(
        typeName: String,
        legs: String,
        chainSize: String,
        length: String?,
        end1: String?,
        end2: String?,
        end3: String?,
        end4: String?
    ): String {
        val base = "$typeName $legs $chainSize".trim()
        val lengthText = if (!length.isNullOrBlank()) " באורך $length מטר" else ""
        val ends = listOfNotNull(end1, end2, end3, end4).filter { it.isNotBlank() }
        val endsText = if (ends.isEmpty()) "" else " עם " + ends.joinToString(" ו")
        return (base + lengthText + endsText).trim()
    }
}

