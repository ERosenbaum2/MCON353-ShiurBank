// Check if user is logged in on page load and then load dashboard data
document.addEventListener('DOMContentLoaded', async function() {
    await checkAuthAndLoadUser();
    await checkAdminStatus();
    wireCreateSeriesButton();
    wireDatabaseControls();
    loadDatabaseStatus();
    loadMySeries();
});

// Check authentication and load user info
async function checkAuthAndLoadUser() {
    try {
        const response = await fetch('/api/current-user');
        if (response.ok) {
            const data = await response.json();
            if (data.loggedIn) {
                // User is logged in, display username and show content
                document.getElementById('username-display').textContent = data.username;
                document.getElementById('user-greeting').classList.remove('hidden');
                document.getElementById('dashboard-main').classList.remove('hidden');
            } else {
                // User is not logged in, redirect to home page
                window.location.href = '/index.html';
            }
        } else {
            // Error checking auth, redirect to home page
            window.location.href = '/index.html';
        }
    } catch (error) {
        console.error('Error checking authentication:', error);
        window.location.href = '/index.html';
    }
}

// Handle logout
async function handleLogout() {
    try {
        const response = await fetch('/api/logout', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            }
        });

        const data = await response.json();

        if (data.success) {
            // Redirect to home page
            window.location.href = '/index.html';
        } else {
            alert('Logout failed. Please try again.');
        }
    } catch (error) {
        console.error('Error during logout:', error);
        alert('An error occurred during logout. Please try again.');
    }
}

function wireCreateSeriesButton() {
    const btn = document.getElementById('start-series-btn');
    if (btn) {
        btn.addEventListener('click', function() {
            window.location.href = '/create-series.html';
        });
    }
}

// Check admin status and show admin link if user is admin
async function checkAdminStatus() {
    try {
        const response = await fetch('/api/admin/check');
        if (response.ok) {
            const data = await response.json();
            if (data.isAdmin) {
                const adminLinkContainer = document.getElementById('admin-link-container');
                if (adminLinkContainer) {
                    adminLinkContainer.classList.remove('hidden');
                }
            }
        }
    } catch (error) {
        console.error('Error checking admin status:', error);
        // Silently fail - don't show admin link if check fails
    }
}

// Wire up database control buttons
function wireDatabaseControls() {
    const checkBtn = document.getElementById('check-db-status-btn');
    const startBtn = document.getElementById('start-db-btn');
    const stopBtn = document.getElementById('stop-db-btn');

    if (checkBtn) {
        checkBtn.addEventListener('click', loadDatabaseStatus);
    }
    if (startBtn) {
        startBtn.addEventListener('click', startDatabase);
    }
    if (stopBtn) {
        stopBtn.addEventListener('click', stopDatabase);
    }
}

// Load database status
async function loadDatabaseStatus() {
    const statusDisplay = document.getElementById('db-status-display');
    const startBtn = document.getElementById('start-db-btn');
    const stopBtn = document.getElementById('stop-db-btn');
    const messageEl = document.getElementById('db-message');

    if (!statusDisplay) return;

    try {
        statusDisplay.textContent = 'Checking...';
        messageEl.textContent = '';
        
        const response = await fetch('/api/admin/rds/status');
        
        if (!response.ok) {
            if (response.status === 403) {
                statusDisplay.textContent = 'Unknown (Admin access required)';
                messageEl.textContent = 'Note: Database controls require admin access.';
                startBtn.disabled = true;
                stopBtn.disabled = true;
            } else {
                statusDisplay.textContent = 'Error checking status';
                messageEl.textContent = 'Could not check database status. You can still try to start it.';
                // Enable start button even on error - might be able to start it
                startBtn.disabled = false;
                stopBtn.disabled = true;
            }
            return;
        }

        const data = await response.json();
        if (data.success) {
            const status = data.status || 'unknown';
            statusDisplay.textContent = status;
            messageEl.textContent = '';

            // Enable/disable buttons based on status
            if (status.toLowerCase() === 'stopped') {
                startBtn.disabled = false;
                stopBtn.disabled = true;
                messageEl.textContent = 'Database is stopped. Click "Start Database" to start it.';
            } else if (status.toLowerCase() === 'available' || status.toLowerCase() === 'running') {
                startBtn.disabled = true;
                stopBtn.disabled = false;
                messageEl.textContent = 'Database is running.';
            } else if (status.toLowerCase() === 'starting') {
                startBtn.disabled = true;
                stopBtn.disabled = true;
                messageEl.textContent = 'Database is starting. Please wait...';
            } else if (status.toLowerCase() === 'stopping') {
                startBtn.disabled = true;
                stopBtn.disabled = true;
                messageEl.textContent = 'Database is stopping. Please wait...';
            } else {
                startBtn.disabled = false;
                stopBtn.disabled = false;
                messageEl.textContent = `Database status: ${status}`;
            }
        } else {
            statusDisplay.textContent = 'Error';
            messageEl.textContent = data.message || 'Could not get database status.';
            startBtn.disabled = false; // Allow trying to start anyway
            stopBtn.disabled = true;
        }
    } catch (error) {
        console.error('Error loading database status:', error);
        statusDisplay.textContent = 'Connection Error';
        messageEl.textContent = 'Could not connect to check status. Database might be down. Try starting it.';
        startBtn.disabled = false; // Enable start button - might be able to start it
        stopBtn.disabled = true;
    }
}

// Start database
async function startDatabase() {
    const startBtn = document.getElementById('start-db-btn');
    const messageEl = document.getElementById('db-message');
    
    if (!startBtn) return;
    
    startBtn.disabled = true;
    startBtn.textContent = 'Starting...';
    messageEl.textContent = 'Attempting to start database...';

    try {
        const response = await fetch('/api/admin/rds/start', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            }
        });

        const data = await response.json();
        if (data.success) {
            messageEl.textContent = 'Database start initiated! It may take 2-5 minutes to become available.';
            // Reload status after a short delay
            setTimeout(loadDatabaseStatus, 2000);
        } else {
            messageEl.textContent = 'Error: ' + (data.message || 'Failed to start database');
            startBtn.disabled = false;
            startBtn.textContent = 'Start Database';
            loadDatabaseStatus();
        }
    } catch (error) {
        console.error('Error starting database:', error);
        messageEl.textContent = 'Error starting database. Check if you have admin access.';
        startBtn.disabled = false;
        startBtn.textContent = 'Start Database';
    }
}

// Stop database
async function stopDatabase() {
    const stopBtn = document.getElementById('stop-db-btn');
    const messageEl = document.getElementById('db-message');
    
    if (!stopBtn) return;
    
    stopBtn.disabled = true;
    stopBtn.textContent = 'Stopping...';
    messageEl.textContent = 'Attempting to stop database...';

    try {
        const response = await fetch('/api/admin/rds/stop', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            }
        });

        const data = await response.json();
        if (data.success) {
            messageEl.textContent = 'Database stop initiated.';
            setTimeout(loadDatabaseStatus, 2000);
        } else {
            messageEl.textContent = 'Error: ' + (data.message || 'Failed to stop database');
            stopBtn.disabled = false;
            stopBtn.textContent = 'Stop Database';
            loadDatabaseStatus();
        }
    } catch (error) {
        console.error('Error stopping database:', error);
        messageEl.textContent = 'Error stopping database.';
        stopBtn.disabled = false;
        stopBtn.textContent = 'Stop Database';
    }
}

async function loadMySeries() {
    const listEl = document.getElementById('my-series-list');
    if (!listEl) {
        return;
    }

    listEl.textContent = 'Loading your series...';

    try {
        const resp = await fetch('/api/my-series');
        if (!resp.ok) {
            listEl.textContent = 'Could not load your series.';
            return;
        }

        const series = await resp.json();
        if (!Array.isArray(series) || series.length === 0) {
            listEl.textContent = 'You have not started any Shiur Series yet.';
            return;
        }

        listEl.innerHTML = '';
        series.forEach(function(s) {
            const item = document.createElement('div');
            item.className = 'series-item';

            const title = s.displayName || ('Series #' + s.seriesId);

            // Check if series is pending verification
            if (s.isPending === true) {
                item.classList.add('series-pending');
                item.style.cursor = 'not-allowed';

                const titleDiv = document.createElement('div');
                titleDiv.textContent = title;
                titleDiv.style.marginBottom = '0.25rem';

                const badge = document.createElement('span');
                badge.className = 'pending-badge';
                badge.textContent = 'Pending Verification';

                item.appendChild(titleDiv);
                item.appendChild(badge);
            } else {
                item.textContent = title;
                item.style.cursor = 'pointer';

                // Only add click handler for verified series
                item.addEventListener('click', function() {
                    window.location.href = '/series/' + encodeURIComponent(s.seriesId);
                });
            }

            listEl.appendChild(item);
        });
    } catch (error) {
        console.error('Error loading my series', error);
        listEl.textContent = 'Could not load your series.';
    }
}