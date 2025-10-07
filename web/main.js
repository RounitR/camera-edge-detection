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
    statusText.textContent = `Status: ${msg}`;
  }

  function applySettings() {
    const settings = {
      lowThreshold: Number(lowSlider.value),
      highThreshold: Number(highSlider.value),
      edgesEnabled,
    };
    // Placeholder: no backend yet. Simulate send and update UI.
    if (pendingSend) clearTimeout(pendingSend);
    pendingSend = setTimeout(() => {
      console.log('[web] settings', settings);
      updateStatus('Backend not connected (using local demo settings)');
    }, 200);
  }

  // Slider events
  lowSlider.addEventListener('input', () => {
    lowValue.textContent = lowSlider.value;
    applySettings();
  });
  highSlider.addEventListener('input', () => {
    highValue.textContent = highSlider.value;
    applySettings();
  });

  // Toggle edges
  toggleBtn.addEventListener('click', () => {
    edgesEnabled = !edgesEnabled;
    toggleBtn.textContent = edgesEnabled ? 'Disable Edge Detection' : 'Enable Edge Detection';
    applySettings();
  });

  // Placeholder stream element
  const placeholder = document.createElement('div');
  placeholder.className = 'placeholder';
  placeholder.textContent = 'No stream connected';
  preview.innerHTML = '';
  preview.appendChild(placeholder);

  // Initialize defaults
  updateStatus('Backend not connected');
  applySettings();
})();