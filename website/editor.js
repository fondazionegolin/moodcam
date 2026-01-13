/**
 * MoodCam Web Profile Editor
 * Real-time image manipulation with Canvas API
 */

class ProfileEditor {
    constructor() {
        this.canvas = null;
        this.ctx = null;
        this.originalImage = null;
        this.imageData = null;

        // Default parameters matching FilmPreset structure
        this.params = {
            exposureEV: 0,
            contrast: 1,
            saturation: 1,
            vibrance: 0,
            fade: 0,
            highlights: 0,
            midtones: 0,
            shadows: 0,
            temperatureK: 6500,
            tint: 0,
            grain: {
                amount: 0,
                size: 1,
                roughness: 0.5
            }
        };

        this.isOpen = false;
        this.presetName = 'My Custom Preset';
    }

    init() {
        this.createModal();
        this.loadSampleImage();
        this.bindEvents();
    }

    createModal() {
        const modal = document.createElement('div');
        modal.id = 'editorModal';
        modal.className = 'editor-modal';
        modal.innerHTML = `
            <div class="editor-container">
                <div class="editor-header">
                    <h2>✨ Create Film Profile</h2>
                    <button class="editor-close" id="editorClose">&times;</button>
                </div>
                
                <div class="editor-body">
                    <div class="editor-preview">
                        <canvas id="editorCanvas"></canvas>
                    </div>
                    
                    <div class="editor-controls">
                        <div class="editor-section">
                            <h3>📸 Base Adjustments</h3>
                            
                            <div class="control-group">
                                <label>Exposure EV</label>
                                <input type="range" id="exposureEV" min="-2" max="2" step="0.1" value="0">
                                <span class="value-display" id="exposureEVValue">0</span>
                            </div>
                            
                            <div class="control-group">
                                <label>Contrast</label>
                                <input type="range" id="contrast" min="0.5" max="2" step="0.05" value="1">
                                <span class="value-display" id="contrastValue">1.0</span>
                            </div>
                            
                            <div class="control-group">
                                <label>Saturation</label>
                                <input type="range" id="saturation" min="0" max="2" step="0.05" value="1">
                                <span class="value-display" id="saturationValue">1.0</span>
                            </div>
                            
                            <div class="control-group">
                                <label>Vibrance</label>
                                <input type="range" id="vibrance" min="-1" max="1" step="0.05" value="0">
                                <span class="value-display" id="vibranceValue">0</span>
                            </div>
                            
                            <div class="control-group">
                                <label>Fade</label>
                                <input type="range" id="fade" min="0" max="0.5" step="0.02" value="0">
                                <span class="value-display" id="fadeValue">0</span>
                            </div>
                        </div>
                        
                        <div class="editor-section">
                            <h3>🌡️ White Balance</h3>
                            
                            <div class="control-group">
                                <label>Temperature (K)</label>
                                <input type="range" id="temperatureK" min="2500" max="10000" step="100" value="6500">
                                <span class="value-display" id="temperatureKValue">6500K</span>
                            </div>
                            
                            <div class="control-group">
                                <label>Tint</label>
                                <input type="range" id="tint" min="-1" max="1" step="0.05" value="0">
                                <span class="value-display" id="tintValue">0</span>
                            </div>
                        </div>
                        
                        <div class="editor-section">
                            <h3>🎬 Tone Curve</h3>
                            
                            <div class="control-group">
                                <label>Highlights</label>
                                <input type="range" id="highlights" min="-1" max="1" step="0.05" value="0">
                                <span class="value-display" id="highlightsValue">0</span>
                            </div>
                            
                            <div class="control-group">
                                <label>Midtones</label>
                                <input type="range" id="midtones" min="-1" max="1" step="0.05" value="0">
                                <span class="value-display" id="midtonesValue">0</span>
                            </div>
                            
                            <div class="control-group">
                                <label>Shadows</label>
                                <input type="range" id="shadows" min="-1" max="1" step="0.05" value="0">
                                <span class="value-display" id="shadowsValue">0</span>
                            </div>
                        </div>
                        
                        <div class="editor-section">
                            <h3>🎞️ Film Grain</h3>
                            
                            <div class="control-group">
                                <label>Amount</label>
                                <input type="range" id="grainAmount" min="0" max="0.5" step="0.02" value="0">
                                <span class="value-display" id="grainAmountValue">0</span>
                            </div>
                            
                            <div class="control-group">
                                <label>Size</label>
                                <input type="range" id="grainSize" min="0.5" max="3" step="0.1" value="1">
                                <span class="value-display" id="grainSizeValue">1.0</span>
                            </div>
                        </div>
                    </div>
                </div>
                
                <div class="editor-footer">
                    <div class="preset-name-input">
                        <label>Profile Name:</label>
                        <input type="text" id="presetNameInput" value="My Custom Preset" placeholder="Enter profile name...">
                    </div>
                    <div class="editor-actions">
                        <button class="btn btn-secondary" id="resetBtn">↺ Reset</button>
                        <button class="btn btn-primary" id="saveBtn">💾 Save Profile</button>
                    </div>
                </div>
            </div>
        `;

        document.body.appendChild(modal);
        this.modal = modal;
        this.canvas = document.getElementById('editorCanvas');
        this.ctx = this.canvas.getContext('2d');
    }

    loadSampleImage() {
        const img = new Image();
        img.crossOrigin = 'anonymous';
        // Use a sample image from the gallery or a default
        img.src = 'screenshot_editor.jpg';

        img.onload = () => {
            // Set canvas size to match image (scaled down for performance)
            const maxWidth = 600;
            const scale = Math.min(1, maxWidth / img.width);
            this.canvas.width = img.width * scale;
            this.canvas.height = img.height * scale;

            this.ctx.drawImage(img, 0, 0, this.canvas.width, this.canvas.height);
            this.originalImage = this.ctx.getImageData(0, 0, this.canvas.width, this.canvas.height);
            this.imageData = new ImageData(
                new Uint8ClampedArray(this.originalImage.data),
                this.originalImage.width,
                this.originalImage.height
            );
        };

        img.onerror = () => {
            // Fallback: create gradient image
            this.canvas.width = 400;
            this.canvas.height = 300;
            const gradient = this.ctx.createLinearGradient(0, 0, 400, 300);
            gradient.addColorStop(0, '#264653');
            gradient.addColorStop(0.5, '#e9c46a');
            gradient.addColorStop(1, '#f4a261');
            this.ctx.fillStyle = gradient;
            this.ctx.fillRect(0, 0, 400, 300);
            this.originalImage = this.ctx.getImageData(0, 0, 400, 300);
            this.imageData = new ImageData(
                new Uint8ClampedArray(this.originalImage.data),
                400, 300
            );
        };
    }

    bindEvents() {
        // Close button
        document.getElementById('editorClose').addEventListener('click', () => this.close());

        // Click outside to close
        this.modal.addEventListener('click', (e) => {
            if (e.target === this.modal) this.close();
        });

        // Escape key
        document.addEventListener('keydown', (e) => {
            if (e.key === 'Escape' && this.isOpen) this.close();
        });

        // Reset button
        document.getElementById('resetBtn').addEventListener('click', () => this.reset());

        // Save button
        document.getElementById('saveBtn').addEventListener('click', () => this.saveProfile());

        // Bind all sliders
        const sliders = [
            'exposureEV', 'contrast', 'saturation', 'vibrance', 'fade',
            'temperatureK', 'tint', 'highlights', 'midtones', 'shadows',
            'grainAmount', 'grainSize'
        ];

        sliders.forEach(id => {
            const slider = document.getElementById(id);
            if (slider) {
                slider.addEventListener('input', (e) => {
                    this.updateParam(id, parseFloat(e.target.value));
                });
            }
        });

        // Preset name
        document.getElementById('presetNameInput').addEventListener('input', (e) => {
            this.presetName = e.target.value;
        });
    }

    updateParam(id, value) {
        // Update display
        const displayEl = document.getElementById(id + 'Value');
        if (displayEl) {
            if (id === 'temperatureK') {
                displayEl.textContent = value + 'K';
            } else {
                displayEl.textContent = value.toFixed(2);
            }
        }

        // Map to params
        if (id === 'grainAmount') {
            this.params.grain.amount = value;
        } else if (id === 'grainSize') {
            this.params.grain.size = value;
        } else {
            this.params[id] = value;
        }

        // Apply effect
        this.applyEffect();
    }

    applyEffect() {
        if (!this.originalImage) return;

        // Reset to original
        const data = new Uint8ClampedArray(this.originalImage.data);

        for (let i = 0; i < data.length; i += 4) {
            let r = data[i];
            let g = data[i + 1];
            let b = data[i + 2];

            // Convert to 0-1 range
            r /= 255;
            g /= 255;
            b /= 255;

            // Exposure
            const expMult = Math.pow(2, this.params.exposureEV);
            r *= expMult;
            g *= expMult;
            b *= expMult;

            // Temperature (simplified Kelvin to RGB shift)
            const tempShift = (this.params.temperatureK - 6500) / 10000;
            r += tempShift * 0.5;
            b -= tempShift * 0.5;

            // Tint (green-magenta axis)
            g += this.params.tint * 0.1;
            r -= this.params.tint * 0.05;
            b -= this.params.tint * 0.05;

            // Convert to HSL for saturation/vibrance
            const max = Math.max(r, g, b);
            const min = Math.min(r, g, b);
            let l = (max + min) / 2;
            let s = 0;
            let h = 0;

            if (max !== min) {
                const d = max - min;
                s = l > 0.5 ? d / (2 - max - min) : d / (max + min);

                switch (max) {
                    case r: h = ((g - b) / d + (g < b ? 6 : 0)) / 6; break;
                    case g: h = ((b - r) / d + 2) / 6; break;
                    case b: h = ((r - g) / d + 4) / 6; break;
                }
            }

            // Saturation
            s *= this.params.saturation;

            // Vibrance (boost low-saturation more)
            const vibranceBoost = (1 - s) * this.params.vibrance;
            s = Math.min(1, s + vibranceBoost);

            // Tone adjustments
            if (l > 0.7) l += this.params.highlights * 0.1;
            else if (l > 0.3) l += this.params.midtones * 0.1;
            else l += this.params.shadows * 0.1;

            // Convert back to RGB
            let c = (1 - Math.abs(2 * l - 1)) * s;
            let x = c * (1 - Math.abs((h * 6) % 2 - 1));
            let m = l - c / 2;

            if (h < 1 / 6) { r = c; g = x; b = 0; }
            else if (h < 2 / 6) { r = x; g = c; b = 0; }
            else if (h < 3 / 6) { r = 0; g = c; b = x; }
            else if (h < 4 / 6) { r = 0; g = x; b = c; }
            else if (h < 5 / 6) { r = x; g = 0; b = c; }
            else { r = c; g = 0; b = x; }

            r += m; g += m; b += m;

            // Contrast
            r = (r - 0.5) * this.params.contrast + 0.5;
            g = (g - 0.5) * this.params.contrast + 0.5;
            b = (b - 0.5) * this.params.contrast + 0.5;

            // Fade (lift blacks)
            r = r * (1 - this.params.fade) + this.params.fade;
            g = g * (1 - this.params.fade) + this.params.fade;
            b = b * (1 - this.params.fade) + this.params.fade;

            // Grain
            if (this.params.grain.amount > 0) {
                const noise = (Math.random() - 0.5) * this.params.grain.amount;
                r += noise;
                g += noise;
                b += noise;
            }

            // Clamp and convert back to 0-255
            data[i] = Math.max(0, Math.min(255, r * 255));
            data[i + 1] = Math.max(0, Math.min(255, g * 255));
            data[i + 2] = Math.max(0, Math.min(255, b * 255));
        }

        this.imageData = new ImageData(data, this.originalImage.width, this.originalImage.height);
        this.ctx.putImageData(this.imageData, 0, 0);
    }

    reset() {
        // Reset all sliders
        document.getElementById('exposureEV').value = 0;
        document.getElementById('contrast').value = 1;
        document.getElementById('saturation').value = 1;
        document.getElementById('vibrance').value = 0;
        document.getElementById('fade').value = 0;
        document.getElementById('temperatureK').value = 6500;
        document.getElementById('tint').value = 0;
        document.getElementById('highlights').value = 0;
        document.getElementById('midtones').value = 0;
        document.getElementById('shadows').value = 0;
        document.getElementById('grainAmount').value = 0;
        document.getElementById('grainSize').value = 1;

        // Reset displays
        this.params = {
            exposureEV: 0, contrast: 1, saturation: 1, vibrance: 0, fade: 0,
            highlights: 0, midtones: 0, shadows: 0, temperatureK: 6500, tint: 0,
            grain: { amount: 0, size: 1, roughness: 0.5 }
        };

        // Update all displays
        ['exposureEV', 'contrast', 'saturation', 'vibrance', 'fade', 'temperatureK', 'tint',
            'highlights', 'midtones', 'shadows', 'grainAmount', 'grainSize'].forEach(id => {
                const el = document.getElementById(id + 'Value');
                const slider = document.getElementById(id);
                if (el && slider) {
                    el.textContent = id === 'temperatureK' ? slider.value + 'K' : parseFloat(slider.value).toFixed(2);
                }
            });

        // Redraw original
        if (this.originalImage) {
            this.ctx.putImageData(this.originalImage, 0, 0);
        }
    }

    saveProfile() {
        const name = this.presetName.trim() || 'Custom Preset';

        // Build preset JSON matching Android FilmPreset structure
        const preset = {
            id: 'custom_' + Date.now(),
            name: name,
            type: 'custom',
            version: 1,
            params: {
                exposureEV: this.params.exposureEV,
                contrast: this.params.contrast,
                fade: this.params.fade,
                saturation: this.params.saturation,
                vibrance: this.params.vibrance,
                highlights: this.params.highlights,
                midtones: this.params.midtones,
                shadows: this.params.shadows,
                temperatureK: this.params.temperatureK,
                tint: this.params.tint,
                curves: {
                    lutResolution: 256,
                    lumaPoints: [[0, 0], [0.25, 0.25], [0.5, 0.5], [0.75, 0.75], [1, 1]],
                    rPoints: [[0, 0], [0.5, 0.5], [1, 1]],
                    gPoints: [[0, 0], [0.5, 0.5], [1, 1]],
                    bPoints: [[0, 0], [0.5, 0.5], [1, 1]]
                },
                grain: {
                    amount: this.params.grain.amount,
                    size: this.params.grain.size,
                    roughness: 0.5,
                    colorVariation: 0.3
                },
                effects: {
                    vignette: 0,
                    vignetteColor: [0, 0, 0],
                    halation: 0,
                    bloom: 0
                }
            },
            createdAt: Date.now(),
            updatedAt: Date.now()
        };

        // Download as JSON
        const blob = new Blob([JSON.stringify(preset, null, 2)], { type: 'application/json' });
        const url = URL.createObjectURL(blob);

        const a = document.createElement('a');
        a.href = url;
        a.download = `${name.replace(/\s+/g, '_')}_preset.json`;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);

        // Show success feedback
        alert(`✅ Profile "${name}" saved!\n\nImport it into MoodCam by opening the JSON file in the app.`);
    }

    open() {
        this.modal.classList.add('show');
        document.body.classList.add('modal-open');
        this.isOpen = true;
        this.loadSampleImage();
    }

    close() {
        this.modal.classList.remove('show');
        document.body.classList.remove('modal-open');
        this.isOpen = false;
    }
}

// Initialize editor on page load
let profileEditor;
document.addEventListener('DOMContentLoaded', () => {
    profileEditor = new ProfileEditor();
    profileEditor.init();
});

// Global function to open editor
function openProfileEditor() {
    if (profileEditor) {
        profileEditor.open();
    }
}
