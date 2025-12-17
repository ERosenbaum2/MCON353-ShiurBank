// Check if user is logged in on page load and then load dashboard data
document.addEventListener('DOMContentLoaded', async function() {
    await checkAuthAndLoadUser();
    wireCreateSeriesButton();
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
            item.style.cursor = 'pointer';
            item.style.padding = '0.5rem 0';
            item.style.borderBottom = '1px solid #eee';

            const title = s.displayName || ('Series #' + s.seriesId);
            item.textContent = title;

            item.addEventListener('click', function() {
                window.location.href = '/series/' + encodeURIComponent(s.seriesId);
            });

            listEl.appendChild(item);
        });
    } catch (error) {
        console.error('Error loading my series', error);
        listEl.textContent = 'Error loading your series.';
    }
}
