(function () {
  const state = {
    scanner: null
  };

  async function stopCamera() {
    if (state.scanner) {
      try {
        await state.scanner.stop();
        await state.scanner.clear();
      } catch (err) {
        console.warn("Unable to stop QR camera cleanly", err);
      }
      state.scanner = null;
    }
    const container = document.getElementById("qr-camera-container");
    if (container) {
      container.classList.remove("active");
      container.innerHTML = "";
    }
  }

  async function startCamera(setStatus) {
    if (state.scanner) {
      return;
    }
    const container = document.getElementById("qr-camera-container");
    if (!container) {
      setStatus("Camera container not found.", "error");
      return;
    }
    if (!window.Html5Qrcode) {
      setStatus("Camera scanning is unavailable on this device.", "error");
      return;
    }
    container.innerHTML = "";
    container.classList.add("active");

    try {
      const scanner = new Html5Qrcode(container.id, { verbose: false });
      state.scanner = scanner;
      await scanner.start(
        { facingMode: "environment" },
        { fps: 10, qrbox: { width: 240, height: 240 } },
        (text) => {
          const input = document.getElementById("qr-input");
          if (input) {
            input.value = text;
          }
          setStatus("QR captured from camera.", "success");
          stopCamera();
        },
        () => {}
      );
      setStatus("Align the QR code within the frame.", "");
    } catch (error) {
      console.error("QR camera start failed", error);
      setStatus("Unable to access camera: " + error, "error");
      await stopCamera();
    }
  }

  function handleFileUpload(event, setStatus) {
    const file = event.target.files && event.target.files[0];
    if (!file) {
      return;
    }
    if (!window.Html5Qrcode) {
      setStatus("Image scanning is unavailable on this device.", "error");
      return;
    }
    setStatus("Processing QR image...", "");
    const previewId = "qr-file-preview";
    const readerContainer = document.getElementById(previewId) || document.createElement("div");
    readerContainer.id = previewId;
    readerContainer.classList.add("visually-hidden");
    document.body.appendChild(readerContainer);
    const scanner = new Html5Qrcode(previewId, { verbose: false });
    scanner.scanFileV2(file, true)
      .then((result) => {
        const input = document.getElementById("qr-input");
        if (input) {
          input.value = result.text;
        }
        setStatus("QR recognised from image.", "success");
      })
      .catch((err) => {
        console.warn("QR scan failed", err);
        setStatus("Could not read QR image. Try a clearer photo.", "error");
      })
      .finally(async () => {
        try {
          await scanner.clear();
        } catch (clearErr) {
          console.warn("Unable to clear QR scanner", clearErr);
        }
      });
  }

  function setStatusFactory(statusEl) {
    return function setStatus(message, tone) {
      if (!statusEl) {
        return;
      }
      statusEl.textContent = message || " ";
      statusEl.classList.remove("success", "error");
      if (tone) {
        statusEl.classList.add(tone);
      }
    };
  }

  window.setupCartPanel = function setupCartPanel() {
    const qrInput = document.getElementById("qr-input");
    if (!qrInput) {
      return;
    }
    const root = qrInput.closest("form") || qrInput.parentElement;
    if (!root) {
      return;
    }
    if (root.dataset.qrEnhanced === "true") {
      return;
    }
    root.dataset.qrEnhanced = "true";

    const statusEl = document.getElementById("qr-status");
    const setStatus = setStatusFactory(statusEl);
    const fileInput = document.getElementById("qr-file");
    const cameraBtn = document.getElementById("qr-camera-btn");

    if (fileInput) {
      fileInput.addEventListener("change", function (event) {
        handleFileUpload(event, setStatus);
      });
    }

    if (cameraBtn) {
      cameraBtn.addEventListener("click", async function () {
        if (state.scanner) {
          await stopCamera();
          setStatus("Camera closed.", "");
        } else {
          await startCamera(setStatus);
        }
      });
    }
  };

  document.addEventListener("htmx:beforeSwap", function (event) {
    if (event.detail && event.detail.target && event.detail.target.id === "cart-panel") {
      stopCamera();
    }
  });

  window.addEventListener("beforeunload", stopCamera);
})();
