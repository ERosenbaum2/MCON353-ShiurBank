let currentSeriesId = null;
let currentRecordingId = null;
let mainAudio = null;
let animationFrameId = null;
let currentUserId = null;
let pendingRemoveUserId = null;
let pendingRemoveUserName = null;
let pendingAddGabbaiUserId = null;
let pendingAddGabbaiUserName = null;
const SKIP_INTERVAL = 15; // 15 seconds

document.addEventListener('DOMContentLoaded', async () => {
  await checkAuthAndShowPage();
  const seriesId = getSeriesIdFromPath();
  if (!seriesId) {
    alert('No series ID provided.');
    return;
  }
  currentSeriesId = seriesId;
  await loadSeriesDetails(seriesId);
  await checkGabbaiStatus(seriesId);
  await loadRecordings();

  // Check if there's a recording parameter to auto-play
  const urlParams = new URLSearchParams(window.location.search);
  const recordingId = urlParams.get('recording');
  if (recordingId) {
    // Wait a bit for recordings to load, then find and play the specified recording
    setTimeout(() => {
      autoPlayRecording(parseInt(recordingId));
    }, 500);
  }

  // Set up file input change handler
  document.getElementById('audio-file').addEventListener('change', handleFileSelect);

  // Initialize main audio element
  mainAudio = document.createElement('audio');
  mainAudio.className = 'hidden-audio';
  mainAudio.preload = 'metadata';
  document.body.appendChild(mainAudio);

  // Set up audio event listeners
  initializeAudioListeners();
});

function getSeriesIdFromPath() {
  const segments = window.location.pathname.split('/').filter(Boolean);
  const last = segments[segments.length - 1];
  const id = Number.parseInt(last, 10);
  return Number.isNaN(id) ? null : id;
}

async function checkAuthAndShowPage() {
  try {
    const response = await fetch('/api/current-user');
    if (!response.ok) throw new Error('Auth check failed');
    const data = await response.json();
    if (!data.loggedIn) {
      window.location.href = '/index.html';
      return;
    }
    currentUserId = data.userId;
    document.getElementById('username-display').textContent = data.username;
    document.getElementById('user-greeting').classList.remove('hidden');
    document.getElementById('series-main').classList.remove('hidden');
  } catch (error) {
    console.error('Error checking auth', error);
    window.location.href = '/index.html';
  }
}

async function handleLogout() {
  try {
    const response = await fetch('/api/logout', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' }
    });
    const data = await response.json();
    if (data.success) {
      window.location.href = '/index.html';
    } else {
      alert('Logout failed. Please try again.');
    }
  } catch (error) {
    console.error('Logout error', error);
    alert('An error occurred during logout.');
  }
}

async function loadSeriesDetails(seriesId) {
  try {
    const resp = await fetch(`/api/series/${seriesId}`);
    if (!resp.ok) throw new Error('Failed to load series');
    const s = await resp.json();

    document.getElementById('series-title').textContent =
      s.displayName || `Series #${s.seriesId}`;
    document.getElementById('series-description').textContent = s.description || '';

    const meta = [];
    if (s.rebbiName) meta.push(`Rebbi: ${s.rebbiName}`);
    if (s.topicName) meta.push(`Topic: ${s.topicName}`);
    if (s.institutionName) meta.push(`Institution: ${s.institutionName}`);
    document.getElementById('series-meta').textContent = meta.join(' | ');
  } catch (error) {
    console.error('Error loading series details', error);
    alert('Could not load this series.');
  }
}

async function checkGabbaiStatus(seriesId) {
  try {
    const resp = await fetch(`/api/series/${seriesId}/is-gabbai`);
    if (!resp.ok) return;
    const data = await resp.json();
    if (data.isGabbai) {
      document.getElementById('upload-shiur-btn').classList.remove('hidden');
      // Load participants and pending participants if user is gabbai
      await loadParticipants(seriesId);
      await loadPendingParticipants(seriesId);
    }
  } catch (error) {
    console.error('Error checking gabbai status', error);
  }
}

// ============ PARTICIPANTS MANAGEMENT ============

async function loadParticipants(seriesId) {
  const section = document.getElementById('participants-section');
  const loadingDiv = document.getElementById('participants-loading');
  const errorDiv = document.getElementById('participants-error');
  const listDiv = document.getElementById('participants-list');

  section.classList.remove('hidden');
  loadingDiv.classList.remove('hidden');
  errorDiv.classList.add('hidden');
  listDiv.innerHTML = '';

  try {
    const resp = await fetch(`/api/series/${seriesId}/participants`);
    if (!resp.ok) throw new Error('Failed to load participants');
    const data = await resp.json();

    loadingDiv.classList.add('hidden');

    if (data.success) {
      if (data.participants && data.participants.length > 0) {
        data.participants.forEach(participant => {
          const item = createParticipantItem(participant);
          listDiv.appendChild(item);
        });
      } else {
        listDiv.innerHTML = '<div class="no-participants">No participants in this series yet.</div>';
      }
    } else {
      throw new Error(data.message || 'Failed to load participants');
    }
  } catch (error) {
    console.error('Error loading participants:', error);
    loadingDiv.classList.add('hidden');
    errorDiv.textContent = 'Error loading participants: ' + error.message;
    errorDiv.classList.remove('hidden');
  }
}

function createParticipantItem(participant) {
  const item = document.createElement('div');
  item.className = 'participant-item';
  item.dataset.userId = participant.userId;

  const info = document.createElement('div');
  info.className = 'participant-info';

  const name = document.createElement('div');
  name.className = 'participant-name';
  name.textContent = participant.fullName;

  const email = document.createElement('div');
  email.className = 'participant-email';
  email.textContent = participant.email;

  info.appendChild(name);
  info.appendChild(email);

  const actions = document.createElement('div');
  actions.className = 'participant-actions';

  // Only show "Add as Gabbai" button if user is not already a gabbai
  if (!participant.isGabbai) {
    const addGabbaiBtn = document.createElement('button');
    addGabbaiBtn.className = 'btn-add-gabbai';
    addGabbaiBtn.textContent = 'Add as Additional Gabbai';
    addGabbaiBtn.onclick = () => openAddGabbaiModal(participant.userId, participant.fullName);
    actions.appendChild(addGabbaiBtn);
  }

  // Don't allow removing yourself
  if (participant.userId !== currentUserId) {
    const removeBtn = document.createElement('button');
    removeBtn.className = 'btn-remove-participant';
    removeBtn.textContent = 'Remove Participant';
    removeBtn.onclick = () => openRemoveParticipantModal(participant.userId, participant.fullName);
    actions.appendChild(removeBtn);
  }

  item.appendChild(info);
  item.appendChild(actions);

  return item;
}

function openRemoveParticipantModal(userId, fullName) {
  pendingRemoveUserId = userId;
  pendingRemoveUserName = fullName;
  document.getElementById('remove-participant-message').textContent =
    `Are you sure you want to remove ${fullName} from this series? This will remove all their associations with this series.`;
  document.getElementById('remove-participant-modal').classList.add('active');
}

function closeRemoveParticipantModal() {
  document.getElementById('remove-participant-modal').classList.remove('active');
  pendingRemoveUserId = null;
  pendingRemoveUserName = null;
}

async function confirmRemoveParticipant() {
  if (!pendingRemoveUserId) return;

  try {
    const response = await fetch(`/api/series/${currentSeriesId}/remove-participant`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ userId: pendingRemoveUserId })
    });

    const data = await response.json();

    if (data.success) {
      // Remove the item from the list
      const item = document.querySelector(`.participant-item[data-user-id="${pendingRemoveUserId}"]`);
      if (item) {
        item.remove();
      }

      // Check if list is now empty
      const listDiv = document.getElementById('participants-list');
      if (listDiv.children.length === 0) {
        listDiv.innerHTML = '<div class="no-participants">No participants in this series yet.</div>';
      }

      closeRemoveParticipantModal();
      alert(`${pendingRemoveUserName} has been removed from the series.`);
    } else {
      alert('Error: ' + (data.message || 'Failed to remove participant'));
    }
  } catch (error) {
    console.error('Error removing participant:', error);
    alert('An error occurred while removing the participant. Please try again.');
  }
}

function openAddGabbaiModal(userId, fullName) {
  pendingAddGabbaiUserId = userId;
  pendingAddGabbaiUserName = fullName;
  document.getElementById('add-gabbai-message').textContent =
    `Are you sure you want to add ${fullName} as an additional gabbai for this series?`;
  document.getElementById('add-gabbai-modal').classList.add('active');
}

function closeAddGabbaiModal() {
  document.getElementById('add-gabbai-modal').classList.remove('active');
  pendingAddGabbaiUserId = null;
  pendingAddGabbaiUserName = null;
}

async function confirmAddGabbai() {
  if (!pendingAddGabbaiUserId) return;

  try {
    const response = await fetch(`/api/series/${currentSeriesId}/add-gabbai`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ userId: pendingAddGabbaiUserId })
    });

    const data = await response.json();

    if (data.success) {
      closeAddGabbaiModal();
      alert(`${pendingAddGabbaiUserName} has been added as a gabbai for this series.`);
      // Reload participants to update the UI (hide "Add as Gabbai" button)
      await loadParticipants(currentSeriesId);
    } else {
      alert('Error: ' + (data.message || 'Failed to add gabbai'));
    }
  } catch (error) {
    console.error('Error adding gabbai:', error);
    alert('An error occurred while adding the gabbai. Please try again.');
  }
}

// ============ PENDING PARTICIPANTS MANAGEMENT ============

async function loadPendingParticipants(seriesId) {
  const section = document.getElementById('pending-participants-section');
  const loadingDiv = document.getElementById('pending-participants-loading');
  const errorDiv = document.getElementById('pending-participants-error');
  const listDiv = document.getElementById('pending-participants-list');

  section.classList.remove('hidden');
  loadingDiv.classList.remove('hidden');
  errorDiv.classList.add('hidden');
  listDiv.innerHTML = '';

  try {
    const resp = await fetch(`/api/series/${seriesId}/pending-participants`);
    if (!resp.ok) throw new Error('Failed to load pending participants');
    const data = await resp.json();

    loadingDiv.classList.add('hidden');

    if (data.success) {
      if (data.pendingParticipants && data.pendingParticipants.length > 0) {
        data.pendingParticipants.forEach(participant => {
          const item = createPendingParticipantItem(participant);
          listDiv.appendChild(item);
        });
      } else {
        listDiv.innerHTML = '<div class="no-pending-participants">No pending participant requests at this time.</div>';
      }
    } else {
      throw new Error(data.message || 'Failed to load pending participants');
    }
  } catch (error) {
    console.error('Error loading pending participants:', error);
    loadingDiv.classList.add('hidden');
    errorDiv.textContent = 'Error loading pending participants: ' + error.message;
    errorDiv.classList.remove('hidden');
  }
}

function createPendingParticipantItem(participant) {
  const item = document.createElement('div');
  item.className = 'pending-participant-item';
  item.dataset.userId = participant.userId;

  const info = document.createElement('div');
  info.className = 'pending-participant-info';

  const name = document.createElement('div');
  name.className = 'pending-participant-name';
  name.textContent = participant.fullName;

  const email = document.createElement('div');
  email.className = 'pending-participant-email';
  email.textContent = participant.email;

  info.appendChild(name);
  info.appendChild(email);

  const actions = document.createElement('div');
  actions.className = 'pending-participant-actions';

  const approveBtn = document.createElement('button');
  approveBtn.className = 'btn-approve';
  approveBtn.textContent = 'Approve';
  approveBtn.onclick = () => approveParticipant(participant.userId);

  const rejectBtn = document.createElement('button');
  rejectBtn.className = 'btn-reject';
  rejectBtn.textContent = 'Reject';
  rejectBtn.onclick = () => rejectParticipant(participant.userId);

  actions.appendChild(approveBtn);
  actions.appendChild(rejectBtn);

  item.appendChild(info);
  item.appendChild(actions);

  return item;
}

async function approveParticipant(userId) {
  if (!confirm('Are you sure you want to approve this participant?')) {
    return;
  }

  try {
    const response = await fetch(`/api/series/${currentSeriesId}/approve-participant`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ userId: userId })
    });

    const data = await response.json();

    if (data.success) {
      // Remove the item from the pending list
      const item = document.querySelector(`.pending-participant-item[data-user-id="${userId}"]`);
      if (item) {
        item.remove();
      }

      // Check if pending list is now empty
      const listDiv = document.getElementById('pending-participants-list');
      if (listDiv.children.length === 0) {
        listDiv.innerHTML = '<div class="no-pending-participants">No pending participant requests at this time.</div>';
      }

      alert('Participant approved successfully!');

      // Reload participants list to show the newly approved participant
      await loadParticipants(currentSeriesId);
    } else {
      alert('Error: ' + (data.message || 'Failed to approve participant'));
    }
  } catch (error) {
    console.error('Error approving participant:', error);
    alert('An error occurred while approving the participant. Please try again.');
  }
}

async function rejectParticipant(userId) {
  if (!confirm('Are you sure you want to reject this participant?')) {
    return;
  }

  try {
    const response = await fetch(`/api/series/${currentSeriesId}/reject-participant`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ userId: userId })
    });

    const data = await response.json();

    if (data.success) {
      // Remove the item from the list
      const item = document.querySelector(`.pending-participant-item[data-user-id="${userId}"]`);
      if (item) {
        item.remove();
      }

      // Check if list is now empty
      const listDiv = document.getElementById('pending-participants-list');
      if (listDiv.children.length === 0) {
        listDiv.innerHTML = '<div class="no-pending-participants">No pending participant requests at this time.</div>';
      }

      alert('Participant rejected.');
    } else {
      alert('Error: ' + (data.message || 'Failed to reject participant'));
    }
  } catch (error) {
    console.error('Error rejecting participant:', error);
    alert('An error occurred while rejecting the participant. Please try again.');
  }
}

// ============ UPLOAD MODAL ============

function openUploadModal() {
  document.getElementById('upload-modal').classList.add('active');
  // Set default date/time to now
  const now = new Date();
  const localDateTime = new Date(now.getTime() - now.getTimezoneOffset() * 60000)
    .toISOString()
    .slice(0, 16);
  document.getElementById('recorded-at').value = localDateTime;
}

function closeUploadModal() {
  document.getElementById('upload-modal').classList.remove('active');
  resetUploadForm();
}

function resetUploadForm() {
  document.getElementById('upload-form').reset();
  document.getElementById('file-name').textContent = '';
  document.getElementById('success-message').classList.remove('active');
  document.getElementById('error-message').classList.remove('active');
  document.getElementById('progress-container').classList.remove('active');
  document.getElementById('progress-bar').style.width = '0%';
  document.getElementById('progress-bar').textContent = '0%';
  document.getElementById('submit-upload-btn').disabled = false;
}

function handleFileSelect(event) {
  const file = event.target.files[0];
  if (file) {
    const maxSize = 1024 * 1024 * 1024; // 1GB
    if (file.size > maxSize) {
      showError('File size exceeds 1GB limit.');
      event.target.value = '';
      document.getElementById('file-name').textContent = '';
      return;
    }
    const fileSizeMB = (file.size / (1024 * 1024)).toFixed(2);
    document.getElementById('file-name').textContent = `Selected: ${file.name} (${fileSizeMB} MB)`;
  }
}

async function submitUpload() {
  // Hide previous messages
  document.getElementById('success-message').classList.remove('active');
  document.getElementById('error-message').classList.remove('active');

  // Validate form
  const form = document.getElementById('upload-form');
  if (!form.checkValidity()) {
    form.reportValidity();
    return;
  }

  // Get form data
  const title = document.getElementById('title').value.trim();
  const recordedAt = document.getElementById('recorded-at').value;
  const keyword1 = document.getElementById('keyword1').value.trim();
  const keyword2 = document.getElementById('keyword2').value.trim();
  const keyword3 = document.getElementById('keyword3').value.trim();
  const keyword4 = document.getElementById('keyword4').value.trim();
  const keyword5 = document.getElementById('keyword5').value.trim();
  const keyword6 = document.getElementById('keyword6').value.trim();
  const description = document.getElementById('description').value.trim();
  const audioFile = document.getElementById('audio-file').files[0];

  if (!audioFile) {
    showError('Please select an audio file.');
    return;
  }

  // Disable submit button
  document.getElementById('submit-upload-btn').disabled = true;

  // Show progress container
  document.getElementById('progress-container').classList.add('active');
  document.getElementById('progress-text').textContent = 'Preparing upload...';

  // Create FormData
  const formData = new FormData();
  formData.append('title', title);
  formData.append('recordedAt', recordedAt);
  formData.append('keyword1', keyword1);
  formData.append('keyword2', keyword2);
  formData.append('keyword3', keyword3);
  formData.append('keyword4', keyword4);
  formData.append('keyword5', keyword5);
  formData.append('keyword6', keyword6);
  if (description) {
    formData.append('description', description);
  }
  formData.append('audioFile', audioFile);

  try {
    // Create XMLHttpRequest for progress tracking
    const xhr = new XMLHttpRequest();

    // Track upload progress
    xhr.upload.addEventListener('progress', (e) => {
      if (e.lengthComputable) {
        const percentComplete = Math.round((e.loaded / e.total) * 100);
        updateProgress(percentComplete, 'Uploading...');
      }
    });

    // Handle completion
    xhr.addEventListener('load', () => {
      if (xhr.status === 200) {
        const response = JSON.parse(xhr.responseText);
        if (response.success) {
          updateProgress(100, 'Upload complete!');
          showSuccess(response.message || 'Shiur uploaded successfully!');
          setTimeout(() => {
            closeUploadModal();
            // Reload recordings list to show the new upload
            loadRecordings();
          }, 2000);
        } else {
          showError(response.message || 'Upload failed.');
          document.getElementById('submit-upload-btn').disabled = false;
        }
      } else {
        const response = JSON.parse(xhr.responseText);
        showError(response.message || 'Upload failed. Please try again.');
        document.getElementById('submit-upload-btn').disabled = false;
      }
    });

    // Handle errors
    xhr.addEventListener('error', () => {
      showError('Network error occurred. Please try again.');
      document.getElementById('submit-upload-btn').disabled = false;
    });

    xhr.addEventListener('abort', () => {
      showError('Upload was cancelled.');
      document.getElementById('submit-upload-btn').disabled = false;
    });

    // Send request
    xhr.open('POST', `/api/series/${currentSeriesId}/recordings`);
    xhr.send(formData);

  } catch (error) {
    console.error('Error uploading shiur:', error);
    showError('An error occurred during upload. Please try again.');
    document.getElementById('submit-upload-btn').disabled = false;
  }
}

function updateProgress(percent, text) {
  const progressBar = document.getElementById('progress-bar');
  progressBar.style.width = percent + '%';
  progressBar.textContent = percent + '%';
  document.getElementById('progress-text').textContent = text;
}

function showSuccess(message) {
  const successDiv = document.getElementById('success-message');
  successDiv.textContent = message;
  successDiv.classList.add('active');
  document.getElementById('progress-container').classList.remove('active');
}

function showError(message) {
  const errorDiv = document.getElementById('error-message');
  errorDiv.textContent = message;
  errorDiv.classList.add('active');
  document.getElementById('progress-container').classList.remove('active');
}

// ============ RECORDING LIST AND PLAYER FUNCTIONS ============

async function loadRecordings() {
  const sortOrder = document.getElementById('sort-select').value;
  const loadingDiv = document.getElementById('recordings-loading');
  const errorDiv = document.getElementById('recordings-error');
  const listDiv = document.getElementById('recordings-list');

  loadingDiv.classList.remove('hidden');
  errorDiv.classList.add('hidden');
  listDiv.innerHTML = '';

  try {
    const resp = await fetch(`/api/series/${currentSeriesId}/recordings?sort=${sortOrder}`);
    if (!resp.ok) throw new Error('Failed to load recordings');
    const data = await resp.json();

    if (data.success && data.recordings) {
      if (data.recordings.length === 0) {
        listDiv.innerHTML = '<div class="no-recordings">There are currently no shiurim uploaded for this series.</div>';
      } else {
        data.recordings.forEach(recording => {
          const item = createRecordingItem(recording);
          listDiv.appendChild(item);
        });
      }
    } else {
      throw new Error(data.message || 'Failed to load recordings');
    }
  } catch (error) {
    console.error('Error loading recordings:', error);
    errorDiv.textContent = 'Error loading shiurim: ' + error.message;
    errorDiv.classList.remove('hidden');
  } finally {
    loadingDiv.classList.add('hidden');
  }
}

function createRecordingItem(recording) {
  const item = document.createElement('div');
  item.className = 'recording-item';
  item.dataset.recordingId = recording.recordingId;
  item.dataset.s3FilePath = recording.s3FilePath;

  // Format date
  const recordedDate = new Date(recording.recordedAt);
  const formattedDate = formatRecordingDate(recordedDate);

  // Create header
  const header = document.createElement('div');
  header.className = 'recording-header';
  header.onclick = () => toggleRecordingDetails(recording.recordingId);

  const playBtn = document.createElement('button');
  playBtn.className = 'recording-play-btn';
  playBtn.textContent = '▶';
  playBtn.onclick = (e) => {
    e.stopPropagation();
    togglePlayRecording(recording.recordingId, recording.s3FilePath);
  };

  const info = document.createElement('div');
  info.className = 'recording-info';

  const title = document.createElement('div');
  title.className = 'recording-title';
  title.textContent = recording.title;

  const date = document.createElement('div');
  date.className = 'recording-date';
  date.textContent = formattedDate;

  info.appendChild(title);
  info.appendChild(date);
  header.appendChild(playBtn);
  header.appendChild(info);

  // Create details section
  const details = document.createElement('div');
  details.className = 'recording-details';

  const description = document.createElement('div');
  description.className = 'recording-description';
  if (recording.description && recording.description.trim()) {
    description.textContent = recording.description;
  } else {
    description.textContent = 'No description provided.';
    description.classList.add('empty');
  }

  details.appendChild(description);

  // Create player section (will be populated when expanded)
  const playerDiv = document.createElement('div');
  playerDiv.className = 'recording-player';
  playerDiv.id = `player-${recording.recordingId}`;
  playerDiv.style.display = 'none';
  details.appendChild(playerDiv);

  item.appendChild(header);
  item.appendChild(details);

  return item;
}

function formatRecordingDate(date) {
  const month = (date.getMonth() + 1).toString();
  const day = date.getDate().toString();
  const year = date.getFullYear();
  let hours = date.getHours();
  const minutes = date.getMinutes().toString().padStart(2, '0');
  const ampm = hours >= 12 ? 'pm' : 'am';
  hours = hours % 12 || 12;

  return `${month}/${day}/${year} ${hours}:${minutes}${ampm}`;
}

function toggleRecordingDetails(recordingId) {
  const item = document.querySelector(`.recording-item[data-recording-id="${recordingId}"]`);
  const wasExpanded = item.classList.contains('expanded');

  // Collapse all items
  document.querySelectorAll('.recording-item').forEach(i => {
    i.classList.remove('expanded');
  });

  // Expand this item if it wasn't already expanded
  if (!wasExpanded) {
    item.classList.add('expanded');

    // If this is the currently playing recording, show the player
    if (currentRecordingId === recordingId) {
      showPlayerInRecording(recordingId);
    }
  }
}

function togglePlayRecording(recordingId, s3FilePath) {
  const item = document.querySelector(`.recording-item[data-recording-id="${recordingId}"]`);

  // If this recording is already playing, pause it
  if (currentRecordingId === recordingId && !mainAudio.paused) {
    mainAudio.pause();
    return;
  }

  // Otherwise, play this recording
  playRecording(recordingId, s3FilePath);

  // Expand the item if not already expanded
  if (!item.classList.contains('expanded')) {
    toggleRecordingDetails(recordingId);
  }
}

function playRecording(recordingId, s3FilePath) {
  currentRecordingId = recordingId;

  // Update UI
  updateAllPlayButtons();

  // Show player in the expanded recording
  showPlayerInRecording(recordingId);

  // Disable play button until loaded
  const playPauseBtn = document.getElementById(`play-pause-${recordingId}`);
  if (playPauseBtn) {
    playPauseBtn.disabled = true;
    playPauseBtn.textContent = '⏳';
  }

  // Load and play audio
  const streamUrl = `/api/audio/series/${currentSeriesId}/stream/${encodeURIComponent(s3FilePath)}`;
  mainAudio.src = streamUrl;
  mainAudio.load();

  // Wait for duration to be available before playing
  const tryPlay = () => {
    if (mainAudio.duration && isFinite(mainAudio.duration)) {
      mainAudio.play();
      if (playPauseBtn) {
        playPauseBtn.disabled = false;
      }
    } else {
      // Try again in a short interval
      setTimeout(tryPlay, 100);
    }
  };

  tryPlay();
}

function showPlayerInRecording(recordingId) {
  // Hide all players
  document.querySelectorAll('.recording-player').forEach(p => p.style.display = 'none');

  // Show and populate player for this recording
  const playerDiv = document.getElementById(`player-${recordingId}`);
  if (playerDiv) {
    playerDiv.style.display = 'block';

    // Only create controls if they don't exist
    if (!playerDiv.querySelector('.player-controls')) {
      const title = document.createElement('h4');
      title.textContent = 'Now Playing';

      const controls = createPlayerControls(recordingId);

      playerDiv.innerHTML = '';
      playerDiv.appendChild(title);
      playerDiv.appendChild(controls);
    }
  }
}

function createPlayerControls(recordingId) {
  const controls = document.createElement('div');
  controls.className = 'player-controls';

  // Playback controls
  const playbackDiv = document.createElement('div');
  playbackDiv.className = 'playback-controls';

  const skipBack = document.createElement('button');
  skipBack.className = 'btn-skip';
  skipBack.textContent = '◄ 15s';
  skipBack.onclick = () => skipBackward();

  const playPause = document.createElement('button');
  playPause.className = 'btn-play-pause';
  playPause.id = `play-pause-${recordingId}`;
  playPause.textContent = mainAudio.paused ? '▶' : '⏸';
  playPause.onclick = () => togglePlayPause();

  const skipFwd = document.createElement('button');
  skipFwd.className = 'btn-skip';
  skipFwd.textContent = '15s ►';
  skipFwd.onclick = () => skipForward();

  playbackDiv.appendChild(skipBack);
  playbackDiv.appendChild(playPause);
  playbackDiv.appendChild(skipFwd);

  // Progress bar
  const progressContainer = document.createElement('div');
  progressContainer.className = 'progress-bar-container';

  const progressTrack = document.createElement('div');
  progressTrack.className = 'progress-bar-track';
  progressTrack.id = `progress-track-${recordingId}`;
  progressTrack.onclick = (e) => seekTo(e, recordingId);

  const progressFill = document.createElement('div');
  progressFill.className = 'progress-bar-fill';
  progressFill.id = `progress-fill-${recordingId}`;

  progressTrack.appendChild(progressFill);
  progressContainer.appendChild(progressTrack);

  // Time display
  const timeDisplay = document.createElement('div');
  timeDisplay.className = 'time-display';
  timeDisplay.id = `time-display-${recordingId}`;
  timeDisplay.innerHTML = '<span class="current-time">0:00</span> / <span class="duration">--:--</span>';

  // Speed control
  const speedDiv = document.createElement('div');
  speedDiv.className = 'speed-control';

  const speedLabel = document.createElement('label');
  speedLabel.textContent = 'Speed:';

  const speedSelect = document.createElement('select');
  speedSelect.id = `speed-${recordingId}`;
  speedSelect.innerHTML = `
    <option value="0.25">0.25x</option>
    <option value="0.5">0.5x</option>
    <option value="0.75">0.75x</option>
    <option value="1" selected>1.0x</option>
    <option value="1.25">1.25x</option>
    <option value="1.5">1.5x</option>
    <option value="1.75">1.75x</option>
    <option value="2">2.0x</option>
  `;
  speedSelect.onchange = (e) => changeSpeed(e.target.value);

  speedDiv.appendChild(speedLabel);
  speedDiv.appendChild(speedSelect);

  controls.appendChild(playbackDiv);
  controls.appendChild(progressContainer);
  controls.appendChild(timeDisplay);
  controls.appendChild(speedDiv);

  return controls;
}

function updateAllPlayButtons() {
  document.querySelectorAll('.recording-item').forEach(item => {
    const btn = item.querySelector('.recording-play-btn');
    const itemId = parseInt(item.dataset.recordingId);

    if (itemId === currentRecordingId && !mainAudio.paused) {
      btn.textContent = '⏸';
      item.classList.add('playing');
    } else {
      btn.textContent = '▶';
      item.classList.remove('playing');
    }
  });

  // Update play/pause button in active player
  if (currentRecordingId) {
    const playPauseBtn = document.getElementById(`play-pause-${currentRecordingId}`);
    if (playPauseBtn) {
      playPauseBtn.textContent = mainAudio.paused ? '▶' : '⏸';
    }
  }
}

function togglePlayPause() {
  if (mainAudio.paused) {
    mainAudio.play();
  } else {
    mainAudio.pause();
  }
}

function skipBackward() {
  mainAudio.currentTime = Math.max(0, mainAudio.currentTime - SKIP_INTERVAL);
}

function skipForward() {
  if (mainAudio.duration) {
    mainAudio.currentTime = Math.min(mainAudio.duration, mainAudio.currentTime + SKIP_INTERVAL);
  } else {
    mainAudio.currentTime += SKIP_INTERVAL;
  }
}

function changeSpeed(speed) {
  mainAudio.playbackRate = parseFloat(speed);
}

function seekTo(event, recordingId) {
  if (mainAudio.duration) {
    const track = document.getElementById(`progress-track-${recordingId}`);
    const rect = track.getBoundingClientRect();
    const percent = (event.clientX - rect.left) / rect.width;
    mainAudio.currentTime = percent * mainAudio.duration;
  }
}

function formatTime(seconds) {
  if (isNaN(seconds) || !isFinite(seconds)) return '0:00';
  const mins = Math.floor(seconds / 60);
  const secs = Math.floor(seconds % 60);
  return `${mins}:${secs.toString().padStart(2, '0')}`;
}

function updateProgress() {
  if (!currentRecordingId) return;

  const progressFill = document.getElementById(`progress-fill-${currentRecordingId}`);
  const timeDisplay = document.getElementById(`time-display-${currentRecordingId}`);

  if (progressFill && mainAudio.duration && isFinite(mainAudio.duration)) {
    const percent = (mainAudio.currentTime / mainAudio.duration) * 100;
    progressFill.style.width = percent + '%';
  }

  if (timeDisplay) {
    const currentSpan = timeDisplay.querySelector('.current-time');
    const durationSpan = timeDisplay.querySelector('.duration');

    if (currentSpan) currentSpan.textContent = formatTime(mainAudio.currentTime);

    // Only show duration when it's actually available
    if (durationSpan) {
      if (mainAudio.duration && isFinite(mainAudio.duration)) {
        durationSpan.textContent = formatTime(mainAudio.duration);
      } else {
        durationSpan.textContent = '--:--';
      }
    }
  }
}

function animateProgress() {
  updateProgress();
  if (!mainAudio.paused && !mainAudio.ended) {
    animationFrameId = requestAnimationFrame(animateProgress);
  }
}

function startProgressAnimation() {
  if (animationFrameId) {
    cancelAnimationFrame(animationFrameId);
  }
  if (!mainAudio.paused) {
    animateProgress();
  }
}

function stopProgressAnimation() {
  if (animationFrameId) {
    cancelAnimationFrame(animationFrameId);
    animationFrameId = null;
  }
}

function initializeAudioListeners() {
  mainAudio.addEventListener('loadedmetadata', () => {
    updateProgress();
  });

  mainAudio.addEventListener('loadeddata', () => {
    // loadeddata fires when duration is definitely available
    updateProgress();
  });

  mainAudio.addEventListener('durationchange', () => {
    // This fires when duration becomes available
    updateProgress();
  });

  mainAudio.addEventListener('canplay', () => {
    // Additional fallback to ensure progress updates
    updateProgress();
  });

  mainAudio.addEventListener('play', () => {
    updateAllPlayButtons();
    startProgressAnimation();
    // Ensure progress is updated when playback starts
    updateProgress();
  });

  mainAudio.addEventListener('pause', () => {
    updateAllPlayButtons();
    stopProgressAnimation();
    updateProgress();
  });

  mainAudio.addEventListener('ended', () => {
    stopProgressAnimation();
    updateProgress();
    updateAllPlayButtons();
    mainAudio.currentTime = 0;
  });

  mainAudio.addEventListener('timeupdate', () => {
    // Fallback for progress updates if animation frame isn't working
    if (mainAudio.paused) {
      updateProgress();
    }
  });
}

// ============ AUTO-PLAY RECORDING FROM SEARCH ============

function autoPlayRecording(recordingId) {
  const item = document.querySelector(`.recording-item[data-recording-id="${recordingId}"]`);

  if (!item) {
    console.warn(`Recording ${recordingId} not found`);
    return;
  }

  const s3FilePath = item.dataset.s3FilePath;

  // Expand the recording
  item.classList.add('expanded');

  // Scroll to the recording
  item.scrollIntoView({ behavior: 'smooth', block: 'center' });

  // Play the recording
  playRecording(recordingId, s3FilePath);
}