package com.nasavi.liftinginspectorpro.core

object InspectionNumberGenerator {
    fun build(inspectorNumber: String, runningNumber: Int): String = "$inspectorNumber/R$runningNumber"
}

