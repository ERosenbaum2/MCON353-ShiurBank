// Check if user is logged in on page load
document.addEventListener('DOMContentLoaded', async function() {
    await checkAuthAndLoadUser();
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