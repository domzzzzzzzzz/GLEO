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
      if (e.key === 'k'){ e.preventDefault(); const adminBadge = document.querySelector('.admin-badge'); const adminMenu = document.getElementById('adminMenu'); if (adminMenu.hidden){ adminBadge.setAttribute('aria-expanded','true'); adminMenu.hidden = false; const first = adminMenu.querySelector('.menu-item'); if (first) first.focus(); } else { adminBadge.setAttribute('aria-expanded','false'); adminMenu.hidden = true; adminBadge.focus(); } }
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

  // initialize on DOMContentLoaded
  document.addEventListener('DOMContentLoaded', function(){
    try { 
      window.initializeTheme();
      enableMenuDrag();
      enableMobileMenu();
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
