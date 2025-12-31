let currentSeriesId = null;

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

  // Set up file input change handler
  document.getElementById('audio-file').addEventListener('change', handleFileSelect);
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
    }
  } catch (error) {
    console.error('Error checking gabbai status', error);
  }
}

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