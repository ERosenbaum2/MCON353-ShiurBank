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
    wireDbControlModal();
});

// Database Control Modal Functions
let dbControlPasswordVerified = false;

function openDbControlModal() {
    const modal = document.getElementById('db-control-modal');
    if (modal) {
        modal.classList.remove('hidden');
        // Reset state
        dbControlPasswordVerified = false;
        document.getElementById('db-control-password-section').style.display = 'block';
        document.getElementById('db-control-content-section').style.display = 'none';
        document.getElementById('db-control-password').value = '';
        document.getElementById('db-control-password-error').style.display = 'none';
        document.getElementById('db-control-password').focus();
    }
}

function closeDbControlModal() {
    const modal = document.getElementById('db-control-modal');
    if (modal) {
        modal.classList.add('hidden');
        dbControlPasswordVerified = false;
        document.getElementById('db-control-password').value = '';
    }
}

function wireDbControlModal() {
    // Close modal when clicking outside
    const modal = document.getElementById('db-control-modal');
    if (modal) {
        modal.addEventListener('click', function(e) {
            if (e.target === this) {
                closeDbControlModal();
            }
        });
    }

    // Password submit
    const passwordSubmit = document.getElementById('db-control-password-submit');
    if (passwordSubmit) {
        passwordSubmit.addEventListener('click', verifyDbControlPassword);
    }

    // Enter key on password input
    const passwordInput = document.getElementById('db-control-password');
    if (passwordInput) {
        passwordInput.addEventListener('keypress', function(e) {
            if (e.key === 'Enter') {
                verifyDbControlPassword();
            }
        });
    }

    // Database control buttons
    const checkBtn = document.getElementById('db-control-check-btn');
    const startBtn = document.getElementById('db-control-start-btn');
    const stopBtn = document.getElementById('db-control-stop-btn');

    if (checkBtn) {
        checkBtn.addEventListener('click', loadDbControlStatus);
    }
    if (startBtn) {
        startBtn.addEventListener('click', startDbControlDatabase);
    }
    if (stopBtn) {
        stopBtn.addEventListener('click', stopDbControlDatabase);
    }
}

async function verifyDbControlPassword() {
    const passwordInput = document.getElementById('db-control-password');
    const errorEl = document.getElementById('db-control-password-error');
    const submitBtn = document.getElementById('db-control-password-submit');
    
    if (!passwordInput) return;
    
    const password = passwordInput.value.trim();
    if (!password) {
        errorEl.textContent = 'Please enter the admin password.';
        errorEl.style.display = 'block';
        return;
    }
    
    submitBtn.disabled = true;
    submitBtn.textContent = 'Verifying...';
    errorEl.style.display = 'none';

    try {
        const response = await fetch('/api/admin/rds/verify-password', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ adminPassword: password })
        });

        const data = await response.json();
        
        if (!data.success) {
            errorEl.textContent = data.message || 'Invalid password. Please try again.';
            errorEl.style.display = 'block';
            passwordInput.value = '';
            passwordInput.focus();
            submitBtn.disabled = false;
            submitBtn.textContent = 'Continue';
            return;
        }

        // Password is correct - show the database control section
        dbControlPasswordVerified = true;
        document.getElementById('db-control-password-section').style.display = 'none';
        document.getElementById('db-control-content-section').style.display = 'block';
        
        // Store password in sessionStorage for this session
        sessionStorage.setItem('dbControlPassword', password);
        
        // Load database status
        loadDbControlStatus();
    } catch (error) {
        console.error('Error verifying password:', error);
        errorEl.textContent = 'Error verifying password. Please try again.';
        errorEl.style.display = 'block';
        submitBtn.disabled = false;
        submitBtn.textContent = 'Continue';
    }
}

async function loadDbControlStatus() {
    const statusDisplay = document.getElementById('db-control-status');
    const startBtn = document.getElementById('db-control-start-btn');
    const stopBtn = document.getElementById('db-control-stop-btn');
    const messageEl = document.getElementById('db-control-message');

    if (!statusDisplay) return;

    try {
        statusDisplay.textContent = 'Checking...';
        messageEl.textContent = '';
        
        const response = await fetch('/api/admin/rds/status');
        
        if (!response.ok) {
            statusDisplay.textContent = 'Unknown';
            messageEl.textContent = 'Cannot check status. Database might be down.';
            startBtn.disabled = false;
            stopBtn.disabled = true;
            return;
        }

        const data = await response.json();
        if (data.success) {
            const status = data.status || 'unknown';
            statusDisplay.textContent = status;
            
            if (status.toLowerCase() === 'stopped') {
                startBtn.disabled = false;
                stopBtn.disabled = true;
                messageEl.textContent = 'Database is stopped.';
            } else if (status.toLowerCase() === 'available' || status.toLowerCase() === 'running') {
                startBtn.disabled = true;
                stopBtn.disabled = false;
                messageEl.textContent = 'Database is running.';
            } else if (status.toLowerCase() === 'starting') {
                startBtn.disabled = true;
                stopBtn.disabled = true;
                messageEl.textContent = 'Database is starting. Please wait 2-5 minutes...';
            } else if (status.toLowerCase() === 'stopping') {
                startBtn.disabled = true;
                stopBtn.disabled = true;
                messageEl.textContent = 'Database is stopping. Please wait...';
            } else {
                startBtn.disabled = false;
                stopBtn.disabled = false;
                messageEl.textContent = `Status: ${status}`;
            }
        } else {
            statusDisplay.textContent = 'Error';
            messageEl.textContent = 'Could not check status.';
            startBtn.disabled = false;
            stopBtn.disabled = true;
        }
    } catch (error) {
        console.error('Error loading database status:', error);
        statusDisplay.textContent = 'Connection Error';
        messageEl.textContent = 'Cannot connect. Database is likely down.';
        startBtn.disabled = false;
        stopBtn.disabled = true;
    }
}

async function startDbControlDatabase() {
    const startBtn = document.getElementById('db-control-start-btn');
    const messageEl = document.getElementById('db-control-message');
    
    if (!startBtn) return;
    
    const password = sessionStorage.getItem('dbControlPassword');
    if (!password) {
        messageEl.textContent = 'Password not found. Please close and reopen the modal.';
        return;
    }
    
    startBtn.disabled = true;
    startBtn.textContent = 'Starting...';
    messageEl.textContent = 'Starting database... This may take 2-5 minutes.';

    try {
        const response = await fetch('/api/admin/rds/start-public', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ adminPassword: password })
        });

        const data = await response.json();
        if (data.success) {
            messageEl.textContent = 'Database start initiated! Please wait 2-5 minutes.';
            setTimeout(loadDbControlStatus, 2000);
        } else {
            messageEl.textContent = 'Error: ' + (data.message || 'Failed to start database');
            startBtn.disabled = false;
            startBtn.textContent = 'Start';
            loadDbControlStatus();
        }
    } catch (error) {
        console.error('Error starting database:', error);
        messageEl.textContent = 'Error starting database. Please try again.';
        startBtn.disabled = false;
        startBtn.textContent = 'Start';
    }
}

async function stopDbControlDatabase() {
    const stopBtn = document.getElementById('db-control-stop-btn');
    const messageEl = document.getElementById('db-control-message');
    
    if (!stopBtn) return;
    
    const password = sessionStorage.getItem('dbControlPassword');
    if (!password) {
        messageEl.textContent = 'Password not found. Please close and reopen the modal.';
        return;
    }
    
    stopBtn.disabled = true;
    stopBtn.textContent = 'Stopping...';
    messageEl.textContent = 'Stopping database...';

    try {
        const response = await fetch('/api/admin/rds/stop-public', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ adminPassword: password })
        });

        const data = await response.json();
        if (data.success) {
            messageEl.textContent = 'Database stop initiated.';
            setTimeout(loadDbControlStatus, 2000);
        } else {
            messageEl.textContent = 'Error: ' + (data.message || 'Failed to stop database');
            stopBtn.disabled = false;
            stopBtn.textContent = 'Stop';
            loadDbControlStatus();
        }
    } catch (error) {
        console.error('Error stopping database:', error);
        messageEl.textContent = 'Error stopping database. Please try again.';
        stopBtn.disabled = false;
        stopBtn.textContent = 'Stop';
    }
}

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

        // Check if response is not OK (might be 500 if DB is down)
        if (!response.ok && response.status === 500) {
            alert('Database connection error. The database might be down. Please check the database status and start it if needed.');
            loadHomepageDatabaseStatus(); // Refresh status
            return;
        }

        const data = await response.json();

        if (data.success) {
            // Redirect to dashboard
            window.location.href = '/dashboard.html';
        } else {
            // Check if error message suggests DB connection issue
            const errorMsg = data.message || 'Login failed';
            if (errorMsg.toLowerCase().includes('connection') || 
                errorMsg.toLowerCase().includes('database') ||
                errorMsg.toLowerCase().includes('sql')) {
                alert('Database connection error: ' + errorMsg + '\n\nPlease check the database status and start it if needed.');
                loadHomepageDatabaseStatus();
            } else {
                alert(errorMsg);
            }
        }
    } catch (error) {
        console.error('Error during login:', error);
        alert('Connection error. The database might be down. Please check the database status above and start it if needed.');
        loadHomepageDatabaseStatus();
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