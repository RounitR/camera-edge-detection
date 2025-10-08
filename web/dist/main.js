"use strict";
// Use strict null checks and proper element types
(() => {
    const lowSlider = document.getElementById('lowThreshold');
    const highSlider = document.getElementById('highThreshold');
    const lowValue = document.getElementById('lowValue');
    const highValue = document.getElementById('highValue');
    const toggleBtn = document.getElementById('toggleEdges');
    const statusText = document.getElementById('statusText');
    const preview = document.getElementById('preview');
    let edgesEnabled = true;
    let pendingSend = null;
    function updateStatus(msg) {
        if (statusText)
            statusText.textContent = `Status: ${msg}`;
    }
    function applySettings() {
        var _a, _b;
        const settings = {
            lowThreshold: Number((_a = lowSlider === null || lowSlider === void 0 ? void 0 : lowSlider.value) !== null && _a !== void 0 ? _a : 0),
            highThreshold: Number((_b = highSlider === null || highSlider === void 0 ? void 0 : highSlider.value) !== null && _b !== void 0 ? _b : 0),
            edgesEnabled,
        };
        if (pendingSend)
            clearTimeout(pendingSend);
        pendingSend = window.setTimeout(() => {
            console.log('[web] settings', settings);
            updateStatus('Backend not connected (using local demo settings)');
        }, 200);
    }
    // Slider events
    lowSlider === null || lowSlider === void 0 ? void 0 : lowSlider.addEventListener('input', () => {
        if (lowValue && lowSlider)
            lowValue.textContent = lowSlider.value;
        applySettings();
    });
    highSlider === null || highSlider === void 0 ? void 0 : highSlider.addEventListener('input', () => {
        if (highValue && highSlider)
            highValue.textContent = highSlider.value;
        applySettings();
    });
    // Toggle edges
    toggleBtn === null || toggleBtn === void 0 ? void 0 : toggleBtn.addEventListener('click', () => {
        edgesEnabled = !edgesEnabled;
        if (toggleBtn)
            toggleBtn.textContent = edgesEnabled ? 'Disable Edge Detection' : 'Enable Edge Detection';
        applySettings();
    });
    // Placeholder stream element
    const placeholder = document.createElement('div');
    placeholder.className = 'placeholder';
    placeholder.textContent = 'No stream connected';
    if (preview) {
        preview.innerHTML = '';
        preview.appendChild(placeholder);
    }
    // Initialize defaults
    updateStatus('Backend not connected');
    applySettings();
})();
