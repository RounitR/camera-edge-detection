// Use strict null checks and proper element types
(() => {
  const lowSlider = document.getElementById('lowThreshold') as HTMLInputElement | null;
  const highSlider = document.getElementById('highThreshold') as HTMLInputElement | null;
  const lowValue = document.getElementById('lowValue') as HTMLSpanElement | null;
  const highValue = document.getElementById('highValue') as HTMLSpanElement | null;
  const toggleBtn = document.getElementById('toggleEdges') as HTMLButtonElement | null;
  const statusText = document.getElementById('statusText') as HTMLDivElement | null;
  const preview = document.getElementById('preview') as HTMLDivElement | null;

  let edgesEnabled: boolean = true;
  let pendingSend: number | null = null;

  function updateStatus(msg: string): void {
    if (statusText) statusText.textContent = `Status: ${msg}`;
  }

  function applySettings(): void {
    const settings = {
      lowThreshold: Number(lowSlider?.value ?? 0),
      highThreshold: Number(highSlider?.value ?? 0),
      edgesEnabled,
    };
    if (pendingSend) clearTimeout(pendingSend);
    pendingSend = window.setTimeout(() => {
      console.log('[web] settings', settings);
      updateStatus('Backend not connected (using local demo settings)');
    }, 200);
  }

  // Slider events
  lowSlider?.addEventListener('input', () => {
    if (lowValue && lowSlider) lowValue.textContent = lowSlider.value;
    applySettings();
  });
  highSlider?.addEventListener('input', () => {
    if (highValue && highSlider) highValue.textContent = highSlider.value;
    applySettings();
  });

  // Toggle edges
  toggleBtn?.addEventListener('click', () => {
    edgesEnabled = !edgesEnabled;
    if (toggleBtn) toggleBtn.textContent = edgesEnabled ? 'Disable Edge Detection' : 'Enable Edge Detection';
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