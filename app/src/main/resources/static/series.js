document.addEventListener('DOMContentLoaded', async () => {
  await checkAuthAndShowPage();
  const seriesId = getSeriesIdFromPath();
  if (!seriesId) {
    alert('No series ID provided.');
    return;
  }
  await loadSeriesDetails(seriesId);
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


