package com.jwidess.mcmurdomap

import android.webkit.JavascriptInterface

class AndroidBridge(
    private val onBuildingSelectedCallback: (String) -> Unit,
    private val onBuildingDeselectedCallback: () -> Unit,
    private val onHelpRequestedCallback: () -> Unit = {},
    private val onMapReadyCallback: () -> Unit = {}
) {
    @JavascriptInterface
    fun onBuildingSelected(buildingId: String) {
        onBuildingSelectedCallback(buildingId)
    }

    @JavascriptInterface
    fun onBuildingDeselected() {
        onBuildingDeselectedCallback()
    }

    @JavascriptInterface
    fun onHelpRequested() {
        onHelpRequestedCallback()
    }

    @JavascriptInterface
    fun onMapReady() {
        onMapReadyCallback()
    }
}
