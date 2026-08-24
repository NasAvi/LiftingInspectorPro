package com.nasavi.liftinginspectorpro.data

data class FieldOption(
    val typeId: String,
    val fieldId: String,
    val option: String,
    val dependsOn: String? = null,
    val diameterChain: String? = null
)
