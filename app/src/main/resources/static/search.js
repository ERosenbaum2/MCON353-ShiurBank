let currentQuery = '';
let currentPage = 0;
const PAGE_SIZE = 20;
let totalPages = 0;
let currentApplicationSeriesId = null;

document.addEventListener('DOMContentLoaded', async function() {
    await checkAuthAndShowPage();

    // Get query from URL parameters
    const urlParams = new URLSearchParams(window.location.search);
    const query = urlParams.get('q');

    if (query) {
        currentQuery = query;
        document.getElementById('search-input').value = query;
        await performSearch();
    }

    // Set up enter key handler for search input
    document.getElementById('search-input').addEventListener('keypress', function(e) {
        if (e.key === 'Enter') {
            performSearch();
        }
    });
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
        document.getElementById('search-main').classList.remove('hidden');
    } catch (error) {
        console.error('Error checking auth:', error);
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
        console.error('Error during logout:', error);
        alert('An error occurred during logout.');
    }
}

async function performSearch(page = 0) {
    const searchInput = document.getElementById('search-input');
    const query = searchInput.value.trim();

    if (!query) {
        alert('Please enter a search query');
        return;
    }

    currentQuery = query;
    currentPage = page;

    // Update URL without reload
    const url = new URL(window.location);
    url.searchParams.set('q', query);
    if (page > 0) {
        url.searchParams.set('page', page);
    } else {
        url.searchParams.delete('page');
    }
    window.history.pushState({}, '', url);

    // Show loading, hide everything else
    showLoading();
    hideResults();
    hideNoResults();
    hideError();

    try {
        const response = await fetch(`/api/search?q=${encodeURIComponent(query)}&page=${page}&pageSize=${PAGE_SIZE}`);

        if (!response.ok) {
            throw new Error('Search request failed');
        }

        const data = await response.json();

        hideLoading();

        if (data.success) {
            if (data.results && data.results.length > 0) {
                displayResults(data.results, data.query);
                totalPages = data.totalPages;
                displayPagination(data.page, data.totalPages, data.totalResults);
            } else {
                showNoResults();
            }
        } else {
            showError(data.message || 'Search failed');
        }
    } catch (error) {
        console.error('Error performing search:', error);
        hideLoading();
        showError('An error occurred while searching. Please try again.');
    }
}

function displayResults(results, query) {
    const container = document.getElementById('results-list');
    const resultsContainer = document.getElementById('results-container');

    container.innerHTML = '';

    // Update title
    document.getElementById('results-title').textContent = `Search Results for "${query}"`;

    results.forEach(result => {
        const resultItem = createResultItem(result);
        container.appendChild(resultItem);
    });

    resultsContainer.classList.remove('hidden');
}

function createResultItem(result) {
    const item = document.createElement('div');
    item.className = 'result-item';

    if (!result.hasAccess) {
        item.classList.add('no-access');
    }

    // Type badge
    const typeBadge = document.createElement('div');
    typeBadge.className = `type-badge ${result.type.toLowerCase()}`;
    typeBadge.textContent = result.type === 'SERIES' ? 'Series' : 'Recording';

    // Content container
    const content = document.createElement('div');
    content.className = 'result-content';

    // Title
    const title = document.createElement('div');
    title.className = 'result-title';

    if (result.type === 'SERIES') {
        title.textContent = result.rebbiName ? `${result.rebbiName} - ${result.topicName}` : `Series #${result.id}`;
    } else {
        title.textContent = result.title || `Recording #${result.id}`;
    }

    // Metadata
    const metadata = document.createElement('div');
    metadata.className = 'result-metadata';

    const metaParts = [];
    if (result.rebbiName) metaParts.push(`Rebbi: ${result.rebbiName}`);
    if (result.topicName) metaParts.push(`Topic: ${result.topicName}`);
    if (result.institutionName) metaParts.push(`Institution: ${result.institutionName}`);
    if (result.recordedAt) {
        const date = new Date(result.recordedAt);
        metaParts.push(`Recorded: ${formatDate(date)}`);
    }

    metadata.textContent = metaParts.join(' • ');

    // Description
    const description = document.createElement('div');
    description.className = 'result-description';
    description.textContent = result.description || 'No description available.';

    content.appendChild(title);
    content.appendChild(metadata);
    content.appendChild(description);

    // Action container
    const actions = document.createElement('div');
    actions.className = 'result-actions';

    if (result.hasAccess) {
        const viewBtn = document.createElement('button');
        viewBtn.className = 'btn-primary btn-view';
        viewBtn.textContent = result.type === 'SERIES' ? 'View Series' : 'Play Recording';
        viewBtn.onclick = () => handleResultClick(result);
        actions.appendChild(viewBtn);
    } else {
        const noAccessDiv = document.createElement('div');
        noAccessDiv.className = 'no-access-label';
        noAccessDiv.textContent = 'No Access';

        const applyBtn = document.createElement('button');
        applyBtn.className = 'btn-secondary btn-apply';
        applyBtn.textContent = 'Apply to Series';
        applyBtn.onclick = () => openApplyModal(result);

        actions.appendChild(noAccessDiv);
        actions.appendChild(applyBtn);
    }

    item.appendChild(typeBadge);
    item.appendChild(content);
    item.appendChild(actions);

    return item;
}

function handleResultClick(result) {
    if (result.type === 'SERIES') {
        window.location.href = `/series/${result.id}`;
    } else if (result.type === 'RECORDING') {
        // Navigate to series page with recording ID
        window.location.href = `/series/${result.seriesId}?recording=${result.id}`;
    }
}

async function openApplyModal(result) {
    // Determine the series ID
    const seriesId = result.type === 'SERIES' ? result.id : result.seriesId;
    currentApplicationSeriesId = seriesId;

    // Show loading state in modal
    const modal = document.getElementById('apply-modal');
    const modalBody = modal.querySelector('.modal-body');
    modalBody.innerHTML = '<p style="text-align: center; padding: 2rem;">Loading series information...</p>';
    modal.classList.add('active');

    try {
        const response = await fetch(`/api/series/${seriesId}/application-info`);
        const data = await response.json();

        if (!data.success) {
            modalBody.innerHTML = `<p style="color: #e74c3c;">${data.message || 'Error loading series information.'}</p>`;
            return;
        }

        // Check if already a participant
        if (data.isParticipant) {
            modalBody.innerHTML = '<p style="color: #27ae60; font-weight: 600;">You are already a participant in this series.</p>';
            updateModalFooter(false, true);
            return;
        }

        // Check if has pending application
        if (data.hasPendingApplication) {
            modalBody.innerHTML = '<p style="color: #f39c12; font-weight: 600;">You already have a pending application for this series. Please wait for the Gabbai to review your request.</p>';
            updateModalFooter(false, true);
            return;
        }

        // Build modal content
        const seriesInfo = data.seriesInfo;
        const gabbaim = data.gabbaim;

        let html = '<div style="margin-bottom: 1.5rem;">';
        html += `<h3 style="color: #1B4965; margin-bottom: 1rem;">Series Information</h3>`;
        html += `<p><strong>Rebbi:</strong> ${seriesInfo.rebbiName}</p>`;
        html += `<p><strong>Topic:</strong> ${seriesInfo.topicName}</p>`;
        html += `<p><strong>Institution:</strong> ${seriesInfo.institutionName}</p>`;
        if (seriesInfo.description) {
            html += `<p><strong>Description:</strong> ${seriesInfo.description}</p>`;
        }
        html += '</div>';

        if (gabbaim && gabbaim.length > 0) {
            html += '<div style="margin-bottom: 1.5rem; padding: 1rem; background-color: #f8f9fa; border-radius: 4px;">';
            html += `<h3 style="color: #1B4965; margin-bottom: 1rem;">Gabbai${gabbaim.length > 1 ? 'im' : ''}</h3>`;
            gabbaim.forEach(gabbai => {
                html += `<p><strong>${gabbai.fullName}</strong><br>Email: <a href="mailto:${gabbai.email}">${gabbai.email}</a></p>`;
            });
            html += '</div>';
        }

        html += '<p style="font-size: 1.05rem; margin-top: 1rem;">Would you like to apply to join this series?</p>';

        modalBody.innerHTML = html;
        updateModalFooter(true, false);

    } catch (error) {
        console.error('Error loading application info:', error);
        modalBody.innerHTML = '<p style="color: #e74c3c;">An error occurred while loading series information. Please try again.</p>';
        updateModalFooter(false, true);
    }
}

function updateModalFooter(showApplyButton, showCloseOnly) {
    const modalFooter = document.querySelector('#apply-modal .modal-footer');

    if (showCloseOnly) {
        modalFooter.innerHTML = '<button type="button" class="btn-primary" onclick="closeApplyModal()">Close</button>';
    } else if (showApplyButton) {
        modalFooter.innerHTML = `
            <button type="button" class="btn-secondary" onclick="closeApplyModal()">Cancel</button>
            <button type="button" class="btn-primary" onclick="submitApplication()">Apply</button>
        `;
    } else {
        modalFooter.innerHTML = '<button type="button" class="btn-primary" onclick="closeApplyModal()">Close</button>';
    }
}

async function submitApplication() {
    if (!currentApplicationSeriesId) {
        alert('Error: No series selected');
        return;
    }

    const modal = document.getElementById('apply-modal');
    const modalBody = modal.querySelector('.modal-body');
    modalBody.innerHTML = '<p style="text-align: center; padding: 2rem;">Submitting application...</p>';
    updateModalFooter(false, false);

    try {
        const response = await fetch(`/api/series/${currentApplicationSeriesId}/apply`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' }
        });

        const data = await response.json();

        if (data.success) {
            if (data.autoApproved) {
                modalBody.innerHTML = '<p style="color: #27ae60; font-weight: 600; text-align: center; padding: 2rem;">✓ You have been successfully added to the series!</p>';
            } else {
                modalBody.innerHTML = '<p style="color: #27ae60; font-weight: 600; text-align: center; padding: 2rem;">✓ Your application has been submitted successfully! The Gabbai will review your request.</p>';
            }
            updateModalFooter(false, true);

            // Refresh search results after a delay
            setTimeout(() => {
                closeApplyModal();
                performSearch(currentPage);
            }, 2000);
        } else {
            modalBody.innerHTML = `<p style="color: #e74c3c; text-align: center; padding: 2rem;">${data.message || 'Failed to submit application.'}</p>`;
            updateModalFooter(false, true);
        }

    } catch (error) {
        console.error('Error submitting application:', error);
        modalBody.innerHTML = '<p style="color: #e74c3c; text-align: center; padding: 2rem;">An error occurred while submitting your application. Please try again.</p>';
        updateModalFooter(false, true);
    }
}

function closeApplyModal() {
    document.getElementById('apply-modal').classList.remove('active');
    currentApplicationSeriesId = null;
}

function displayPagination(currentPage, totalPages, totalResults) {
    const container = document.getElementById('pagination-container');

    if (totalPages <= 1) {
        container.classList.add('hidden');
        return;
    }

    container.innerHTML = '';
    container.classList.remove('hidden');

    const pagination = document.createElement('div');
    pagination.className = 'pagination';

    // Results info
    const info = document.createElement('div');
    info.className = 'pagination-info';
    const start = currentPage * PAGE_SIZE + 1;
    const end = Math.min((currentPage + 1) * PAGE_SIZE, totalResults);
    info.textContent = `Showing ${start}-${end} of ${totalResults} results`;

    // Buttons container
    const buttons = document.createElement('div');
    buttons.className = 'pagination-buttons';

    // Previous button
    const prevBtn = document.createElement('button');
    prevBtn.className = 'btn-page';
    prevBtn.textContent = '← Previous';
    prevBtn.disabled = currentPage === 0;
    prevBtn.onclick = () => performSearch(currentPage - 1);
    buttons.appendChild(prevBtn);

    // Page numbers
    const maxPageButtons = 5;
    let startPage = Math.max(0, currentPage - Math.floor(maxPageButtons / 2));
    let endPage = Math.min(totalPages, startPage + maxPageButtons);

    if (endPage - startPage < maxPageButtons) {
        startPage = Math.max(0, endPage - maxPageButtons);
    }

    if (startPage > 0) {
        const firstBtn = document.createElement('button');
        firstBtn.className = 'btn-page';
        firstBtn.textContent = '1';
        firstBtn.onclick = () => performSearch(0);
        buttons.appendChild(firstBtn);

        if (startPage > 1) {
            const ellipsis = document.createElement('span');
            ellipsis.className = 'pagination-ellipsis';
            ellipsis.textContent = '...';
            buttons.appendChild(ellipsis);
        }
    }

    for (let i = startPage; i < endPage; i++) {
        const pageBtn = document.createElement('button');
        pageBtn.className = 'btn-page';
        if (i === currentPage) {
            pageBtn.classList.add('active');
        }
        pageBtn.textContent = i + 1;
        pageBtn.onclick = () => performSearch(i);
        buttons.appendChild(pageBtn);
    }

    if (endPage < totalPages) {
        if (endPage < totalPages - 1) {
            const ellipsis = document.createElement('span');
            ellipsis.className = 'pagination-ellipsis';
            ellipsis.textContent = '...';
            buttons.appendChild(ellipsis);
        }

        const lastBtn = document.createElement('button');
        lastBtn.className = 'btn-page';
        lastBtn.textContent = totalPages;
        lastBtn.onclick = () => performSearch(totalPages - 1);
        buttons.appendChild(lastBtn);
    }

    // Next button
    const nextBtn = document.createElement('button');
    nextBtn.className = 'btn-page';
    nextBtn.textContent = 'Next →';
    nextBtn.disabled = currentPage >= totalPages - 1;
    nextBtn.onclick = () => performSearch(currentPage + 1);
    buttons.appendChild(nextBtn);

    pagination.appendChild(info);
    pagination.appendChild(buttons);
    container.appendChild(pagination);
}

function formatDate(date) {
    const month = date.getMonth() + 1;
    const day = date.getDate();
    const year = date.getFullYear();
    return `${month}/${day}/${year}`;
}

function showLoading() {
    document.getElementById('search-loading').classList.remove('hidden');
}

function hideLoading() {
    document.getElementById('search-loading').classList.add('hidden');
}

function showResults() {
    document.getElementById('results-container').classList.remove('hidden');
}

function hideResults() {
    document.getElementById('results-container').classList.add('hidden');
}

function showNoResults() {
    document.getElementById('no-results').classList.remove('hidden');
}

function hideNoResults() {
    document.getElementById('no-results').classList.add('hidden');
}

function showError(message) {
    const errorDiv = document.getElementById('search-error');
    errorDiv.textContent = message;
    errorDiv.classList.remove('hidden');
}

function hideError() {
    document.getElementById('search-error').classList.add('hidden');
}