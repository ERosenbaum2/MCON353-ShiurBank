// Admin page JavaScript

let currentPendingId = null;
let currentUserIdToAdd = null;
let allUsers = [];

// Check if user is logged in and is an admin on page load
document.addEventListener('DOMContentLoaded', async function() {
    await checkAuthAndAdminStatus();
    wireEventHandlers();
    await loadDatabaseStatus();
    await loadAllUsers();
    await loadPendingPermissions();
});

// Check authentication and admin status
async function checkAuthAndAdminStatus() {
    try {
        // First check if user is logged in
        const userResponse = await fetch('/api/current-user', {
            credentials: 'include'
        });
        if (!userResponse.ok) {
            window.location.href = '/dashboard.html';
            return;
        }

        const userData = await userResponse.json();
        if (!userData.loggedIn) {
            window.location.href = '/index.html';
            return;
        }

        // Display username
        document.getElementById('username-display').textContent = userData.username;
        document.getElementById('user-greeting').classList.remove('hidden');

        // Check if user is admin
        const adminResponse = await fetch('/api/admin/check', {
            credentials: 'include'
        });
        if (!adminResponse.ok) {
            window.location.href = '/dashboard.html';
            return;
        }

        const adminData = await adminResponse.json();
        if (!adminData.isAdmin) {
            // Not an admin, redirect to dashboard
            window.location.href = '/dashboard.html';
            return;
        }

        // User is admin, show content
        document.getElementById('admin-main').classList.remove('hidden');
    } catch (error) {
        console.error('Error checking authentication:', error);
        window.location.href = '/dashboard.html';
    }
}

// Wire up event handlers
function wireEventHandlers() {
    // Database control buttons
    const checkStatusBtn = document.getElementById('check-status-btn');
    const startDbBtn = document.getElementById('start-db-btn');
    const stopDbBtn = document.getElementById('stop-db-btn');

    if (checkStatusBtn) {
        checkStatusBtn.addEventListener('click', loadDatabaseStatus);
    }
    if (startDbBtn) {
        startDbBtn.addEventListener('click', startDatabase);
    }
    if (stopDbBtn) {
        stopDbBtn.addEventListener('click', stopDatabase);
    }

    // Modal buttons for pending permission
    const confirmYesBtn = document.getElementById('confirm-yes-btn');
    const confirmNoBtn = document.getElementById('confirm-no-btn');
    const modal = document.getElementById('confirm-modal');

    if (confirmYesBtn) {
        confirmYesBtn.addEventListener('click', handleVerifyConfirm);
    }
    if (confirmNoBtn) {
        confirmNoBtn.addEventListener('click', () => {
            modal.classList.add('hidden');
            currentPendingId = null;
        });
    }

    // Close modal when clicking outside
    if (modal) {
        modal.addEventListener('click', (e) => {
            if (e.target === modal) {
                modal.classList.add('hidden');
                currentPendingId = null;
            }
        });
    }

    // Add admin controls
    const userSelect = document.getElementById('user-select');
    const addAdminBtn = document.getElementById('add-admin-btn');
    const addAdminModal = document.getElementById('add-admin-modal');
    const addAdminConfirmBtn = document.getElementById('add-admin-confirm-btn');
    const addAdminCancelBtn = document.getElementById('add-admin-cancel-btn');

    if (userSelect) {
        userSelect.addEventListener('change', function() {
            const selectedValue = this.value;
            if (selectedValue && selectedValue !== '') {
                const selectedUser = allUsers.find(u => u.userId.toString() === selectedValue);
                if (selectedUser && !selectedUser.isAdmin) {
                    addAdminBtn.disabled = false;
                } else {
                    addAdminBtn.disabled = true;
                }
            } else {
                addAdminBtn.disabled = true;
            }
        });
    }

    if (addAdminBtn) {
        addAdminBtn.addEventListener('click', handleAddAdminClick);
    }

    if (addAdminConfirmBtn) {
        addAdminConfirmBtn.addEventListener('click', handleAddAdminConfirm);
    }

    if (addAdminCancelBtn) {
        addAdminCancelBtn.addEventListener('click', () => {
            addAdminModal.classList.add('hidden');
            currentUserIdToAdd = null;
        });
    }

    // Close add admin modal when clicking outside
    if (addAdminModal) {
        addAdminModal.addEventListener('click', (e) => {
            if (e.target === addAdminModal) {
                addAdminModal.classList.add('hidden');
                currentUserIdToAdd = null;
            }
        });
    }
}

// Load database status
async function loadDatabaseStatus() {
    const statusDisplay = document.getElementById('db-status');
    const startBtn = document.getElementById('start-db-btn');
    const stopBtn = document.getElementById('stop-db-btn');

    try {
        statusDisplay.textContent = 'Loading...';
        const response = await fetch('/api/admin/rds/status');

        if (!response.ok) {
            statusDisplay.textContent = 'Error';
            startBtn.disabled = true;
            stopBtn.disabled = true;
            return;
        }

        const data = await response.json();
        if (data.success) {
            const status = data.status || 'unknown';
            statusDisplay.textContent = status;

            // Enable/disable buttons based on status
            if (status.toLowerCase() === 'stopped' || status.toLowerCase() === 'available') {
                if (status.toLowerCase() === 'stopped') {
                    startBtn.disabled = false;
                    stopBtn.disabled = true;
                } else {
                    startBtn.disabled = true;
                    stopBtn.disabled = false;
                }
            } else if (status.toLowerCase() === 'starting' || status.toLowerCase() === 'stopping') {
                startBtn.disabled = true;
                stopBtn.disabled = true;
            } else {
                startBtn.disabled = true;
                stopBtn.disabled = true;
            }
        } else {
            statusDisplay.textContent = 'Error';
            startBtn.disabled = true;
            stopBtn.disabled = true;
        }
    } catch (error) {
        console.error('Error loading database status:', error);
        statusDisplay.textContent = 'Error';
        startBtn.disabled = true;
        stopBtn.disabled = true;
    }
}

// Start database
async function startDatabase() {
    const startBtn = document.getElementById('start-db-btn');
    startBtn.disabled = true;
    startBtn.textContent = 'Starting...';

    try {
        const response = await fetch('/api/admin/rds/start', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            }
        });

        const data = await response.json();
        if (data.success) {
            setTimeout(loadDatabaseStatus, 1000);
        } else {
            alert('Error starting database: ' + (data.message || 'Unknown error'));
            startBtn.disabled = false;
            startBtn.textContent = 'Start Database';
            loadDatabaseStatus();
        }
    } catch (error) {
        console.error('Error starting database:', error);
        alert('Error starting database. Please try again.');
        startBtn.disabled = false;
        startBtn.textContent = 'Start Database';
        loadDatabaseStatus();
    }
}

// Stop database
async function stopDatabase() {
    const stopBtn = document.getElementById('stop-db-btn');
    stopBtn.disabled = true;
    stopBtn.textContent = 'Stopping...';

    try {
        const response = await fetch('/api/admin/rds/stop', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            }
        });

        const data = await response.json();
        if (data.success) {
            setTimeout(loadDatabaseStatus, 1000);
        } else {
            alert('Error stopping database: ' + (data.message || 'Unknown error'));
            stopBtn.disabled = false;
            stopBtn.textContent = 'Stop Database';
            loadDatabaseStatus();
        }
    } catch (error) {
        console.error('Error stopping database:', error);
        alert('Error stopping database. Please try again.');
        stopBtn.disabled = false;
        stopBtn.textContent = 'Stop Database';
        loadDatabaseStatus();
    }
}

// Load pending permissions
async function loadPendingPermissions() {
    const listEl = document.getElementById('pending-permissions-list');
    if (!listEl) {
        return;
    }

    listEl.textContent = 'Loading pending permissions...';

    try {
        const response = await fetch('/api/admin/pending-permissions');
        if (!response.ok) {
            listEl.textContent = 'Error loading pending permissions.';
            return;
        }

        const pendingPermissions = await response.json();
        if (!Array.isArray(pendingPermissions) || pendingPermissions.length === 0) {
            listEl.innerHTML = '<div class="empty-state">No pending permissions at this time.</div>';
            return;
        }

        listEl.innerHTML = '';
        pendingPermissions.forEach(function(item) {
            const pendingItem = document.createElement('div');
            pendingItem.className = 'pending-item';
            pendingItem.setAttribute('data-pending-id', item.pendingId);

            const infoDiv = document.createElement('div');
            infoDiv.className = 'pending-item-info';

            const title = document.createElement('h4');
            title.textContent = item.displayName || ('Series #' + item.seriesId);
            infoDiv.appendChild(title);

            if (item.description) {
                const desc = document.createElement('p');
                desc.textContent = 'Description: ' + item.description;
                infoDiv.appendChild(desc);
            }

            if (item.institutionName) {
                const inst = document.createElement('p');
                inst.textContent = 'Institution: ' + item.institutionName;
                infoDiv.appendChild(inst);
            }

            const actionsDiv = document.createElement('div');
            actionsDiv.className = 'pending-item-actions';

            const verifyBtn = document.createElement('button');
            verifyBtn.className = 'btn-primary';
            verifyBtn.textContent = 'Verify';
            verifyBtn.addEventListener('click', () => {
                currentPendingId = item.pendingId;
                document.getElementById('confirm-modal').classList.remove('hidden');
            });
            actionsDiv.appendChild(verifyBtn);

            pendingItem.appendChild(infoDiv);
            pendingItem.appendChild(actionsDiv);
            listEl.appendChild(pendingItem);
        });
    } catch (error) {
        console.error('Error loading pending permissions:', error);
        listEl.textContent = 'Error loading pending permissions.';
    }
}

// Handle verify confirmation
async function handleVerifyConfirm() {
    if (!currentPendingId) {
        return;
    }

    const modal = document.getElementById('confirm-modal');
    const verifyBtn = modal.querySelector('#confirm-yes-btn');
    verifyBtn.disabled = true;
    verifyBtn.textContent = 'Verifying...';

    try {
        const response = await fetch(`/api/admin/pending-permissions/${currentPendingId}/verify`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            }
        });

        const data = await response.json();
        if (data.success) {
            modal.classList.add('hidden');
            currentPendingId = null;
            await loadPendingPermissions();
        } else {
            alert('Error verifying pending permission: ' + (data.message || 'Unknown error'));
            verifyBtn.disabled = false;
            verifyBtn.textContent = 'Yes, Verify';
        }
    } catch (error) {
        console.error('Error verifying pending permission:', error);
        alert('Error verifying pending permission. Please try again.');
        verifyBtn.disabled = false;
        verifyBtn.textContent = 'Yes, Verify';
    }
}

// Load all users for admin designation
async function loadAllUsers() {
    const userSelect = document.getElementById('user-select');
    if (!userSelect) {
        return;
    }

    try {
        const response = await fetch('/api/admin/users');
        if (!response.ok) {
            console.error('Error loading users');
            return;
        }

        allUsers = await response.json();

        userSelect.innerHTML = '<option value="">-- Select a user --</option>';

        allUsers.forEach(function(user) {
            const option = document.createElement('option');
            option.value = user.userId;
            option.textContent = user.displayName;
            if (user.isAdmin) {
                option.textContent += ' (Already Admin)';
                option.disabled = true;
            }
            userSelect.appendChild(option);
        });
    } catch (error) {
        console.error('Error loading users:', error);
    }
}

// Handle add admin button click
function handleAddAdminClick() {
    const userSelect = document.getElementById('user-select');
    const selectedValue = userSelect.value;

    if (!selectedValue || selectedValue === '') {
        return;
    }

    const selectedUser = allUsers.find(u => u.userId.toString() === selectedValue);
    if (!selectedUser || selectedUser.isAdmin) {
        return;
    }

    currentUserIdToAdd = selectedUser.userId;
    const modal = document.getElementById('add-admin-modal');
    const messageEl = document.getElementById('add-admin-message');

    if (messageEl) {
        messageEl.textContent = `Are you sure you want to add "${selectedUser.displayName}" as a system admin?`;
    }

    if (modal) {
        modal.classList.remove('hidden');
    }
}

// Handle add admin confirmation
async function handleAddAdminConfirm() {
    if (!currentUserIdToAdd) {
        return;
    }

    const modal = document.getElementById('add-admin-modal');
    const confirmBtn = document.getElementById('add-admin-confirm-btn');
    confirmBtn.disabled = true;
    confirmBtn.textContent = 'Adding...';

    try {
        const response = await fetch('/api/admin/add-admin', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ userId: currentUserIdToAdd })
        });

        const data = await response.json();
        if (data.success) {
            modal.classList.add('hidden');
            currentUserIdToAdd = null;

            await loadAllUsers();

            const userSelect = document.getElementById('user-select');
            if (userSelect) {
                userSelect.value = '';
            }
            const addAdminBtn = document.getElementById('add-admin-btn');
            if (addAdminBtn) {
                addAdminBtn.disabled = true;
            }

            alert('User successfully added as admin!');
        } else {
            alert('Error adding admin: ' + (data.message || 'Unknown error'));
            confirmBtn.disabled = false;
            confirmBtn.textContent = 'Yes, Add Admin';
        }
    } catch (error) {
        console.error('Error adding admin:', error);
        alert('Error adding admin. Please try again.');
        confirmBtn.disabled = false;
        confirmBtn.textContent = 'Yes, Add Admin';
    }
}

// Handle logout
async function handleLogout() {
    try {
        const response = await fetch('/api/logout', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            credentials: 'include'
        });

        const data = await response.json();

        if (data.success) {
            window.location.href = '/index.html';
        } else {
            alert('Logout failed. Please try again.');
        }
    } catch (error) {
        console.error('Error during logout:', error);
        alert('An error occurred during logout. Please try again.');
    }
}