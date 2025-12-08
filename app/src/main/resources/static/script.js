// Modal functions
function openModal() {
    document.getElementById('modal-overlay').classList.remove('hidden');
}

function closeModal() {
    document.getElementById('modal-overlay').classList.add('hidden');
    // Reset forms
    document.getElementById('login-form').reset();
    document.getElementById('create-account-form').reset();
    // Clear all error messages
    const errorMessages = document.querySelectorAll('.error-message');
    errorMessages.forEach(span => span.textContent = '');
    // Clear error styling
    const inputs = document.querySelectorAll('#create-account-form input, #create-account-form select');
    inputs.forEach(input => input.classList.remove('error'));
}

// Close modal when clicking outside
document.addEventListener('DOMContentLoaded', function() {
    document.getElementById('modal-overlay').addEventListener('click', function(e) {
        if (e.target === this) {
            closeModal();
        }
    });

    // Tab switching
    document.getElementById('login-tab').addEventListener('change', function() {
        if (this.checked) {
            document.getElementById('login-content').style.display = 'block';
            document.getElementById('create-content').style.display = 'none';
        }
    });

    document.getElementById('create-tab').addEventListener('change', function() {
        if (this.checked) {
            document.getElementById('login-content').style.display = 'none';
            document.getElementById('create-content').style.display = 'block';
        }
    });

    // Initialize tab display
    document.getElementById('login-content').style.display = 'block';
    document.getElementById('create-content').style.display = 'none';

    // Initialize on page load
    loadInstitutions();
    checkCurrentUser();
});

// Load institutions on page load
let institutions = [];
async function loadInstitutions() {
    try {
        const response = await fetch('/api/institutions');
        if (response.ok) {
            institutions = await response.json();
            const container = document.getElementById('institutions-container');
            if (!container) {
                console.error('institutions-container not found!');
                return;
            }
            container.innerHTML = '';
            institutions.forEach(inst => {
                // Create a wrapper div for each checkbox
                const wrapper = document.createElement('div');
                wrapper.style.marginBottom = '0.5rem';
                wrapper.style.display = 'flex';
                wrapper.style.alignItems = 'center';

                const checkbox = document.createElement('input');
                checkbox.type = 'checkbox';
                checkbox.value = inst.instId;
                checkbox.id = `institution-${inst.instId}`;
                checkbox.name = 'institution';
                checkbox.style.marginRight = '0.5rem';
                checkbox.style.cursor = 'pointer';

                const label = document.createElement('label');
                label.setAttribute('for', `institution-${inst.instId}`);
                label.textContent = inst.name;
                label.style.cursor = 'pointer';

                wrapper.appendChild(checkbox);
                wrapper.appendChild(label);
                container.appendChild(wrapper);
            });
        } else {
            console.error('Failed to load institutions:', response.status);
        }
    } catch (error) {
        console.error('Error loading institutions:', error);
    }
}

// Check current user session on page load
async function checkCurrentUser() {
    try {
        const response = await fetch('/api/current-user');
        if (response.ok) {
            const data = await response.json();
            if (data.loggedIn) {
                showUserGreeting(data.username);
            }
        }
    } catch (error) {
        console.error('Error checking current user:', error);
    }
}

// Form handlers
async function handleLogin(event) {
    event.preventDefault();
    const username = document.getElementById('login-username').value;
    const password = document.getElementById('login-password').value;

    try {
        const response = await fetch('/api/login', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ username, password })
        });

        const data = await response.json();

        if (data.success) {
            showUserGreeting(data.username);
            closeModal();
        } else {
            alert(data.message || 'Login failed');
        }
    } catch (error) {
        console.error('Error during login:', error);
        alert('An error occurred during login. Please try again.');
    }
}

async function handleCreateAccount(event) {
    event.preventDefault();

    // Get selected institutions from checkboxes BEFORE clearing errors
    const institutionCheckboxes = document.querySelectorAll('#institutions-container input[type="checkbox"]:checked');

    const selectedInstitutions = Array.from(institutionCheckboxes).map(cb => {
        return parseInt(cb.value);
    });

    // Clear previous errors (AFTER reading checkbox values)
    clearErrors();

    // Get form values
    const title = document.getElementById('title').value;
    const firstName = document.getElementById('firstName').value;
    const lastName = document.getElementById('lastName').value;
    const username = document.getElementById('username').value;
    const password = document.getElementById('password').value;
    const email = document.getElementById('email').value;

    const accountData = {
        title: title,
        firstName: firstName,
        lastName: lastName,
        username: username,
        password: password,
        email: email,
        institutions: selectedInstitutions
    };

    try {
        const response = await fetch('/api/create-account', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(accountData)
        });

        const data = await response.json();

        if (data.success) {
            showUserGreeting(data.username);
            closeModal();
        } else {
            // Display validation errors
            if (data.errors) {
                displayErrors(data.errors);
            } else {
                alert(data.message || 'Account creation failed');
            }
        }
    } catch (error) {
        console.error('Error during account creation:', error);
        alert('An error occurred during account creation. Please try again.');
    }
}

function clearErrors() {
    const errorMessages = document.querySelectorAll('.error-message');
    errorMessages.forEach(span => span.textContent = '');
    const inputs = document.querySelectorAll('#create-account-form input, #create-account-form select');
    inputs.forEach(input => input.classList.remove('error'));
    // Don't clear checkboxes here - that should only happen when the form is reset
}

function displayErrors(errors) {
    for (const [field, message] of Object.entries(errors)) {
        const errorSpan = document.getElementById(field + '-error');
        const input = document.getElementById(field);
        if (errorSpan) {
            errorSpan.textContent = message;
        }
        if (input) {
            input.classList.add('error');
        }
    }
}

function showUserGreeting(username) {
    document.getElementById('username-display').textContent = username;
    document.getElementById('user-greeting').classList.remove('hidden');
    document.getElementById('main-content').style.display = 'none';
}

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
            // Hide the user greeting
            document.getElementById('user-greeting').classList.add('hidden');

            // Show main content with logout message
            document.getElementById('main-content').style.display = 'block';
            const homepageContent = document.querySelector('.homepage-content');
            homepageContent.innerHTML = '<h2>You\'ve been logged out successfully</h2>';

            // After 3 seconds, reset to initial view
            setTimeout(() => {
                homepageContent.innerHTML = `
                    <img src="Full ShiurBank Logo.png" alt="ShiurBank" class="homepage-logo">
                    <h2>Welcome to ShiurBank</h2>
                    <p>Your source for Shiurim</p>
                    <button id="login-btn" class="btn-primary" onclick="openModal()">Login/Create Account</button>
                `;
            }, 3000);
        } else {
            alert('Logout failed. Please try again.');
        }
    } catch (error) {
        console.error('Error during logout:', error);
        alert('An error occurred during logout. Please try again.');
    }
}