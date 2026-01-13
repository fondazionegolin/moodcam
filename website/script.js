// MoodCam Website Scripts

const API_BASE = 'http://localhost:3000/api';

// Load gallery preview on homepage
async function loadGalleryPreview() {
    const preview = document.getElementById('galleryPreview');
    if (!preview) return;
    
    try {
        const response = await fetch(`${API_BASE}/photos?page=1&limit=6`);
        const data = await response.json();
        
        if (data.photos && data.photos.length > 0) {
            preview.innerHTML = data.photos.map(photo => `
                <div class="preview-item">
                    <img src="${photo.imageUrl}" alt="Photo by ${photo.username}" loading="lazy">
                    <div class="overlay">
                        <span>@${photo.username} • ${photo.filter || 'No Filter'}</span>
                    </div>
                </div>
            `).join('');
        } else {
            showPlaceholderGallery(preview);
        }
    } catch (error) {
        showPlaceholderGallery(preview);
    }
}

// Show placeholder gallery when API not available
function showPlaceholderGallery(container) {
    const placeholders = [
        { color: '#A0522D', username: 'filmmaker', filter: 'Portra 400' },
        { color: '#8B7355', username: 'analog_soul', filter: 'Velvia 50' },
        { color: '#5C4033', username: 'grain_lover', filter: 'Classic Chrome' },
        { color: '#D4A574', username: 'sunset_chaser', filter: 'Ektar 100' },
        { color: '#B8956A', username: 'street_shooter', filter: 'Provia 100F' },
        { color: '#3D2B22', username: 'night_owl', filter: 'Astia Soft' },
    ];
    
    container.innerHTML = placeholders.map(p => `
        <div class="preview-item">
            <div style="width: 100%; height: 100%; background: linear-gradient(145deg, ${p.color}, ${adjustColor(p.color, -30)});"></div>
            <div class="overlay">
                <span>@${p.username} • ${p.filter}</span>
            </div>
        </div>
    `).join('');
}

// Adjust color brightness
function adjustColor(hex, amount) {
    const num = parseInt(hex.replace('#', ''), 16);
    const r = Math.max(0, Math.min(255, (num >> 16) + amount));
    const g = Math.max(0, Math.min(255, ((num >> 8) & 0x00FF) + amount));
    const b = Math.max(0, Math.min(255, (num & 0x0000FF) + amount));
    return `rgb(${r}, ${g}, ${b})`;
}

// Smooth scroll for anchor links
document.querySelectorAll('a[href^="#"]').forEach(anchor => {
    anchor.addEventListener('click', function (e) {
        e.preventDefault();
        const target = document.querySelector(this.getAttribute('href'));
        if (target) {
            target.scrollIntoView({ behavior: 'smooth' });
        }
    });
});

// Initialize
document.addEventListener('DOMContentLoaded', () => {
    loadGalleryPreview();
    
    // Add scroll effect to navbar
    const navbar = document.querySelector('.navbar');
    window.addEventListener('scroll', () => {
        if (window.scrollY > 50) {
            navbar.style.boxShadow = '0 2px 20px rgba(0,0,0,0.1)';
        } else {
            navbar.style.boxShadow = 'none';
        }
    });
});
