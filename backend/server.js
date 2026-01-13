/**
 * MoodCam Backend Server
 * Handles photo gallery uploads and preset profiles
 */

const express = require('express');
const cors = require('cors');
const multer = require('multer');
const path = require('path');
const fs = require('fs');
const { v4: uuidv4 } = require('uuid');

const app = express();
const PORT = process.env.PORT || 3000;

// Database setup (SQLite)
const Database = require('better-sqlite3');
const db = new Database(path.join(__dirname, 'moodcam.db'));

// Initialize database tables
db.exec(`
    CREATE TABLE IF NOT EXISTS photos (
        id TEXT PRIMARY KEY,
        device_key TEXT NOT NULL,
        image_path TEXT NOT NULL,
        device TEXT,
        filter_name TEXT,
        username TEXT,
        timestamp INTEGER NOT NULL,
        approved INTEGER DEFAULT 1
    );
    
    CREATE TABLE IF NOT EXISTS device_keys (
        key TEXT PRIMARY KEY,
        created_at INTEGER NOT NULL
    );
    
    CREATE TABLE IF NOT EXISTS profiles (
        id TEXT PRIMARY KEY,
        name TEXT NOT NULL,
        description TEXT,
        tags TEXT,
        preset_json TEXT NOT NULL,
        downloads INTEGER DEFAULT 0,
        created_at INTEGER NOT NULL
    );
`);

// Middleware
app.use(cors());
app.use(express.json());
app.use('/uploads', express.static(path.join(__dirname, 'uploads')));
app.use(express.static(path.join(__dirname, '../website')));

// Ensure uploads directory exists
const uploadsDir = path.join(__dirname, 'uploads');
if (!fs.existsSync(uploadsDir)) {
    fs.mkdirSync(uploadsDir);
}

// Multer config for image uploads
const storage = multer.diskStorage({
    destination: (req, file, cb) => {
        cb(null, path.join(__dirname, 'uploads'));
    },
    filename: (req, file, cb) => {
        const ext = path.extname(file.originalname);
        cb(null, `${uuidv4()}${ext}`);
    }
});

const upload = multer({
    storage,
    limits: { fileSize: 10 * 1024 * 1024 }, // 10MB max
    fileFilter: (req, file, cb) => {
        const allowedTypes = /jpeg|jpg|png|webp/;
        const extname = allowedTypes.test(path.extname(file.originalname).toLowerCase());
        const mimetype = allowedTypes.test(file.mimetype);

        if (extname && mimetype) {
            cb(null, true);
        } else {
            cb(new Error('Only image files are allowed'));
        }
    }
});

// Random username generator
const adjectives = ['Happy', 'Sunny', 'Misty', 'Golden', 'Silver', 'Velvet', 'Smoky', 'Dusty', 'Faded', 'Vivid', 'Mellow', 'Rustic'];
const nouns = ['Lens', 'Frame', 'Grain', 'Film', 'Shutter', 'Focus', 'Light', 'Shadow', 'Pixel', 'Mood', 'Tone', 'Shot'];

function generateRandomUsername() {
    const adj = adjectives[Math.floor(Math.random() * adjectives.length)];
    const noun = nouns[Math.floor(Math.random() * nouns.length)];
    const num = Math.floor(Math.random() * 999);
    return `${adj}${noun}${num}`;
}

// ============================================
// API Routes
// ============================================

// Validate device key
function validateDeviceKey(key) {
    if (!key || key.length < 32) return false;

    const existing = db.prepare('SELECT key FROM device_keys WHERE key = ?').get(key);
    if (existing) return true;

    // Register new key
    db.prepare('INSERT INTO device_keys (key, created_at) VALUES (?, ?)').run(key, Date.now());
    return true;
}

// Upload photo
app.post('/api/upload', upload.single('photo'), (req, res) => {
    try {
        const { device_key, device, filter_name, username } = req.body;

        // Validate device key
        if (!validateDeviceKey(device_key)) {
            if (req.file) fs.unlinkSync(req.file.path);
            return res.status(401).json({ error: 'Invalid device key' });
        }

        if (!req.file) {
            return res.status(400).json({ error: 'No image provided' });
        }

        const photoId = uuidv4();
        const finalUsername = username && username.trim() ? username.trim() : generateRandomUsername();

        db.prepare(`
            INSERT INTO photos (id, device_key, image_path, device, filter_name, username, timestamp)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        `).run(
            photoId,
            device_key,
            req.file.filename,
            device || 'Unknown Device',
            filter_name || 'No Filter',
            finalUsername,
            Date.now()
        );

        res.json({
            success: true,
            photoId,
            username: finalUsername
        });

    } catch (error) {
        console.error('Upload error:', error);
        res.status(500).json({ error: 'Upload failed' });
    }
});

// Get photos (paginated)
app.get('/api/photos', (req, res) => {
    try {
        const page = parseInt(req.query.page) || 1;
        const limit = Math.min(parseInt(req.query.limit) || 12, 50);
        const offset = (page - 1) * limit;

        const photos = db.prepare(`
            SELECT id, device, filter_name as filter, username, timestamp, image_path
            FROM photos
            WHERE approved = 1
            ORDER BY timestamp DESC
            LIMIT ? OFFSET ?
        `).all(limit + 1, offset);

        const hasMore = photos.length > limit;
        if (hasMore) photos.pop();

        const baseUrl = `${req.protocol}://${req.get('host')}`;

        res.json({
            photos: photos.map(p => ({
                id: p.id,
                device: p.device,
                filter: p.filter,
                username: p.username,
                timestamp: p.timestamp,
                imageUrl: `/uploads/${p.image_path}`
            })),
            hasMore,
            page
        });

    } catch (error) {
        console.error('Fetch photos error:', error);
        res.status(500).json({ error: 'Failed to fetch photos' });
    }
});

// Get profiles list
app.get('/api/profiles', (req, res) => {
    try {
        const profiles = db.prepare(`
            SELECT id, name, description, tags, downloads
            FROM profiles
            ORDER BY downloads DESC
        `).all();

        res.json({
            profiles: profiles.map(p => ({
                ...p,
                tags: p.tags ? JSON.parse(p.tags) : []
            }))
        });

    } catch (error) {
        console.error('Fetch profiles error:', error);
        // Return default built-in profiles
        res.json({
            profiles: [
                { id: 'provia', name: 'Provia 100F', description: 'Natural colors with subtle saturation.', tags: ['Natural', 'Subtle'] },
                { id: 'velvia', name: 'Velvia 50', description: 'Vivid, saturated colors for landscapes.', tags: ['Vivid', 'Saturated'] },
                { id: 'portra400', name: 'Portra 400', description: 'Warm skin tones with beautiful rolloff.', tags: ['Portrait', 'Warm'] },
            ]
        });
    }
});

// Get specific profile
app.get('/api/profiles/:id', (req, res) => {
    try {
        const profile = db.prepare('SELECT * FROM profiles WHERE id = ?').get(req.params.id);

        if (profile) {
            // Increment download count
            db.prepare('UPDATE profiles SET downloads = downloads + 1 WHERE id = ?').run(req.params.id);

            res.json(JSON.parse(profile.preset_json));
        } else {
            res.status(404).json({ error: 'Profile not found' });
        }

    } catch (error) {
        console.error('Fetch profile error:', error);
        res.status(500).json({ error: 'Failed to fetch profile' });
    }
});

// Add new profile (admin use)
app.post('/api/profiles', (req, res) => {
    try {
        const { id, name, description, tags, preset } = req.body;

        db.prepare(`
            INSERT OR REPLACE INTO profiles (id, name, description, tags, preset_json, created_at)
            VALUES (?, ?, ?, ?, ?, ?)
        `).run(
            id,
            name,
            description || '',
            JSON.stringify(tags || []),
            JSON.stringify(preset),
            Date.now()
        );

        res.json({ success: true, id });

    } catch (error) {
        console.error('Add profile error:', error);
        res.status(500).json({ error: 'Failed to add profile' });
    }
});

// Health check
app.get('/api/health', (req, res) => {
    res.json({ status: 'ok', timestamp: Date.now() });
});

// Serve website
app.get('/', (req, res) => {
    res.sendFile(path.join(__dirname, '../website/index.html'));
});

app.get('/gallery', (req, res) => {
    res.sendFile(path.join(__dirname, '../website/gallery.html'));
});

app.get('/profiles', (req, res) => {
    res.sendFile(path.join(__dirname, '../website/profiles.html'));
});

// Start server
app.listen(PORT, '0.0.0.0', () => {
    const websitePath = path.join(__dirname, '../website');
    console.log(`Checking website path: ${websitePath}`);
    console.log(`Directory exists? ${fs.existsSync(websitePath)}`);

    console.log(`
╔════════════════════════════════════════╗
║       MoodCam Backend Server           ║
╠════════════════════════════════════════╣
║  🚀 Running on http://localhost:${PORT}   ║
║  📷 Upload endpoint: POST /api/upload  ║
║  🖼️  Gallery: GET /api/photos          ║
║  🎨 Profiles: GET /api/profiles        ║
╚════════════════════════════════════════╝
    `);
});
