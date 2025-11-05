document.addEventListener('DOMContentLoaded', function() {
    // Add smooth reveal animations to cards
    const cards = document.querySelectorAll('.card, .roster-card');
    const observerOptions = {
        threshold: 0.1,
        rootMargin: '20px'
    };

    const observer = new IntersectionObserver((entries) => {
        entries.forEach(entry => {
            if (entry.isIntersecting) {
                entry.target.classList.add('animate-in');
                observer.unobserve(entry.target);
            }
        });
    }, observerOptions);

    cards.forEach(card => {
        card.style.opacity = '0';
        observer.observe(card);
    });

    // Enhance form interactions
    const inputs = document.querySelectorAll('.field-input');
    inputs.forEach(input => {
        input.addEventListener('focus', () => {
            input.parentElement.classList.add('input-focus');
        });
        input.addEventListener('blur', () => {
            input.parentElement.classList.remove('input-focus');
        });
    });

    // Add loading states to buttons
    const buttons = document.querySelectorAll('.btn');
    buttons.forEach(button => {
        button.addEventListener('click', function(e) {
            if (this.type === 'submit' && !this.classList.contains('btn-muted')) {
                this.classList.add('loading');
                const originalText = this.innerHTML;
                this.innerHTML = '<span class="loading-spinner"></span> Processing...';
                
                // Reset button after 2 seconds if form doesn't submit
                setTimeout(() => {
                    if (this.classList.contains('loading')) {
                        this.classList.remove('loading');
                        this.innerHTML = originalText;
                    }
                }, 2000);
            }
        });
    });

    // Enhanced dropzone interactions
    const dropzones = document.querySelectorAll('[data-dropzone]');
    dropzones.forEach(dropzone => {
        ['dragenter', 'dragover', 'dragleave', 'drop'].forEach(eventName => {
            dropzone.addEventListener(eventName, preventDefaults, false);
        });

        function preventDefaults(e) {
            e.preventDefault();
            e.stopPropagation();
        }

        ['dragenter', 'dragover'].forEach(eventName => {
            dropzone.addEventListener(eventName, () => {
                dropzone.classList.add('dropzone--active');
            });
        });

        ['dragleave', 'drop'].forEach(eventName => {
            dropzone.addEventListener(eventName, () => {
                dropzone.classList.remove('dropzone--active');
            });
        });
    });

    // Auto-dismiss toasts
    const toasts = document.querySelectorAll('.toast');
    toasts.forEach(toast => {
        setTimeout(() => {
            toast.style.animation = 'slideOut 0.3s ease-in forwards';
            setTimeout(() => {
                toast.remove();
            }, 300);
        }, 5000);
    });

    // Smooth stat counter animation
    const stats = document.querySelectorAll('.stat-value');
    stats.forEach(stat => {
        const finalValue = parseInt(stat.textContent);
        let currentValue = 0;
        const duration = 1000; // 1 second
        const increment = finalValue / (duration / 16); // 60fps

        function updateCounter() {
            currentValue = Math.min(currentValue + increment, finalValue);
            stat.textContent = Math.round(currentValue).toLocaleString();
            
            if (currentValue < finalValue) {
                requestAnimationFrame(updateCounter);
            }
        }

        updateCounter();
    });
});