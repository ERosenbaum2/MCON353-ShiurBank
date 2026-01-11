document.addEventListener('DOMContentLoaded', async () => {
  await checkAuthAndShowPage();
  await Promise.all([loadTopics(), loadRebbeim(), loadInstitutions()]);
  const form = document.getElementById('create-series-form');
  form.addEventListener('submit', handleCreateSeries);
  const addGabbaiBtn = document.getElementById('add-gabbai-btn');
  if (addGabbaiBtn) {
    addGabbaiBtn.addEventListener('click', addGabbaiRow);
  }
});

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
    document.getElementById('create-series-main').classList.remove('hidden');
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

async function loadTopics() {
  const select = document.getElementById('topic-select');
  try {
    const resp = await fetch('/api/topics');
    if (!resp.ok) throw new Error('Failed to load topics');
    const topics = await resp.json();
    topics.forEach((t) => {
      const opt = document.createElement('option');
      opt.value = t.topicId;
      opt.textContent = t.name;
      select.appendChild(opt);
    });
  } catch (error) {
    console.error('Error loading topics', error);
  }
}

async function loadRebbeim() {
  const select = document.getElementById('rebbi-select');
  if (!select) {
    return;
  }
  // Clear any existing options except the placeholder
  select.innerHTML = '<option value="">Select a Rebbi</option>';
  try {
    const resp = await fetch('/api/rebbeim');
    if (!resp.ok) throw new Error('Failed to load Rebbeim');
    const rebbeim = await resp.json();
    rebbeim.forEach((r) => {
      const opt = document.createElement('option');
      opt.value = r.rebbiId;
      opt.textContent = r.title + ' ' + r.fname + ' ' + r.lname;
      select.appendChild(opt);
    });
  } catch (error) {
    console.error('Error loading Rebbeim', error);
  }
}

async function loadInstitutions() {
  const select = document.getElementById('inst-select');
  try {
    const resp = await fetch('/api/institutions');
    if (!resp.ok) throw new Error('Failed to load institutions');
    const institutions = await resp.json();
    institutions.forEach((inst) => {
      const opt = document.createElement('option');
      opt.value = inst.instId;
      opt.textContent = inst.name;
      select.appendChild(opt);
    });
  } catch (error) {
    console.error('Error loading institutions', error);
  }
}

function addGabbaiRow() {
  const container = document.getElementById('gabbaim-container');
  if (!container) {
    return;
  }

  const row = document.createElement('div');
  row.className = 'gabbai-row';
  row.style.display = 'flex';
  row.style.gap = '0.5rem';
  row.style.marginBottom = '0.5rem';

  const usernameInput = document.createElement('input');
  usernameInput.type = 'text';
  usernameInput.placeholder = 'Gabbai username';
  usernameInput.className = 'gabbai-username';
  usernameInput.style.flex = '1';

  const passwordInput = document.createElement('input');
  passwordInput.type = 'password';
  passwordInput.placeholder = 'Gabbai password';
  passwordInput.className = 'gabbai-password';
  passwordInput.style.flex = '1';

  const removeBtn = document.createElement('button');
  removeBtn.type = 'button';
  removeBtn.className = 'btn-secondary';
  removeBtn.textContent = 'Remove';
  removeBtn.addEventListener('click', function() {
    container.removeChild(row);
  });

  row.appendChild(usernameInput);
  row.appendChild(passwordInput);
  row.appendChild(removeBtn);
  container.appendChild(row);
}

async function handleCreateSeries(event) {
  event.preventDefault();

  const description = document.getElementById('series-description').value.trim();
  const topicId = document.getElementById('topic-select').value;
  const instId = document.getElementById('inst-select').value;

  const rebbiSelect = document.getElementById('rebbi-select');
  const rebbiValue = rebbiSelect ? rebbiSelect.value : '';
  if (!rebbiValue) {
    alert('Please select a Rebbi for this Series.');
    return;
  }
  const rebbiId = parseInt(rebbiValue, 10);

  if (!description || !topicId || !instId) {
    alert('Please fill all required fields.');
    return;
  }

  // Collect additional gabbaim
  const gabbaiRows = Array.from(document.querySelectorAll('.gabbai-row'));
  const extraGabbaim = [];
  for (const row of gabbaiRows) {
    const usernameInput = row.querySelector('.gabbai-username');
    const passwordInput = row.querySelector('.gabbai-password');
    const username = usernameInput ? usernameInput.value.trim() : '';
    const password = passwordInput ? passwordInput.value : '';

    // If one of username/password is filled, require both
    if ((username && !password) || (!username && password)) {
      alert('Each additional gabbai must have both a username and a password, or be left completely blank.');
      return;
    }

    if (username && password) {
      extraGabbaim.push({ username: username, password: password });
    }
  }

  const requiresPermissionStr = document.querySelector(
    'input[name="requiresPermission"]:checked'
  ).value;
  const requiresPermission = requiresPermissionStr === 'true';

  const payload = {
    description,
    topicId: parseInt(topicId, 10),
    rebbiId,
    instId: parseInt(instId, 10),
    requiresPermission,
    extraGabbaim: extraGabbaim
  };

  try {
    const resp = await fetch('/api/series', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });
    const data = await resp.json();

    if (!resp.ok || !data.success) {
      alert(data.message || 'Could not create series.');
      return;
    }

    const seriesId = data.seriesId;
    const needsVerification = data.needsVerification;

    // Redirect based on verification status
    if (needsVerification) {
      // Series is pending approval, redirect to dashboard
      window.location.href = '/dashboard.html';
    } else {
      // Series was automatically approved, redirect to series page
      window.location.href = `/series/${encodeURIComponent(seriesId)}`;
    }
  } catch (error) {
    console.error('Error creating series', error);
    alert('An error occurred while creating the series.');
  }
}