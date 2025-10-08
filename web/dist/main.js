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
    const serverUrlInput = document.getElementById('serverUrl');
    const connectBtn = document.getElementById('connectBtn');
    const streamImg = document.getElementById('streamImg');
    let edgesEnabled = true;
    let pendingSend = null;
    let serverUrl = null;
    let pollTimer = null;
    function updateStatus(msg) {
        if (statusText)
            statusText.textContent = `Status: ${msg}`;
    }
    async function postSettings() {
        var _a, _b;
        if (!serverUrl)
            return;
        try {
            const payload = {
                lowThreshold: Number((_a = lowSlider === null || lowSlider === void 0 ? void 0 : lowSlider.value) !== null && _a !== void 0 ? _a : 0),
                highThreshold: Number((_b = highSlider === null || highSlider === void 0 ? void 0 : highSlider.value) !== null && _b !== void 0 ? _b : 0),
                edgesEnabled,
            };
            const res = await fetch(`${serverUrl}/settings`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload),
            });
            if (!res.ok)
                throw new Error(`HTTP ${res.status}`);
            updateStatus('Settings applied');
        }
        catch (e) {
            console.warn('postSettings error', e);
            updateStatus('Failed to apply settings');
        }
    }
    function scheduleSettingsSend() {
        if (pendingSend)
            clearTimeout(pendingSend);
        pendingSend = window.setTimeout(() => {
            void postSettings();
        }, 200);
    }
    function startPolling() {
        if (!serverUrl || !streamImg)
            return;
        // Show image element and hide placeholder
        const placeholder = preview === null || preview === void 0 ? void 0 : preview.querySelector('.placeholder');
        if (placeholder)
            placeholder.style.display = 'none';
        if (streamImg)
            streamImg.style.display = 'block';
        const poll = async () => {
            try {
                // Update status
                const sres = await fetch(`${serverUrl}/status`);
                if (sres.ok) {
                    const sj = await sres.json();
                    updateStatus(`Device ${sj.status}`);
                }
                else {
                    updateStatus('Status error');
                }
                // Refresh frame image by busting cache
                const ts = Date.now();
                streamImg.src = `${serverUrl}/frame.jpg?t=${ts}`;
            }
            catch (e) {
                updateStatus('Disconnected');
            }
            finally {
                pollTimer = window.setTimeout(poll, 1000);
            }
        };
        void poll();
    }
    function connect() {
        const url = serverUrlInput === null || serverUrlInput === void 0 ? void 0 : serverUrlInput.value.trim();
        if (!url) {
            updateStatus('Please enter device server URL');
            return;
        }
        serverUrl = url.replace(/\/$/, '');
        updateStatus('Connecting...');
        startPolling();
    }
    // Slider events
    lowSlider === null || lowSlider === void 0 ? void 0 : lowSlider.addEventListener('input', () => {
        if (lowValue && lowSlider)
            lowValue.textContent = lowSlider.value;
        scheduleSettingsSend();
    });
    highSlider === null || highSlider === void 0 ? void 0 : highSlider.addEventListener('input', () => {
        if (highValue && highSlider)
            highValue.textContent = highSlider.value;
        scheduleSettingsSend();
    });
    // Toggle edges
    toggleBtn === null || toggleBtn === void 0 ? void 0 : toggleBtn.addEventListener('click', () => {
        edgesEnabled = !edgesEnabled;
        if (toggleBtn)
            toggleBtn.textContent = edgesEnabled ? 'Disable Edge Detection' : 'Enable Edge Detection';
        scheduleSettingsSend();
    });
    // Connect button
    connectBtn === null || connectBtn === void 0 ? void 0 : connectBtn.addEventListener('click', () => connect());
    // Initialize defaults
    updateStatus('Backend not connected');
})();
