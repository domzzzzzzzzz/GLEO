// Centralized admin dashboard behaviors: theme, preferences, menu reorder, modals, admin menu keyboard nav
(function(){
  'use strict';

  const AdvancedUI = window.AdvancedUI || { showToast: (m,t)=>console.log(t||'info',m) };

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
    modal.style.display = 'block';
    modal.setAttribute('aria-hidden','false');
    const btn = document.getElementById('confirmDeleteBtn'); if (btn) btn.focus();
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
    const code = _pendingDeleteCode;
    try{
      const headers = Object.assign({'Content-Type':'application/x-www-form-urlencoded'}, readCsrfHeaders());
      const resp = await fetch(`/admin/events/${encodeURIComponent(code)}/delete`, { method: 'POST', headers, body: '' });
      if (resp.ok) window.location.reload(); else {
        const txt = await resp.text(); AdvancedUI.showToast('Failed to delete: '+(txt||resp.statusText),'error');
      }
    }catch(e){ AdvancedUI.showToast('Error deleting: '+e.message,'error'); }
    finally{ window.closeDeleteModal(); }
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

  // initialize on DOMContentLoaded
  document.addEventListener('DOMContentLoaded', function(){
    try{ window.initializeTheme(); }catch(e){}
    enableMenuDrag();
    // attach confirm button if present
    const confirmBtn = document.getElementById('confirmDeleteBtn'); if (confirmBtn) confirmBtn.addEventListener('click', window.confirmDelete);
    // attach event form validation
    const evtForm = document.getElementById('eventForm');
    if (evtForm){
      evtForm.addEventListener('submit', function(e){
        const startDate = document.getElementById('eventStart')?.value;
        const endDate = document.getElementById('eventEnd')?.value;
        if (startDate && endDate && new Date(startDate) >= new Date(endDate)){
          e.preventDefault();
          try{ AdvancedUI.showToast('End date must be after start date', 'error'); }catch(err){ alert('End date must be after start date'); }
        }
      });
    }
  });

})();
