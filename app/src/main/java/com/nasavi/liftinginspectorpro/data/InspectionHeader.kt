package com.nasavi.liftinginspectorpro.data

data class InspectionHeader(
    val inspectorNumber: String,
    val runningNumber: Int,
    val inspectionDate: String,
    val nextInspectionDate: String,
    val owner: String,
    val address: String,
    val phone: String,
    val vehicle: String,
    val inspectionLocation: String
)
