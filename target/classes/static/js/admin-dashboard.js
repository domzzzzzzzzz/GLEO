// Centralized admin dashboard behaviors: theme, preferences, menu reorder, modals, admin menu keyboard nav
(function(){
  'use strict';

  // Enhanced UI utilities with better toast and loading indicators
  const AdvancedUI = window.AdvancedUI || {
    showToast: (message, type = 'info', duration = 3000) => {
      const toast = document.createElement('div');
      toast.className = `toast toast-${type} animate-in`;
      toast.setAttribute('role', 'alert');
      toast.innerHTML = `
        <div class="toast-content">
          <span class="toast-icon">${type === 'error' ? '❌' : type === 'success' ? '✅' : 'ℹ️'}</span>
          <span class="toast-message">${message}</span>
        </div>
      `;
      document.body.appendChild(toast);
      setTimeout(() => {
        toast.classList.add('animate-out');
        setTimeout(() => toast.remove(), 300);
      }, duration);
    },
    showLoading: (target, message = 'Loading...') => {
      const loader = document.createElement('div');
      loader.className = 'loading-overlay';
      loader.innerHTML = `
        <div class="loading-spinner"></div>
        <div class="loading-message">${message}</div>
      `;
      target.classList.add('loading');
      target.appendChild(loader);
      return () => {
        target.classList.remove('loading');
        loader.remove();
      };
    }
  };

  function readCsrfHeaders(){
    const csrfMeta = document.querySelector('meta[name="_csrf"]');
    const csrfHeaderMeta = document.querySelector('meta[name="_csrf_header"]');
    const headers = {};
    if (csrfMeta && csrfHeaderMeta) headers[csrfHeaderMeta.getAttribute('content')] = csrfMeta.getAttribute('content');
    return headers;
  }

  async function savePreferenceToServer(payload){
    try{
      const headers = Object.assign({'Content-Type':'application/json'}, readCsrfHeaders());
      await fetch('/admin/api/preferences', { method:'POST', headers, body: JSON.stringify(payload) });
    }catch(e){
      console.warn('pref save failed', e);
    }
  }

  // Theme
  window.toggleTheme = function(){
    const html = document.documentElement;
    const current = html.getAttribute('data-theme');
    const next = current === 'dark' ? 'light' : 'dark';
    html.setAttribute('data-theme', next);
    try{ localStorage.setItem('theme', next); }catch(e){}
    document.querySelectorAll('[data-theme-update]').forEach(el=>el.setAttribute('data-theme', next));
    // best-effort save
    savePreferenceToServer({ theme: next });
  };

  window.initializeTheme = function(){
    const html = document.documentElement;
    const server = html.getAttribute('data-theme');
    if (server) return; // server preference takes precedence
    let saved = null;
    try{ saved = localStorage.getItem('theme'); }catch(e){}
    const prefersDark = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches;
    const theme = saved || (prefersDark ? 'dark' : 'light');
    html.setAttribute('data-theme', theme);
  };

  // Menu order persistence
  window.saveMenuOrder = async function(){
    try{
      const menu = document.getElementById('adminMenu');
      if (!menu) return;
      const items = Array.from(menu.querySelectorAll('.menu-item'))
        .map(it => ({ text: it.textContent.trim(), href: it.getAttribute('href') }))
        .filter(i => i.text.length > 0);
      await savePreferenceToServer({ menuOrderJson: JSON.stringify(items) });
      AdvancedUI.showToast('Menu order saved', 'success');
    }catch(e){ AdvancedUI.showToast('Failed to save menu order','error'); }
  };

  // Drag & drop reorder for admin menu
  function enableMenuDrag(){
    const menu = document.getElementById('adminMenu');
    if (!menu) return;
    const items = menu.querySelectorAll('.menu-item');
    items.forEach(it => it.setAttribute('draggable','true'));

    let dragSrc = null;
    menu.addEventListener('dragstart', function(e){
      const tr = e.target.closest('.menu-item');
      if (!tr) return;
      dragSrc = tr;
      e.dataTransfer.effectAllowed = 'move';
    });

    menu.addEventListener('dragover', function(e){
      e.preventDefault();
      e.dataTransfer.dropEffect = 'move';
      const over = e.target.closest('.menu-item');
      if (!over || over === dragSrc) return;
      // show indicator by inserting before
      const rect = over.getBoundingClientRect();
      const after = (e.clientY - rect.top) > (rect.height/2);
      const parent = over.parentNode;
      if (after) parent.insertBefore(dragSrc, over.nextSibling); else parent.insertBefore(dragSrc, over);
    });

    menu.addEventListener('drop', function(e){
      e.preventDefault();
      dragSrc = null;
      // auto-save
      window.saveMenuOrder();
    });
  }

  // Modal helpers for delete confirmation
  let _pendingDeleteCode = null;
  window.openDeleteModal = function(el){
    const code = el.getAttribute('data-event-code') || el.dataset.eventCode;
    _pendingDeleteCode = code;
    const modal = document.getElementById('confirmModal');
    if (!modal) return;
    console.log('openDeleteModal, eventCode=', code);
    modal.style.display = 'block';
    modal.setAttribute('aria-hidden','false');
    const btn = document.getElementById('confirmDeleteBtn'); if (btn) btn.focus();
    // set hidden form action for fallback submit
    try{
      const form = document.getElementById('deleteForm');
      if (form) form.action = `/admin/events/${encodeURIComponent(code)}/delete`;
    }catch(e){ }
  };
  window.closeDeleteModal = function(){
    const modal = document.getElementById('confirmModal'); if (!modal) return;
    modal.style.display = 'none'; modal.setAttribute('aria-hidden','true'); _pendingDeleteCode = null;
  };

  // Small helpers for the event form (reset + validation)
  window.resetForm = function(){
    const form = document.getElementById('eventForm');
    if (form) form.reset();
    try{ AdvancedUI.showToast('Form reset successfully', 'info'); }catch(e){ console.log('reset'); }
  };

  window.confirmDelete = async function(){
    if (!_pendingDeleteCode) return window.closeDeleteModal();
    // If a hidden form is present with action set, submit it as the simplest fallback (will perform full page POST)
    try{
      const form = document.getElementById('deleteForm');
      if (form && form.action){
        console.log('Submitting fallback form to', form.action);
        form.submit();
        return;
      }
    }catch(e){ console.warn('fallback form submit failed', e); }
    const code = _pendingDeleteCode;
    const modal = document.getElementById('confirmModal');
    let stopLoading = null;
    try{
      // show loading indicator inside modal
      try{ stopLoading = AdvancedUI.showLoading(modal, 'Deleting...'); }catch(e){}

      const headers = Object.assign({'Content-Type':'application/x-www-form-urlencoded', 'X-Requested-With':'XMLHttpRequest'}, readCsrfHeaders());
      const resp = await fetch(`/admin/events/${encodeURIComponent(code)}/delete`, { method: 'POST', headers, body: '', credentials: 'same-origin', redirect: 'follow' });
      console.log('Delete response status:', resp.status, resp.statusText);
      if (resp.ok || resp.status === 204 || resp.status === 302 || resp.status === 303) {
        AdvancedUI.showToast('Event deleted', 'success');
        // small delay to allow toast to show before reload
        setTimeout(() => window.location.reload(), 350);
        return;
      } else {
        const txt = await resp.text().catch(()=>'');
        AdvancedUI.showToast('Failed to delete: '+(txt||resp.statusText||resp.status), 'error');
        console.warn('Delete failed', resp.status, txt);

        // If we got 403 or CSRF related, attempt form fallback or fetch with body containing _csrf
        if (resp.status === 403 || resp.status === 401) {
          const csrfMeta = document.querySelector('meta[name="_csrf"]');
          const token = csrfMeta ? csrfMeta.getAttribute('content') : null;
          const retryUrl = `/admin/events/${encodeURIComponent(code)}/delete`;
          if (token) {
            try{
              // First, try fetch again sending the token in the request body as form data
              const params = new URLSearchParams();
              params.append('_csrf', token);
              // include method override in case server expects DELETE via _method form param
              params.append('_method', 'DELETE');
              console.log('Retrying delete via fetch with body _csrf for', retryUrl);
              const retryResp = await fetch(retryUrl, { method: 'POST', headers: {'Content-Type':'application/x-www-form-urlencoded', 'X-Requested-With':'XMLHttpRequest'}, body: params.toString(), credentials: 'same-origin', redirect: 'follow' });
              console.log('Retry response', retryResp.status, retryResp.statusText);
              if (retryResp.ok || retryResp.status === 204 || retryResp.status === 302 || retryResp.status === 303) {
                AdvancedUI.showToast('Event deleted (retry)', 'success');
                setTimeout(() => window.location.reload(), 350);
                return;
              }
            }catch(er){ console.warn('Retry via fetch failed', er); }

            // fallback: create and submit a hidden form as a last resort
            try{
              const form = document.createElement('form');
              form.method = 'POST';
              form.action = retryUrl;
              form.style.display = 'none';
              const input = document.createElement('input');
              input.type = 'hidden';
              input.name = '_csrf';
              input.value = token;
              form.appendChild(input);
              // method override field
              const methodInput = document.createElement('input');
              methodInput.type = 'hidden';
              methodInput.name = '_method';
              methodInput.value = 'DELETE';
              form.appendChild(methodInput);
              document.body.appendChild(form);
              console.log('Submitting fallback form to', retryUrl);
              form.submit();
              return;
            }catch(err){ console.warn('Form fallback failed', err); }
          }
        }
      }
    }catch(e){
      console.error('Error during delete request', e);
      AdvancedUI.showToast('Error deleting: '+(e && e.message ? e.message : e), 'error');
    }finally{
      // stop loading and keep modal open for user to retry unless deletion succeeded and a reload was scheduled
      try{ if (stopLoading) stopLoading(); }catch(e){}
      // clear pending code only if page will reload soon; otherwise leave it so user can retry
      _pendingDeleteCode = null;
      // close modal visually after short delay so user sees result
      setTimeout(() => { try{ window.closeDeleteModal(); }catch(e){} }, 500);
    }
  };

  // Admin menu open/close + keyboard navigation
  window.toggleAdminMenu = function(event){
    const button = event.currentTarget;
    const menu = document.getElementById('adminMenu');
    if (!button || !menu) return;
    const isExpanded = button.getAttribute('aria-expanded') === 'true';
    button.setAttribute('aria-expanded', !isExpanded);
    menu.hidden = isExpanded;
    if (!isExpanded){
      document.addEventListener('click', function closeMenu(e){ if (!button.contains(e.target) && !menu.contains(e.target)){ button.setAttribute('aria-expanded','false'); menu.hidden = true; document.removeEventListener('click', closeMenu); } });
    }
  };

  // Programmatic bindings for admin controls (works if inline onclick is blocked)
  function attachAdminControls(){
    const adminBadge = document.querySelector('.admin-badge');
    const menu = document.getElementById('adminMenu');
    if (!adminBadge || !menu) return;

    // ensure defaults
    if (!adminBadge.hasAttribute('aria-expanded')) adminBadge.setAttribute('aria-expanded','false');
    if (menu.hidden === undefined) menu.hidden = true;

    // toggle via JS (use existing global if available)
    adminBadge.addEventListener('click', (e) => {
      e.stopPropagation();
      try { window.toggleAdminMenu({ currentTarget: adminBadge }); } catch(err){
        const isExpanded = adminBadge.getAttribute('aria-expanded') === 'true';
        adminBadge.setAttribute('aria-expanded', String(!isExpanded));
        menu.hidden = isExpanded;
      }
    });

    // Keep menu open while interacting with it
    menu.addEventListener('click', (e) => { e.stopPropagation(); });

    // Bind menu actions
    Array.from(menu.querySelectorAll('.menu-item, .theme-toggle')).forEach(item => {
      // skip anchors that navigate normally
      if (item.tagName.toLowerCase() === 'a' && item.getAttribute('href')) return;

      item.addEventListener('click', (ev) => {
        ev.preventDefault();
        ev.stopPropagation();

        // theme toggle
        if (item.classList.contains('theme-toggle') || item.dataset.action === 'toggleTheme'){
          if (typeof window.toggleTheme === 'function') window.toggleTheme();
          return;
        }

        // save menu order
        if (/save menu order/i.test(item.textContent || '')){
          if (typeof window.saveMenuOrder === 'function') window.saveMenuOrder();
          return;
        }

        // generic data-action -> call window[action]
        const action = item.dataset.action;
        if (action && typeof window[action] === 'function'){
          try{ window[action](ev); }catch(err){ console.warn('action handler failed', action, err); }
          return;
        }

        // fallback: if element has data-event-code attribute and openDeleteModal exists
        if (item.dataset.eventCode && typeof window.openDeleteModal === 'function'){
          window.openDeleteModal(item);
          return;
        }
      });
    });
  }

  document.addEventListener('keydown', function(e){
    // global shortcuts
    if (e.ctrlKey || e.metaKey){
      if (e.key === 'b'){ e.preventDefault(); window.toggleTheme(); }
      if (e.key === 'k'){
        const adminBadge = document.querySelector('.admin-badge');
        const adminMenu = document.getElementById('adminMenu');
        if (!adminBadge || !adminMenu) return;
        e.preventDefault();
        if (adminMenu.hidden){
          adminBadge.setAttribute('aria-expanded','true');
          adminMenu.hidden = false;
          const first = adminMenu.querySelector('.menu-item');
          if (first) first.focus();
        } else {
          adminBadge.setAttribute('aria-expanded','false');
          adminMenu.hidden = true;
          adminBadge.focus();
        }
      }
    }
  });

  // keyboard nav inside menu
  document.addEventListener('keydown', function(e){
    const menu = document.getElementById('adminMenu'); if (!menu || menu.hidden) return;
    const items = menu.querySelectorAll('.menu-item'); const current = document.activeElement; let idx = Array.from(items).indexOf(current);
    switch(e.key){
      case 'ArrowDown': e.preventDefault(); if (idx < items.length-1) items[idx+1].focus(); else items[0].focus(); break;
      case 'ArrowUp': e.preventDefault(); if (idx > 0) items[idx-1].focus(); else items[items.length-1].focus(); break;
      case 'Escape': e.preventDefault(); const button = document.querySelector('.admin-badge'); if (button){ button.setAttribute('aria-expanded','false'); menu.hidden = true; button.focus(); } break;
    }
  });

  // Enhanced form validation
  function validateEventForm(form) {
    const startDate = form.querySelector('#eventStart')?.value;
    const endDate = form.querySelector('#eventEnd')?.value;
    const eventCode = form.querySelector('#eventCode')?.value;
    const errors = [];

    if (startDate && endDate && new Date(startDate) >= new Date(endDate)) {
      errors.push('End date must be after start date');
    }

    if (eventCode && !/^[A-Z][0-9]{4}$/.test(eventCode)) {
      errors.push('Event code must be 1 capital letter followed by 4 numbers');
    }

    return errors;
  }

  // Form autosave functionality
  function enableFormAutosave(form, delay = 2000) {
    let timeout;
    const inputs = form.querySelectorAll('input, select, textarea');
    const saveIndicator = document.createElement('div');
    saveIndicator.className = 'autosave-indicator';
    form.appendChild(saveIndicator);

    inputs.forEach(input => {
      input.addEventListener('input', () => {
        clearTimeout(timeout);
        saveIndicator.textContent = 'Changes not saved...';
        saveIndicator.className = 'autosave-indicator pending';

        timeout = setTimeout(async () => {
          try {
            saveIndicator.textContent = 'Saving...';
            const formData = new FormData(form);
            const response = await fetch(form.action, {
              method: 'POST',
              body: formData,
              headers: readCsrfHeaders()
            });
            
            if (response.ok) {
              saveIndicator.textContent = 'Changes saved';
              saveIndicator.className = 'autosave-indicator saved';
              setTimeout(() => saveIndicator.className = 'autosave-indicator', 2000);
            } else {
              throw new Error('Save failed');
            }
          } catch (err) {
            saveIndicator.textContent = 'Failed to save';
            saveIndicator.className = 'autosave-indicator error';
          }
        }, delay);
      });
    });
  }

  // Mobile menu enhancements
  function enableMobileMenu() {
    const menuBtn = document.querySelector('.mobile-menu-btn');
    const menu = document.querySelector('.admin-menu');
    
    if (menuBtn && menu) {
      menuBtn.addEventListener('click', () => {
        menu.classList.toggle('show');
        document.body.classList.toggle('menu-open');
      });

      // Close menu on outside click
      document.addEventListener('click', (e) => {
        if (!menu.contains(e.target) && !menuBtn.contains(e.target)) {
          menu.classList.remove('show');
          document.body.classList.remove('menu-open');
        }
      });

      // Handle swipe gestures
      let touchStart = null;
      document.addEventListener('touchstart', (e) => {
        touchStart = e.touches[0].clientX;
      }, false);

      document.addEventListener('touchmove', (e) => {
        if (!touchStart) return;
        
        const touchEnd = e.touches[0].clientX;
        const diff = touchStart - touchEnd;

        if (Math.abs(diff) > 50) { // Min swipe distance
          if (diff > 0) { // Swipe left
            menu.classList.remove('show');
            document.body.classList.remove('menu-open');
          } else { // Swipe right
            menu.classList.add('show');
            document.body.classList.add('menu-open');
          }
          touchStart = null;
        }
      }, false);
    }
  }

  class EventWizard {
    constructor(root, options = {}) {
      this.root = root || document.getElementById('event-wizard');
      if (!this.root) return;
      this.options = options;
      this.isStandalone = !!options.standalone;
      this.trigger = options.trigger || document.getElementById('open-event-wizard');

      this.eventForm = this.root.querySelector('#wizard-event-form');
      this.vendorContainer = this.root.querySelector('#wizard-vendors');
      this.menuContainer = this.root.querySelector('#wizard-menus');
      this.stepEls = Array.from(this.root.querySelectorAll('.wizard-step'));
      this.panels = Array.from(this.root.querySelectorAll('.wizard-panel'));
      this.backBtn = this.root.querySelector('[data-action="back"]');
      this.nextBtn = this.root.querySelector('[data-action="next"]');
      this.submitBtn = this.root.querySelector('[data-action="submit"]');
      this.addVendorBtn = this.root.querySelector('[data-action="add-vendor"]');
      this.closeBtn = this.root.querySelector('[data-action="close"]');
      this.currentStep = 1;
      this.maxStep = 3;
      this.vendorCounter = 0;
      this.submitting = false;

      if (!this.isStandalone) {
        if (this.trigger) {
          this.trigger.addEventListener('click', () => this.open());
        }
        if (this.closeBtn) this.closeBtn.addEventListener('click', () => this.close());
        this.root.addEventListener('click', (e) => {
          if (e.target === this.root) this.close();
        });
      }

      if (this.backBtn) this.backBtn.addEventListener('click', () => this.prev());
      if (this.nextBtn) this.nextBtn.addEventListener('click', () => this.next());
      if (this.submitBtn) this.submitBtn.addEventListener('click', () => this.submit());
      if (this.addVendorBtn) this.addVendorBtn.addEventListener('click', () => this.addVendor());

      this.renderInitial();
      if (this.isStandalone) {
        this.gotoStep(1);
      }
    }

    open() {
      this.renderInitial();
      this.gotoStep(1);
      if (!this.isStandalone) {
        this.root.hidden = false;
        document.body.classList.add('wizard-open');
      }
    }

    close() {
      if (!this.isStandalone) {
        this.root.hidden = true;
        document.body.classList.remove('wizard-open');
      }
      this.renderInitial();
    }

    renderInitial() {
      if (this.eventForm) this.eventForm.reset();
      this.vendorContainer.innerHTML = '';
      this.menuContainer.innerHTML = '';
      this.vendorCounter = 0;
      const initialVendors = 4;
      for (let i = 0; i < initialVendors; i++) {
        this.addVendor();
      }
    }

    gotoStep(step) {
      this.currentStep = Math.min(Math.max(step, 1), this.maxStep);
      this.stepEls.forEach((el, idx) => el.classList.toggle('active', idx + 1 === this.currentStep));
      this.panels.forEach((panel, idx) => panel.hidden = idx + 1 !== this.currentStep);
      this.backBtn.disabled = this.currentStep === 1;
      this.nextBtn.hidden = this.currentStep === this.maxStep;
      this.submitBtn.hidden = this.currentStep !== this.maxStep;
    }

    next() {
      if (!this.validateCurrentStep()) return;
      if (this.currentStep < this.maxStep) this.gotoStep(this.currentStep + 1);
    }

    prev() {
      if (this.currentStep > 1) this.gotoStep(this.currentStep - 1);
    }

    validateCurrentStep() {
      if (this.currentStep === 1) return this.validateStep1();
      if (this.currentStep === 2) return this.validateStep2();
      if (this.currentStep === 3) return this.validateStep3();
      return true;
    }

    validateStep1() {
      const code = this.eventForm.querySelector('#wizard-event-code')?.value.trim();
      const name = this.eventForm.querySelector('#wizard-event-name')?.value.trim();
      const start = this.eventForm.querySelector('#wizard-event-start')?.value;
      const end = this.eventForm.querySelector('#wizard-event-end')?.value;
      if (!code || !/^[A-Z][0-9]{4}$/.test(code)) {
        AdvancedUI.showToast('Event code must be 1 capital letter followed by 4 digits.', 'warning');
        return false;
      }
      if (!name || name.length < 3) {
        AdvancedUI.showToast('Event name must be at least 3 characters.', 'warning');
        return false;
      }
      if (start && end && new Date(start) >= new Date(end)) {
        AdvancedUI.showToast('End date must be after start date.', 'warning');
        return false;
      }
      return true;
    }

    validateStep2() {
      const vendors = Array.from(this.vendorContainer.querySelectorAll('[data-vendor-id]'));
      if (vendors.length === 0) {
        AdvancedUI.showToast('Add at least one vendor.', 'warning');
        return false;
      }
      if (vendors.length > 8) {
        AdvancedUI.showToast('You can add up to 8 vendors.', 'warning');
        return false;
      }
      for (const vendor of vendors) {
        const nameInput = vendor.querySelector('[data-field="name"]');
        if (!nameInput || !nameInput.value.trim()) {
          nameInput?.focus();
          AdvancedUI.showToast('Each vendor needs a name.', 'warning');
          return false;
        }
      }
      return true;
    }

    validateStep3() {
      const vendors = Array.from(this.vendorContainer.querySelectorAll('[data-vendor-id]'));
      for (const vendor of vendors) {
        const id = vendor.dataset.vendorId;
        const card = this.menuContainer.querySelector(`.wizard-menu-card[data-vendor-id="${id}"]`);
        if (!card) {
          AdvancedUI.showToast('Missing menu section for one vendor.', 'warning');
          return false;
        }
        const rows = Array.from(card.querySelectorAll('.wizard-menu-row'));
        if (rows.length === 0) {
          AdvancedUI.showToast('Each vendor must have at least one menu item.', 'warning');
          return false;
        }
        if (rows.length > 5) {
          AdvancedUI.showToast('Each vendor can have up to 5 menu items.', 'warning');
          return false;
        }
        for (const row of rows) {
          const nameInput = row.querySelector('[data-field="menu-name"]');
          const priceInput = row.querySelector('[data-field="menu-price"]');
          if (!nameInput.value.trim()) {
            nameInput.focus();
            AdvancedUI.showToast('Menu items need a name.', 'warning');
            return false;
          }
          const price = priceInput.value.trim();
          if (!price || Number(price) < 0) {
            priceInput.focus();
            AdvancedUI.showToast('Menu item price must be zero or positive.', 'warning');
            return false;
          }
        }
      }
      return true;
    }

    addVendor(prefill = {}) {
      if (this.vendorContainer.querySelectorAll('[data-vendor-id]').length >= 8) {
        AdvancedUI.showToast('Maximum 8 vendors allowed.', 'warning');
        return;
      }
      const id = `vendor-${++this.vendorCounter}`;
      const vendorRow = document.createElement('div');
      vendorRow.className = 'wizard-vendor';
      vendorRow.dataset.vendorId = id;
      vendorRow.innerHTML = `
        <div class="input-group">
          <label>Vendor name</label>
          <input type="text" class="field-input" data-field="name" placeholder="e.g. BRGR" value="${prefill.name || ''}" required>
        </div>
        <div class="input-group">
          <label>Pickup PIN (optional)</label>
          <input type="text" class="field-input" data-field="pin" placeholder="1234" value="${prefill.pin || ''}">
        </div>
        <div class="wizard-vendor-actions">
          <button type="button" class="btn-icon" data-action="remove-vendor" title="Remove vendor">&times;</button>
        </div>
      `;
      vendorRow.querySelector('[data-action="remove-vendor"]').addEventListener('click', () => this.removeVendor(id));
      vendorRow.querySelector('[data-field="name"]').addEventListener('input', (e) => this.updateMenuCardTitle(id, e.target.value));
      this.vendorContainer.appendChild(vendorRow);
      this.addMenuCard(id, prefill.menuItems);
    }

    removeVendor(id) {
      const vendors = this.vendorContainer.querySelectorAll('[data-vendor-id]');
      if (vendors.length <= 1) {
        AdvancedUI.showToast('At least one vendor is required.', 'warning');
        return;
      }
      const row = this.vendorContainer.querySelector(`[data-vendor-id="${id}"]`);
      row?.remove();
      const card = this.menuContainer.querySelector(`.wizard-menu-card[data-vendor-id="${id}"]`);
      card?.remove();
    }

    addMenuCard(id, menuItems = []) {
      const card = document.createElement('div');
      card.className = 'wizard-menu-card';
      card.dataset.vendorId = id;
      card.innerHTML = `
        <div class="wizard-menu-card__header">
          <strong data-menu-title>Menu</strong>
          <button type="button" class="btn btn-outline btn-sm" data-action="add-menu-item" data-vendor="${id}">Add item</button>
        </div>
        <div class="wizard-menu-items"></div>
      `;
      card.querySelector('[data-action="add-menu-item"]').addEventListener('click', () => this.addMenuRow(card));
      this.menuContainer.appendChild(card);
      const initialItems = menuItems.length ? menuItems : new Array(3).fill({});
      initialItems.forEach(item => this.addMenuRow(card, item));
      this.updateMenuCardTitle(id, this.vendorContainer.querySelector(`[data-vendor-id="${id}"] [data-field="name"]`)?.value);
    }

    addMenuRow(card, prefill = {}) {
      const rows = card.querySelectorAll('.wizard-menu-row');
      if (rows.length >= 5) {
        AdvancedUI.showToast('Limit of 5 menu items per vendor.', 'warning');
        return;
      }
      const row = document.createElement('div');
      row.className = 'wizard-menu-row';
      row.innerHTML = `
        <input type="text" class="field-input" data-field="menu-name" placeholder="Item name" value="${prefill.name || ''}" required>
        <input type="number" class="field-input" data-field="menu-price" placeholder="Price" min="0" step="0.01" value="${prefill.price || ''}" required>
        <input type="number" class="field-input" data-field="menu-max" placeholder="Max/order (optional)" min="1" value="${prefill.maxPerOrder || ''}">
        <button type="button" class="btn-icon" data-action="remove-menu-item" title="Remove item">&times;</button>
      `;
      row.querySelector('[data-action="remove-menu-item"]').addEventListener('click', () => this.removeMenuRow(card, row));
      card.querySelector('.wizard-menu-items').appendChild(row);
    }

    removeMenuRow(card, row) {
      const rows = card.querySelectorAll('.wizard-menu-row');
      if (rows.length <= 1) {
        AdvancedUI.showToast('Keep at least one menu item per vendor.', 'warning');
        return;
      }
      row.remove();
    }

    updateMenuCardTitle(id, name) {
      const card = this.menuContainer.querySelector(`.wizard-menu-card[data-vendor-id="${id}"] [data-menu-title]`);
      if (card) {
        card.textContent = name?.trim() ? `${name.trim()} menu` : 'Menu';
      }
    }

    collectPayload() {
      const payload = {
        code: this.eventForm.querySelector('#wizard-event-code')?.value.trim(),
        name: this.eventForm.querySelector('#wizard-event-name')?.value.trim(),
        startAt: this.eventForm.querySelector('#wizard-event-start')?.value || null,
        endAt: this.eventForm.querySelector('#wizard-event-end')?.value || null,
        vendors: []
      };
      const vendorRows = Array.from(this.vendorContainer.querySelectorAll('[data-vendor-id]'));
      vendorRows.forEach(row => {
        const id = row.dataset.vendorId;
        const vendor = {
          name: row.querySelector('[data-field="name"]').value.trim(),
          pin: row.querySelector('[data-field="pin"]').value.trim(),
          menuItems: []
        };
        const card = this.menuContainer.querySelector(`.wizard-menu-card[data-vendor-id="${id}"]`);
        const menuRows = card ? Array.from(card.querySelectorAll('.wizard-menu-row')) : [];
        menuRows.forEach(menuRow => {
          const maxRaw = menuRow.querySelector('[data-field="menu-max"]').value.trim();
          const maxValue = maxRaw ? Number.parseInt(maxRaw, 10) : null;
          vendor.menuItems.push({
            name: menuRow.querySelector('[data-field="menu-name"]').value.trim(),
            price: menuRow.querySelector('[data-field="menu-price"]').value.trim(),
            maxPerOrder: Number.isNaN(maxValue) ? null : maxValue
          });
        });
        payload.vendors.push(vendor);
      });
      return payload;
    }

    setSubmitting(flag) {
      this.submitting = flag;
      this.submitBtn.disabled = flag;
      this.submitBtn.textContent = flag ? 'Creating…' : 'Create event';
    }

    async submit() {
      if (this.submitting) return;
      if (!this.validateStep3()) return;
      const payload = this.collectPayload();
      try {
        this.setSubmitting(true);
        const headers = Object.assign({'Content-Type': 'application/json'}, readCsrfHeaders());
        const response = await fetch('/admin/events/wizard', {
          method: 'POST',
          headers,
          body: JSON.stringify(payload)
        });
        const data = await response.json().catch(() => ({}));
        if (!response.ok) {
          throw new Error(data.error || 'Failed to create event.');
        }
        AdvancedUI.showToast(data.message || 'Event created successfully.', 'success');
        this.close();
        setTimeout(() => window.location.reload(), 800);
      } catch (err) {
        AdvancedUI.showToast(err.message || 'Failed to create event.', 'error');
      } finally {
        this.setSubmitting(false);
      }
    }
  }

  // initialize on DOMContentLoaded
  document.addEventListener('DOMContentLoaded', function(){
    try { 
      window.initializeTheme();
      enableMenuDrag();
      enableMobileMenu();
      const wizardModal = document.getElementById('event-wizard');
      if (wizardModal) new EventWizard(wizardModal, { trigger: document.getElementById('open-event-wizard') });
      const wizardPage = document.querySelector('[data-wizard-page]');
      if (wizardPage) new EventWizard(wizardPage, { standalone: true });
      // Attach programmatic admin controls so menu actions work even if inline onclicks are blocked
      try{ attachAdminControls(); }catch(e){ /* no-op if function missing */ }
      
      // Enhanced form handling
      const forms = document.querySelectorAll('form[data-autosave]');
      forms.forEach(form => enableFormAutosave(form));
      
      // Attach confirm button if present
      const confirmBtn = document.getElementById('confirmDeleteBtn');
      if (confirmBtn) {
        confirmBtn.addEventListener('click', window.confirmDelete);
      }

      // Enhanced event form validation
      const evtForm = document.getElementById('eventForm');
      if (evtForm) {
        evtForm.addEventListener('submit', function(e) {
          const errors = validateEventForm(this);
          if (errors.length > 0) {
            e.preventDefault();
            errors.forEach(error => AdvancedUI.showToast(error, 'error'));
          }
        });

        // Add real-time validation
        const inputs = evtForm.querySelectorAll('input, select');
        inputs.forEach(input => {
          input.addEventListener('blur', function() {
            const errors = validateEventForm(evtForm);
            const field = this.id;
            const fieldErrors = errors.filter(err => err.toLowerCase().includes(field.toLowerCase()));
            
            if (fieldErrors.length > 0) {
              this.classList.add('error');
              const hint = this.parentElement.querySelector('.field-hint') || document.createElement('div');
              hint.className = 'field-hint error';
              hint.textContent = fieldErrors[0];
              this.parentElement.appendChild(hint);
            } else {
              this.classList.remove('error');
              const hint = this.parentElement.querySelector('.field-hint');
              if (hint) hint.remove();
            }
          });
        });
      }

      // Add responsive data tables
      const tables = document.querySelectorAll('table.responsive');
      tables.forEach(table => {
        const headers = Array.from(table.querySelectorAll('th')).map(th => th.textContent);
        
        table.querySelectorAll('tr').forEach(tr => {
          tr.querySelectorAll('td').forEach((td, i) => {
            td.setAttribute('data-label', headers[i]);
          });
        });
      });
    } catch(e) {
      console.error('Initialization error:', e);
    }
  });

  // Handle service worker for offline support if available
  if ('serviceWorker' in navigator) {
    window.addEventListener('load', () => {
      navigator.serviceWorker.register('/service-worker.js')
        .then(registration => {
          console.log('ServiceWorker registered');
        })
        .catch(err => {
          console.log('ServiceWorker registration failed:', err);
        });
    });
  }

})();
