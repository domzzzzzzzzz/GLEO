(() => {
  const DEFAULT_MAX_SIZE = 5 * 1024 * 1024; // 5 MB

  function setupDropzone(zone) {
    const input = zone.querySelector('input[type="file"]');
    if (!input) {
      return;
    }
    const prompt = zone.querySelector('[data-dropzone-prompt]');
    const preview = zone.querySelector('[data-dropzone-preview]');
    const errorEl = zone.querySelector('[data-dropzone-error]');
    const originalPrompt = prompt ? prompt.textContent.trim() : '';
    const maxSize = parseInt(zone.dataset.maxSize || DEFAULT_MAX_SIZE, 10);
    const initialSrc = preview && preview.getAttribute('data-initial-src');
    zone.dataset.initialSrc = initialSrc || '';

    if (initialSrc) {
      preview.style.display = 'block';
      zone.classList.add('dropzone--has-image');
    }

    function setError(message) {
      if (errorEl) {
        errorEl.textContent = message;
      }
      zone.classList.add('dropzone--error');
    }

    function clearError() {
      if (errorEl) {
        errorEl.textContent = '';
      }
      zone.classList.remove('dropzone--error');
    }

    function setPrompt(text) {
      if (prompt) {
        prompt.textContent = text;
      }
    }

    function restorePrompt() {
      if (prompt) {
        prompt.textContent = originalPrompt;
      }
    }

    function updatePreview(src) {
      if (!preview) {
        return;
      }
      if (src) {
        preview.src = src;
        preview.style.display = 'block';
        zone.classList.add('dropzone--has-image');
      } else {
        preview.src = initialSrc || '';
        preview.style.display = initialSrc ? 'block' : 'none';
        if (!initialSrc) {
          zone.classList.remove('dropzone--has-image');
        }
      }
    }

    function acceptFile(file) {
      if (!file.type || !file.type.startsWith('image/')) {
        setError('Please choose an image file.');
        return false;
      }
      if (file.size > maxSize) {
        const sizeMb = Math.round((maxSize / (1024 * 1024)) * 10) / 10;
        setError(`Image must be ${sizeMb} MB or smaller.`);
        return false;
      }
      clearError();
      const reader = new FileReader();
      reader.onload = (event) => updatePreview(event.target.result);
      reader.readAsDataURL(file);
      zone.classList.remove('dropzone--removed');
      restorePrompt();
      return true;
    }

    input.addEventListener('change', () => {
      const file = input.files && input.files[0];
      if (file) {
        if (!acceptFile(file)) {
          input.value = '';
        }
      } else {
        updatePreview(null);
      }
    });

    zone.addEventListener('click', (event) => {
      if (event.target !== input) {
        input.click();
      }
    });

    zone.addEventListener('dragover', (event) => {
      event.preventDefault();
      zone.classList.add('dropzone--drag');
    });

    zone.addEventListener('dragleave', (event) => {
      if (event.target === zone) {
        zone.classList.remove('dropzone--drag');
      }
    });

    zone.addEventListener('drop', (event) => {
      event.preventDefault();
      zone.classList.remove('dropzone--drag');
      const file = event.dataTransfer.files && event.dataTransfer.files[0];
      if (file && acceptFile(file)) {
        try {
          const dt = new DataTransfer();
          dt.items.add(file);
          input.files = dt.files;
        } catch (err) {
          // Fallback: ignore if DataTransfer unsupported
        }
      }
    });
  }

  function setupRemovalToggles() {
    document.querySelectorAll('[data-dropzone-target]').forEach((checkbox) => {
      const targetId = checkbox.getAttribute('data-dropzone-target');
      const zone = document.getElementById(targetId);
      if (!zone) {
        return;
      }
      const prompt = zone.querySelector('[data-dropzone-prompt]');
      const preview = zone.querySelector('[data-dropzone-preview]');
      const fileInput = zone.querySelector('input[type="file"]');
      const originalPrompt = prompt ? prompt.textContent.trim() : '';

      checkbox.addEventListener('change', () => {
        if (checkbox.checked) {
          zone.classList.add('dropzone--removed');
          if (prompt) prompt.textContent = 'Current image will be removed';
          if (fileInput) fileInput.value = '';
          if (preview) {
            const initialSrc = zone.dataset.initialSrc || '';
            if (initialSrc) {
              preview.src = initialSrc;
              preview.style.display = 'block';
            } else {
              preview.style.display = 'none';
            }
          }
        } else {
          zone.classList.remove('dropzone--removed');
          if (prompt) prompt.textContent = originalPrompt;
          if (preview) {
            const initialSrc = zone.dataset.initialSrc || '';
            if (initialSrc) {
              preview.src = initialSrc;
              preview.style.display = 'block';
            }
          }
        }
      });

      // Trigger initial state
      checkbox.dispatchEvent(new Event('change'));
    });
  }

  document.addEventListener('DOMContentLoaded', () => {
    document.querySelectorAll('[data-dropzone]').forEach(setupDropzone);
    setupRemovalToggles();
  });
})();
