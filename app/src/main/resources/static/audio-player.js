// Global state
let currentAudioFileName = null;
let animationFrameId = null;
const SKIP_INTERVAL = 15; // 15 seconds

// DOM elements - will be initialized after DOM loads
let mainAudio;
let activePlayer;
let activePlayerTitle;
let playPauseBtn;
let skipBackwardBtn;
let skipForwardBtn;
let speedSelect;
let progressBar;
let progressFill;
let currentTimeDisplay;
let durationDisplay;

/**
 * Initialize DOM element references
 */
function initializeDOMElements() {
    mainAudio = document.getElementById('mainAudio');
    activePlayer = document.getElementById('activePlayer');
    activePlayerTitle = document.getElementById('activePlayerTitle');
    playPauseBtn = document.getElementById('playPauseBtn');
    skipBackwardBtn = document.getElementById('skipBackward');
    skipForwardBtn = document.getElementById('skipForward');
    speedSelect = document.getElementById('speedSelect');
    progressBar = document.getElementById('progressBar');
    progressFill = document.getElementById('progressFill');
    currentTimeDisplay = document.getElementById('currentTime');
    durationDisplay = document.getElementById('duration');
}

/**
 * Format time in MM:SS format
 * @param {number} seconds - Time in seconds
 * @returns {string} Formatted time string
 */
function formatTime(seconds) {
    if (isNaN(seconds) || !isFinite(seconds)) return '0:00';
    const mins = Math.floor(seconds / 60);
    const secs = Math.floor(seconds % 60);
    return `${mins}:${secs.toString().padStart(2, '0')}`;
}

/**
 * Update play/pause button state
 */
function updatePlayPauseButton() {
    playPauseBtn.textContent = mainAudio.paused ? '▶' : '⏸';
}

/**
 * Update progress bar and time display
 */
function updateProgress() {
    if (mainAudio.duration) {
        const percent = (mainAudio.currentTime / mainAudio.duration) * 100;
        progressFill.style.width = percent + '%';
    }
    currentTimeDisplay.textContent = formatTime(mainAudio.currentTime);
}

/**
 * Continuous progress update using requestAnimationFrame
 */
function animateProgress() {
    updateProgress();
    if (!mainAudio.paused && !mainAudio.ended) {
        animationFrameId = requestAnimationFrame(animateProgress);
    }
}

/**
 * Start progress animation loop
 */
function startProgressAnimation() {
    if (animationFrameId) {
        cancelAnimationFrame(animationFrameId);
    }
    if (!mainAudio.paused) {
        animateProgress();
    }
}

/**
 * Stop progress animation loop
 */
function stopProgressAnimation() {
    if (animationFrameId) {
        cancelAnimationFrame(animationFrameId);
        animationFrameId = null;
    }
}

/**
 * Update duration display
 */
function updateDuration() {
    durationDisplay.textContent = formatTime(mainAudio.duration);
}

/**
 * Load and play an audio file
 * @param {string} fileName - The audio file name
 */
function playAudioFile(fileName) {
    const displayName = fileName.split('/').pop();
    const mimeType = getMimeType(fileName);

    // Update UI
    currentAudioFileName = fileName;
    activePlayerTitle.textContent = displayName;
    activePlayer.classList.add('show');

    // Update all play buttons
    updatePlayButtons();

    // Load new audio
    mainAudio.src = `/api/audio/stream/${encodeURIComponent(fileName)}`;
    mainAudio.load();
    mainAudio.play();
}

/**
 * Toggle play/pause for current audio
 */
function togglePlayPause() {
    if (mainAudio.paused) {
        mainAudio.play();
    } else {
        mainAudio.pause();
    }
    updatePlayPauseButton();
}

/**
 * Update all play button states in the list
 */
function updatePlayButtons() {
    document.querySelectorAll('.audio-item').forEach(item => {
        const button = item.querySelector('.audio-item-play-btn');
        const itemFileName = item.dataset.fileName;

        if (itemFileName === currentAudioFileName && !mainAudio.paused) {
            button.textContent = '⏸';
            item.classList.add('playing');
        } else {
            button.textContent = '▶';
            item.classList.remove('playing');
        }
    });
}

/**
 * Get MIME type from filename
 * @param {string} fileName - The audio file name
 * @returns {string} MIME type
 */
function getMimeType(fileName) {
    const lowerFileName = fileName.toLowerCase();

    // Common audio formats
    if (lowerFileName.endsWith('.mp3')) return 'audio/mpeg';
    if (lowerFileName.endsWith('.wav')) return 'audio/wav';
    if (lowerFileName.endsWith('.ogg')) return 'audio/ogg';
    if (lowerFileName.endsWith('.opus')) return 'audio/ogg; codecs="opus"'; // Opus in Ogg container
    if (lowerFileName.endsWith('.m4a')) return 'audio/mp4';
    if (lowerFileName.endsWith('.aac')) return 'audio/aac';
    if (lowerFileName.endsWith('.flac')) return 'audio/flac';
    if (lowerFileName.endsWith('.webm')) return 'audio/webm';
    if (lowerFileName.endsWith('.weba')) return 'audio/webm';
    if (lowerFileName.endsWith('.oga')) return 'audio/ogg';
    if (lowerFileName.endsWith('.mp4')) return 'audio/mp4';
    if (lowerFileName.endsWith('.m4b')) return 'audio/mp4';
    if (lowerFileName.endsWith('.3gp')) return 'audio/3gpp';
    if (lowerFileName.endsWith('.amr')) return 'audio/amr';
    if (lowerFileName.endsWith('.aiff') || lowerFileName.endsWith('.aif')) return 'audio/aiff';
    if (lowerFileName.endsWith('.wma')) return 'audio/x-ms-wma';

    // Default fallback
    return 'audio/mpeg';
}

/**
 * Check if browser can play a given audio format
 * @param {string} mimeType - The MIME type to check
 * @returns {boolean} True if format is supported
 */
function canPlayFormat(mimeType) {
    const testAudio = document.createElement('audio');
    const canPlay = testAudio.canPlayType(mimeType);
    // canPlayType returns '', 'maybe', or 'probably'
    return canPlay !== '';
}

/**
 * Get file extension from filename
 * @param {string} fileName - The audio file name
 * @returns {string} File extension (e.g., 'mp3')
 */
function getFileExtension(fileName) {
    const parts = fileName.toLowerCase().split('.');
    return parts.length > 1 ? parts.pop() : '';
}

/**
 * Escape HTML for safe display
 * @param {string} text - Text to escape
 * @returns {string} Escaped HTML
 */
function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

/**
 * Load audio files from API
 */
async function loadAudioFiles() {
    const loadingDiv = document.getElementById('loading');
    const errorDiv = document.getElementById('error');
    const audioListDiv = document.getElementById('audio-list');

    loadingDiv.style.display = 'block';
    errorDiv.classList.add('hidden');
    audioListDiv.innerHTML = '';

    try {
        const response = await fetch('/api/audio/list');
        const data = await response.json();

        if (data.success && data.files) {
            if (data.files.length === 0) {
                audioListDiv.innerHTML = '<p style="text-align: center; color: #666; padding: 2rem;">No audio files found.</p>';
            } else {
                data.files.forEach(fileName => {
                    const audioItem = document.createElement('div');
                    audioItem.className = 'audio-item';
                    audioItem.dataset.fileName = fileName;

                    const displayName = fileName.split('/').pop();
                    const mimeType = getMimeType(fileName);
                    const isSupported = canPlayFormat(mimeType);
                    const fileExtension = getFileExtension(fileName);

                    const playButton = document.createElement('button');
                    playButton.className = 'audio-item-play-btn';
                    playButton.textContent = '▶';
                    playButton.title = isSupported ? 'Play' : 'Format not supported by your browser';

                    if (!isSupported) {
                        playButton.disabled = true;
                        playButton.style.backgroundColor = '#95a5a6';
                        playButton.style.cursor = 'not-allowed';
                    } else {
                        playButton.onclick = () => {
                            if (currentAudioFileName === fileName && !mainAudio.paused) {
                                mainAudio.pause();
                            } else {
                                playAudioFile(fileName);
                            }
                        };
                    }

                    const contentDiv = document.createElement('div');
                    contentDiv.className = 'audio-item-content';

                    const titleDiv = document.createElement('div');
                    titleDiv.className = 'audio-item-title';
                    titleDiv.textContent = displayName;

                    contentDiv.appendChild(titleDiv);

                    // Add unsupported message if format isn't supported
                    if (!isSupported) {
                        const unsupportedMsg = document.createElement('div');
                        unsupportedMsg.className = 'unsupported-message';
                        unsupportedMsg.textContent = `⚠️ Your browser cannot play .${fileExtension} files`;
                        contentDiv.appendChild(unsupportedMsg);
                    }

                    audioItem.appendChild(playButton);
                    audioItem.appendChild(contentDiv);
                    audioListDiv.appendChild(audioItem);
                });
            }
        } else {
            throw new Error(data.message || 'Failed to load audio files');
        }
    } catch (error) {
        console.error('Error loading audio files:', error);
        errorDiv.textContent = 'Error loading audio files: ' + error.message;
        errorDiv.classList.remove('hidden');
    } finally {
        loadingDiv.style.display = 'none';
    }
}

/**
 * Initialize event listeners
 */
function initializeEventListeners() {
    // Main player controls
    playPauseBtn.addEventListener('click', togglePlayPause);

    skipBackwardBtn.addEventListener('click', () => {
        mainAudio.currentTime = Math.max(0, mainAudio.currentTime - SKIP_INTERVAL);
        updateProgress();
    });

    skipForwardBtn.addEventListener('click', () => {
        if (mainAudio.duration) {
            mainAudio.currentTime = Math.min(mainAudio.duration, mainAudio.currentTime + SKIP_INTERVAL);
        } else {
            mainAudio.currentTime += SKIP_INTERVAL;
        }
        updateProgress();
    });

    speedSelect.addEventListener('change', (e) => {
        mainAudio.playbackRate = parseFloat(e.target.value);
    });

    progressBar.addEventListener('click', (e) => {
        if (mainAudio.duration) {
            const rect = progressBar.getBoundingClientRect();
            const percent = (e.clientX - rect.left) / rect.width;
            mainAudio.currentTime = percent * mainAudio.duration;
            updateProgress();
        }
    });

    // Audio event listeners
    mainAudio.addEventListener('loadedmetadata', () => {
        updateDuration();
        updateProgress();
    });

    mainAudio.addEventListener('play', () => {
        updatePlayPauseButton();
        updatePlayButtons();
        startProgressAnimation();
    });

    mainAudio.addEventListener('pause', () => {
        updatePlayPauseButton();
        updatePlayButtons();
        stopProgressAnimation();
        updateProgress();
    });

    mainAudio.addEventListener('ended', () => {
        playPauseBtn.textContent = '▶';
        mainAudio.currentTime = 0;
        stopProgressAnimation();
        updateProgress();
        updatePlayButtons();
    });
}

/**
 * Initialize the application
 */
function initialize() {
    initializeDOMElements();
    initializeEventListeners();
    loadAudioFiles();
    updatePlayPauseButton();
    updateProgress();
}

// Run initialization when DOM is ready
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initialize);
} else {
    // DOM already loaded
    initialize();
}