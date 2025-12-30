// Search functionality
document.addEventListener('DOMContentLoaded', function() {
    const searchBtn = document.getElementById('search-btn');
    const searchInput = document.getElementById('search-input');
    const searchModal = document.getElementById('search-modal-overlay');

    // Wire up search button
    if (searchBtn) {
        searchBtn.addEventListener('click', handleSearch);
    }

    // Wire up Enter key in search input
    if (searchInput) {
        searchInput.addEventListener('keypress', function(e) {
            if (e.key === 'Enter') {
                handleSearch();
            }
        });
    }

    // Close modal when clicking overlay
    if (searchModal) {
        searchModal.addEventListener('click', function(e) {
            if (e.target === searchModal) {
                closeSearchModal();
            }
        });
    }
});

async function handleSearch() {
    const searchInput = document.getElementById('search-input');
    const query = searchInput ? searchInput.value.trim() : '';

    if (!query) {
        alert('Please enter a search term.');
        return;
    }

    try {
        // Show loading state
        openSearchModal();
        displayLoadingState();

        // Call search API
        const response = await fetch(`/api/search?q=${encodeURIComponent(query)}`);
        
        if (!response.ok) {
            if (response.status === 401) {
                alert('You must be logged in to search.');
                closeSearchModal();
                return;
            }
            throw new Error('Search failed');
        }

        const data = await response.json();
        displaySearchResults(data);
    } catch (error) {
        console.error('Error performing search:', error);
        displayError('An error occurred while searching. Please try again.');
    }
}

function displayLoadingState() {
    const seriesList = document.getElementById('series-results-list');
    const recordingList = document.getElementById('recording-results-list');
    
    if (seriesList) {
        seriesList.innerHTML = '<div class="no-results-message">Loading...</div>';
    }
    if (recordingList) {
        recordingList.innerHTML = '<div class="no-results-message">Loading...</div>';
    }
}

function displaySearchResults(data) {
    const seriesResults = data.seriesResults || [];
    const recordingResults = data.recordingResults || [];

    displaySeriesResults(seriesResults);
    displayRecordingResults(recordingResults);
}

function displaySeriesResults(results) {
    const seriesList = document.getElementById('series-results-list');
    if (!seriesList) return;

    if (results.length === 0) {
        seriesList.innerHTML = '<div class="no-results-message">No Series found. Try different keywords.</div>';
        return;
    }

    seriesList.innerHTML = '';
    results.forEach(function(series) {
        const item = createSeriesResultItem(series);
        seriesList.appendChild(item);
    });
}

function displayRecordingResults(results) {
    const recordingList = document.getElementById('recording-results-list');
    if (!recordingList) return;

    if (results.length === 0) {
        recordingList.innerHTML = '<div class="no-results-message">No Recordings found. Try different keywords.</div>';
        return;
    }

    recordingList.innerHTML = '';
    results.forEach(function(recording) {
        const item = createRecordingResultItem(recording);
        recordingList.appendChild(item);
    });
}

function createSeriesResultItem(series) {
    const item = document.createElement('div');
    const hasAccess = series.hasAccess === true;
    const requiresPermission = series.requiresPermission === true;

    item.className = 'search-result-item' + (hasAccess ? '' : ' no-access');

    const title = document.createElement('div');
    title.className = 'search-result-title';
    title.textContent = series.displayName || `Series #${series.seriesId}`;
    item.appendChild(title);

    if (series.description) {
        const description = document.createElement('div');
        description.className = 'search-result-description';
        description.textContent = series.description;
        item.appendChild(description);
    }

    const meta = document.createElement('div');
    meta.className = 'search-result-meta';
    const metaParts = [];
    if (series.topicName) metaParts.push(`Topic: ${series.topicName}`);
    if (series.rebbiName) metaParts.push(`Rebbi: ${series.rebbiName}`);
    if (series.institutionName) metaParts.push(`Institution: ${series.institutionName}`);
    meta.textContent = metaParts.join(' • ');
    item.appendChild(meta);

    const accessBadge = document.createElement('div');
    accessBadge.className = 'access-badge ' + (hasAccess ? 'has-access' : 'no-access');
    if (hasAccess) {
        accessBadge.textContent = 'You have access';
    } else {
        accessBadge.textContent = 'No Access';
    }
    item.appendChild(accessBadge);

    // Only make clickable if user has access
    if (hasAccess) {
        item.style.cursor = 'pointer';
        item.addEventListener('click', function() {
            window.location.href = '/series/' + encodeURIComponent(series.seriesId);
        });
    } else {
        item.style.cursor = 'not-allowed';
        // Note: "Apply for Permission" button will be added in a separate tracer bullet
    }

    return item;
}

function createRecordingResultItem(recording) {
    const item = document.createElement('div');
    const hasAccess = recording.hasAccess === true;
    const requiresPermission = recording.requiresPermission === true;

    item.className = 'search-result-item' + (hasAccess ? '' : ' no-access');

    const title = document.createElement('div');
    title.className = 'search-result-title';
    title.textContent = recording.displayName || `Recording #${recording.recordingId}`;
    item.appendChild(title);

    if (recording.recordingDescription) {
        const description = document.createElement('div');
        description.className = 'search-result-description';
        description.textContent = recording.recordingDescription;
        item.appendChild(description);
    }

    if (recording.keywords && recording.keywords.length > 0) {
        const keywordsContainer = document.createElement('div');
        keywordsContainer.className = 'search-result-keywords';
        recording.keywords.forEach(function(keyword) {
            const tag = document.createElement('span');
            tag.className = 'keyword-tag';
            tag.textContent = keyword;
            keywordsContainer.appendChild(tag);
        });
        item.appendChild(keywordsContainer);
    }

    const meta = document.createElement('div');
    meta.className = 'search-result-meta';
    const metaParts = [];
    if (recording.topicName) metaParts.push(`Topic: ${recording.topicName}`);
    if (recording.rebbiName) metaParts.push(`Rebbi: ${recording.rebbiName}`);
    if (recording.recordedAt) metaParts.push(`Recorded: ${new Date(recording.recordedAt).toLocaleDateString()}`);
    meta.textContent = metaParts.join(' • ');
    item.appendChild(meta);

    const accessBadge = document.createElement('div');
    accessBadge.className = 'access-badge ' + (hasAccess ? 'has-access' : 'no-access');
    if (hasAccess) {
        accessBadge.textContent = 'You have access';
    } else {
        accessBadge.textContent = 'No Access';
    }
    item.appendChild(accessBadge);

    // Only make clickable if user has access
    if (hasAccess) {
        item.style.cursor = 'pointer';
        item.addEventListener('click', function() {
            // Navigate to series page (recordings are accessed through series)
            window.location.href = '/series/' + encodeURIComponent(recording.seriesId);
        });
    } else {
        item.style.cursor = 'not-allowed';
        // Note: "Apply for Permission" button will be added in a separate tracer bullet
    }

    return item;
}

function displayError(message) {
    const seriesList = document.getElementById('series-results-list');
    const recordingList = document.getElementById('recording-results-list');
    
    if (seriesList) {
        seriesList.innerHTML = '<div class="no-results-message" style="color: #d32f2f;">' + message + '</div>';
    }
    if (recordingList) {
        recordingList.innerHTML = '';
    }
}

function openSearchModal() {
    const modal = document.getElementById('search-modal-overlay');
    if (modal) {
        modal.classList.remove('hidden');
    }
}

function closeSearchModal() {
    const modal = document.getElementById('search-modal-overlay');
    if (modal) {
        modal.classList.add('hidden');
    }
}

