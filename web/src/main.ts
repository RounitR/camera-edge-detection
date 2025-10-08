// Use strict null checks and proper element types
(() => {
  const lowSlider = document.getElementById('lowThreshold') as HTMLInputElement | null;
  const highSlider = document.getElementById('highThreshold') as HTMLInputElement | null;
  const lowValue = document.getElementById('lowValue') as HTMLSpanElement | null;
  const highValue = document.getElementById('highValue') as HTMLSpanElement | null;
  const toggleBtn = document.getElementById('toggleEdges') as HTMLButtonElement | null;
  const statusText = document.getElementById('statusText') as HTMLDivElement | null;
  const preview = document.getElementById('preview') as HTMLDivElement | null;
  const serverUrlInput = document.getElementById('serverUrl') as HTMLInputElement | null;
  const connectBtn = document.getElementById('connectBtn') as HTMLButtonElement | null;
  const streamImg = document.getElementById('streamImg') as HTMLImageElement | null;

  let edgesEnabled: boolean = true;
  let pendingSend: number | null = null;
  let serverUrl: string | null = null;
  let pollTimer: number | null = null;

  function updateStatus(msg: string): void {
    if (statusText) statusText.textContent = `Status: ${msg}`;
  }

  async function postSettings(): Promise<void> {
    if (!serverUrl) return;
    try {
      const payload = {
        lowThreshold: Number(lowSlider?.value ?? 0),
        highThreshold: Number(highSlider?.value ?? 0),
        edgesEnabled,
      };
      const res = await fetch(`${serverUrl}/settings`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      updateStatus('Settings applied');
    } catch (e) {
      console.warn('postSettings error', e);
      updateStatus('Failed to apply settings');
    }
  }

  function scheduleSettingsSend(): void {
    if (pendingSend) clearTimeout(pendingSend);
    pendingSend = window.setTimeout(() => {
      void postSettings();
    }, 200);
  }

  function startPolling(): void {
    if (!serverUrl || !streamImg) return;
    // Show image element and hide placeholder
    const placeholder = preview?.querySelector('.placeholder') as HTMLElement | null;
    if (placeholder) placeholder.style.display = 'none';
    if (streamImg) streamImg.style.display = 'block';

    const poll = async () => {
      try {
        // Update status
        const sres = await fetch(`${serverUrl}/status`);
        if (sres.ok) {
          const sj = await sres.json();
          updateStatus(`Device ${sj.status}`);
        } else {
          updateStatus('Status error');
        }
        // Refresh frame image by busting cache
        const ts = Date.now();
        streamImg.src = `${serverUrl}/frame.jpg?t=${ts}`;
      } catch (e) {
        updateStatus('Disconnected');
      } finally {
        pollTimer = window.setTimeout(poll, 1000);
      }
    };
    void poll();
  }

  function connect(): void {
    const url = serverUrlInput?.value.trim();
    if (!url) {
      updateStatus('Please enter device server URL');
      return;
    }
    serverUrl = url.replace(/\/$/, '');
    updateStatus('Connecting...');
    startPolling();
  }

  // Slider events
  lowSlider?.addEventListener('input', () => {
    if (lowValue && lowSlider) lowValue.textContent = lowSlider.value;
    scheduleSettingsSend();
  });
  highSlider?.addEventListener('input', () => {
    if (highValue && highSlider) highValue.textContent = highSlider.value;
    scheduleSettingsSend();
  });

  // Toggle edges
  toggleBtn?.addEventListener('click', () => {
    edgesEnabled = !edgesEnabled;
    if (toggleBtn) toggleBtn.textContent = edgesEnabled ? 'Disable Edge Detection' : 'Enable Edge Detection';
    scheduleSettingsSend();
  });

  // Connect button
  connectBtn?.addEventListener('click', () => connect());

  // Initialize defaults
  updateStatus('Backend not connected');
})();