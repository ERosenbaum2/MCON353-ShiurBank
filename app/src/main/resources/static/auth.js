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
    checkIfAlreadyLoggedIn();
});

// Check if user is already logged in
async function checkIfAlreadyLoggedIn() {
    try {
        const response = await fetch('/api/current-user');
        if (response.ok) {
            const data = await response.json();
            if (data.loggedIn) {
                // User is already logged in, redirect to dashboard
                window.location.href = '/dashboard.html';
            }
        }
    } catch (error) {
        console.error('Error checking current user:', error);
    }
}

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
            // Redirect to dashboard
            window.location.href = '/dashboard.html';
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
            // Redirect to dashboard
            window.location.href = '/dashboard.html';
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