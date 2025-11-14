// Advanced UI Module
const AdvancedUI = {
    config: {
        animationDuration: 300,
        toastDuration: 5000,
        chartColors: ['#4f46e5', '#06b6d4', '#10b981', '#f59e0b', '#ef4444'],
        chartOptions: {
            responsive: true,
            maintainAspectRatio: false,
            animation: {
                duration: 2000,
                easing: 'easeOutQuart'
            }
        }
    },

    init() {
        this.initializeComponents();
        this.setupEventListeners();
        this.initializeCharts();
        this.initializeAnimations();
    },

    initializeComponents() {
        this.setupAdvancedDropdowns();
        this.setupTooltips();
        this.setupModalSystem();
        this.setupTabSystem();
        this.setupAdvancedForms();
        this.setupDataTables();
    },

    // Simple tooltip system for elements with `data-tooltip`.
    // This prevents errors when components expect tooltips to exist.
    setupTooltips() {
        const tooltipClass = 'advui-tooltip';

        function createTooltip(text) {
            const t = document.createElement('div');
            t.className = tooltipClass;
            t.style.position = 'absolute';
            t.style.padding = '6px 10px';
            t.style.background = 'rgba(0,0,0,0.75)';
            t.style.color = '#fff';
            t.style.borderRadius = '6px';
            t.style.fontSize = '13px';
            t.style.pointerEvents = 'none';
            t.style.zIndex = 9999;
            t.textContent = text;
            return t;
        }

        document.querySelectorAll('[data-tooltip]').forEach(el => {
            let tipEl = null;
            const text = el.dataset.tooltip || el.getAttribute('title') || '';
            if (!text) return;

            el.addEventListener('mouseenter', (e) => {
                tipEl = createTooltip(text);
                document.body.appendChild(tipEl);
                const rect = el.getBoundingClientRect();
                const top = rect.top + window.scrollY - tipEl.offsetHeight - 8;
                const left = rect.left + window.scrollX + (rect.width / 2) - (tipEl.offsetWidth / 2);
                // Position after appending so offsetWidth/Height are available
                tipEl.style.top = (top > 0 ? top : rect.bottom + window.scrollY + 8) + 'px';
                tipEl.style.left = (left > 0 ? left : rect.left + window.scrollX) + 'px';
            });

            el.addEventListener('mouseleave', () => {
                if (tipEl && tipEl.parentNode) tipEl.parentNode.removeChild(tipEl);
                tipEl = null;
            });
        });
    },

    setupEventListeners() {
        document.addEventListener('DOMContentLoaded', () => {
            this.handleResponsiveLayout();
            this.initializeScrollEffects();
            this.setupLazyLoading();
        });

        window.addEventListener('resize', this.debounce(() => {
            this.handleResponsiveLayout();
            this.updateChartsResponsiveness();
        }, 250));
    },

    // Advanced Dropdowns with Search
    setupAdvancedDropdowns() {
        const dropdowns = document.querySelectorAll('.advanced-select');
        dropdowns.forEach(dropdown => {
            const select = dropdown.querySelector('select');
            const search = document.createElement('input');
            search.type = 'text';
            search.placeholder = 'Search...';
            search.className = 'dropdown-search';
            
            const wrapper = document.createElement('div');
            wrapper.className = 'advanced-select-wrapper';
            select.parentNode.insertBefore(wrapper, select);
            wrapper.appendChild(search);
            wrapper.appendChild(select);

            search.addEventListener('input', (e) => {
                const searchText = e.target.value.toLowerCase();
                Array.from(select.options).forEach(option => {
                    const text = option.text.toLowerCase();
                    option.style.display = text.includes(searchText) ? '' : 'none';
                });
            });
        });
    },

    // Advanced Form Validation and Enhancement
    setupAdvancedForms() {
        const forms = document.querySelectorAll('form');
        forms.forEach(form => {
            // Real-time validation
            form.querySelectorAll('input, textarea, select').forEach(field => {
                field.addEventListener('input', () => this.validateField(field));
                field.addEventListener('blur', () => this.validateField(field));
            });

            // Advanced form submission
            form.addEventListener('submit', (e) => {
                if (!this.validateForm(form)) {
                    e.preventDefault();
                    this.showFormErrors(form);
                } else {
                    this.handleFormSubmission(form, e);
                }
            });
        });
    },

    // Advanced Data Tables
    setupDataTables() {
        const tables = document.querySelectorAll('.data-table');
        tables.forEach(table => {
            const wrapper = document.createElement('div');
            wrapper.className = 'table-responsive';
            table.parentNode.insertBefore(wrapper, table);
            wrapper.appendChild(table);

            // Add sorting functionality
            this.addTableSorting(table);
            
            // Add search functionality
            this.addTableSearch(table);
            
            // Add pagination
            this.addTablePagination(table);
        });
    },

    // Table helpers (no-op defaults to avoid errors in pages without real implementations)
    addTableSorting(table) {
        // noop: implement sorting if desired
    },

    addTableSearch(table) {
        // noop: implement search if desired
    },

    addTablePagination(table) {
        // noop: implement pagination if desired
    },

    // Responsive layout handler: reflow components that depend on viewport size
    handleResponsiveLayout() {
        // Example: ensure charts update size after layout change
        this.updateChartsResponsiveness();
        // Potential place to collapse/expand sidebar or adjust grid classes
        // (left intentionally minimal to avoid opinionated UI changes)
    },

    // Chart Initialization
    initializeCharts() {
        const chartElements = document.querySelectorAll('[data-chart]');
        chartElements.forEach(element => {
            const type = element.dataset.chartType;
            const data = JSON.parse(element.dataset.chartData);
            
            this.createChart(element, type, data);
        });
    },

    // Create a chart element when Chart.js is available. If Chart.js is not
    // loaded, this is a no-op to avoid runtime errors in environments without it.
    createChart(element, type, data) {
        if (window.Chart) {
            try {
                // Use provided config options where possible
                const cfg = Object.assign({ type: type, data: data, options: this.config.chartOptions }, {});
                new Chart(element, cfg);
            } catch (e) {
                // If creation fails, don't block the rest of the app
                console.warn('Chart creation failed', e);
            }
        }
    },

    // Ensure charts resize correctly on layout changes. Uses Chart.js helpers
    // when available, otherwise fall back to a harmless window resize event.
    updateChartsResponsiveness() {
        if (window.Chart && typeof window.Chart.helpers === 'object') {
            // Chart.js v3+ will listen to resize; attempt to update existing chart instances
            try {
                // Chart.getChart is v3+ helper to retrieve by canvas id
                if (typeof Chart.getChart === 'function') {
                    // iterate over canvases on the page
                    document.querySelectorAll('canvas').forEach(c => {
                        const cInst = Chart.getChart(c);
                        if (cInst && typeof cInst.resize === 'function') cInst.resize();
                    });
                }
            } catch (e) {
                // ignore
            }
        } else {
            // Fallback: trigger a resize event listeners may react to
            try { window.dispatchEvent(new Event('resize')); } catch (e) { /* ignore */ }
        }
    },

    // Lightweight animations initializer. Prefer GSAP if available, otherwise
    // fall back to IntersectionObserver-driven CSS class toggles for scroll animations.
    initializeAnimations() {
        // If GSAP and ScrollTrigger are available, register plugin (safe-guarded)
        if (window.gsap) {
            try {
                if (window.gsap.registerPlugin && window.ScrollTrigger) {
                    window.gsap.registerPlugin(window.ScrollTrigger);
                }
            } catch (e) {
                // ignore registration errors
            }
        }

        // Fallback: simple scroll-based reveal using IntersectionObserver
        const toReveal = document.querySelectorAll('.animate-on-scroll');
        if (toReveal.length > 0 && 'IntersectionObserver' in window) {
            const observer = new IntersectionObserver((entries) => {
                entries.forEach(entry => {
                    if (entry.isIntersecting) {
                        entry.target.classList.add('in-view');
                        observer.unobserve(entry.target);
                    }
                });
            }, { threshold: 0.15 });

            toReveal.forEach(el => observer.observe(el));
        }
    },

    // Advanced Modal System
    setupModalSystem() {
        const modalTriggers = document.querySelectorAll('[data-modal-trigger]');
        modalTriggers.forEach(trigger => {
            trigger.addEventListener('click', () => {
                const modalId = trigger.dataset.modalTarget;
                this.openModal(modalId);
            });
        });
    },

    // Simple scroll effects initializer (non-blocking fallback for GSAP ScrollTrigger)
    initializeScrollEffects() {
        // If GSAP + ScrollTrigger available, user templates may register animations directly.
        if (window.gsap && window.ScrollTrigger) {
            try {
                // nothing to do here; templates use gsap directly
            } catch (e) { /* ignore */ }
            return;
        }

        // Fallback for counters: animate elements with .stat-value when they enter view
        const counters = document.querySelectorAll('.stat-value');
        if ('IntersectionObserver' in window && counters.length > 0) {
            const obs = new IntersectionObserver((entries) => {
                entries.forEach(entry => {
                    if (entry.isIntersecting) {
                        const el = entry.target;
                        const to = parseInt(el.textContent) || 0;
                        // simple numeric tween
                        let current = 0;
                        const step = Math.max(1, Math.round(to / 40));
                        const iv = setInterval(() => {
                            current += step;
                            if (current >= to) {
                                el.textContent = to;
                                clearInterval(iv);
                            } else {
                                el.textContent = current;
                            }
                        }, 40);
                        obs.unobserve(el);
                    }
                });
            }, { threshold: 0.2 });
            counters.forEach(c => obs.observe(c));
        }
    },

    // Advanced Tab System
    setupTabSystem() {
        const tabContainers = document.querySelectorAll('.tab-container');
        tabContainers.forEach(container => {
            const tabs = container.querySelectorAll('[data-tab]');
            const panels = container.querySelectorAll('[data-tab-panel]');
            
            tabs.forEach(tab => {
                tab.addEventListener('click', () => {
                    const targetId = tab.dataset.tab;
                    this.switchTab(tabs, panels, targetId);
                });
            });
        });
    },

    // Advanced Toast Notification System
    showToast(message, type = 'info', duration = this.config.toastDuration) {
        const toast = document.createElement('div');
        toast.className = `toast toast-${type} animate-in`;
        toast.innerHTML = `
            <div class="toast-content">
                <span class="toast-icon"></span>
                <span class="toast-message">${message}</span>
                <button class="toast-close">&times;</button>
            </div>
            <div class="toast-progress"></div>
        `;

        document.body.appendChild(toast);

        // Progress bar animation
        const progress = toast.querySelector('.toast-progress');
        progress.style.transition = `width ${duration}ms linear`;
        setTimeout(() => progress.style.width = '0%', 10);

        // Auto dismiss
        setTimeout(() => {
            toast.classList.add('animate-out');
            setTimeout(() => toast.remove(), 300);
        }, duration);

        // Close button
        toast.querySelector('.toast-close').addEventListener('click', () => {
            toast.classList.add('animate-out');
            setTimeout(() => toast.remove(), 300);
        });
    },

    // Form helpers used by setupAdvancedForms
    validateForm(form) {
        // Basic validation: rely on browser constraint validation, plus any custom validators
        try {
            return form.checkValidity();
        } catch (e) {
            return true;
        }
    },

    showFormErrors(form) {
        // Highlight invalid fields using browser validity API
        Array.from(form.elements).forEach(el => {
            if (el.willValidate && !el.checkValidity()) {
                el.classList.add('invalid');
                // optionally show title/validation message
            }
        });
    },

    handleFormSubmission(form, event) {
        // Default behaviour: allow submission. If dev wants AJAX, they can override this
        return true;
    },

    // Utility Functions
    debounce(func, wait) {
        let timeout;
        return function executedFunction(...args) {
            const later = () => {
                clearTimeout(timeout);
                func(...args);
            };
            clearTimeout(timeout);
            timeout = setTimeout(later, wait);
        };
    },

    validateField(field) {
        const value = field.value.trim();
        const type = field.type;
        const required = field.required;
        const pattern = field.pattern;
        const minLength = field.minLength;
        const maxLength = field.maxLength;

        let isValid = true;
        let errorMessage = '';

        if (required && !value) {
            isValid = false;
            errorMessage = 'This field is required';
        } else if (pattern && !new RegExp(pattern).test(value)) {
            isValid = false;
            errorMessage = 'Invalid format';
        } else if (minLength && value.length < minLength) {
            isValid = false;
            errorMessage = `Minimum ${minLength} characters required`;
        } else if (maxLength && value.length > maxLength) {
            isValid = false;
            errorMessage = `Maximum ${maxLength} characters allowed`;
        } else if (type === 'email' && value && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)) {
            isValid = false;
            errorMessage = 'Invalid email address';
        }

        this.updateFieldValidationUI(field, isValid, errorMessage);
        return isValid;
    },

    updateFieldValidationUI(field, isValid, errorMessage) {
        const container = field.parentElement;
        const existingError = container.querySelector('.field-error');
        
        if (!isValid) {
            field.classList.add('invalid');
            if (!existingError) {
                const error = document.createElement('div');
                error.className = 'field-error animate-in';
                error.textContent = errorMessage;
                container.appendChild(error);
            }
        } else {
            field.classList.remove('invalid');
            if (existingError) {
                existingError.classList.add('animate-out');
                setTimeout(() => existingError.remove(), 300);
            }
        }
    },

    // Advanced Loading States
    showLoading(element, type = 'spinner') {
        element.classList.add('loading');
        const loader = document.createElement('div');
        loader.className = `loader loader-${type}`;
        element.appendChild(loader);
    },

    hideLoading(element) {
        element.classList.remove('loading');
        const loader = element.querySelector('.loader');
        if (loader) loader.remove();
    },

    // Initialize Lazy Loading
    setupLazyLoading() {
        const images = document.querySelectorAll('img[data-src]');
        const observer = new IntersectionObserver((entries) => {
            entries.forEach(entry => {
                if (entry.isIntersecting) {
                    const img = entry.target;
                    img.src = img.dataset.src;
                    img.removeAttribute('data-src');
                    observer.unobserve(img);
                }
            });
        });

        images.forEach(img => observer.observe(img));
    }
};

// Initialize Advanced UI
document.addEventListener('DOMContentLoaded', () => {
    AdvancedUI.init();
});