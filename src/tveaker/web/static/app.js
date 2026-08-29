/**
 * TVeaker Ultra-Polished Interactive Client
 */

// Toast notification helper
function showToast(message, type = 'info', duration = 4000) {
  let container = document.getElementById('toast-container');
  if (!container) {
    container = document.createElement('div');
    container.id = 'toast-container';
    document.body.appendChild(container);
  }

  const toast = document.createElement('div');
  toast.className = `toast ${type}`;
  
  let iconSvg = '';
  if (type === 'success') {
    iconSvg = '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#10b981" stroke-width="2.5"><polyline points="20 6 9 17 4 12"></polyline></svg>';
  } else if (type === 'error') {
    iconSvg = '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#f43f5e" stroke-width="2.5"><circle cx="12" cy="12" r="10"></circle><line x1="15" y1="9" x2="9" y2="15"></line><line x1="9" y1="9" x2="15" y2="15"></line></svg>';
  } else {
    iconSvg = '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#38bdf8" stroke-width="2.5"><circle cx="12" cy="12" r="10"></circle><line x1="12" y1="16" x2="12" y2="12"></line><line x1="12" y1="8" x2="12.01" y2="8"></line></svg>';
  }

  toast.innerHTML = `
    ${iconSvg}
    <div style="flex-grow: 1;">${message}</div>
    <button onclick="this.parentElement.remove()" style="background: none; border: none; color: #94a3b8; cursor: pointer; padding: 4px;">✕</button>
  `;

  container.appendChild(toast);

  // Trigger animation
  requestAnimationFrame(() => {
    toast.classList.add('show');
  });

  setTimeout(() => {
    toast.classList.remove('show');
    setTimeout(() => toast.remove(), 400);
  }, duration);
}

// Sync trigger
async function triggerSync(mode = 'incremental') {
  const btn = document.getElementById('sync-trigger-btn');
  const originalText = btn ? btn.innerHTML : '';
  if (btn) {
    btn.disabled = true;
    btn.innerHTML = '<span class="pulse-dot" style="display:inline-block; margin-right: 6px;"></span> Syncing...';
  }

  showToast(`Starting ${mode} sync with Trakt...`, 'info', 2500);

  try {
    const res = await fetch('/api/v1/sync/trigger', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ mode })
    });
    const data = await res.json();
    if (res.ok) {
      const fetchedCount = Object.values(data.fetched || {}).reduce((a, b) => a + b, 0);
      showToast(`Sync completed: ${data.status} (${fetchedCount} items processed)`, 'success', 3000);
      setTimeout(() => window.location.reload(), 1200);
    } else {
      showToast(`Sync failed: ${data.detail || 'Unknown error'}`, 'error', 5000);
    }
  } catch (err) {
    showToast(`Connection error: ${err.message}`, 'error', 5000);
  } finally {
    if (btn) {
      btn.disabled = false;
      btn.innerHTML = originalText;
    }
  }
}

// Live show status update
async function updateShowStatus(showId, status) {
  try {
    const res = await fetch(`/api/v1/shows/${showId}`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ status })
    });
    if (res.ok) {
      showToast(`Show status updated to ${status}`, 'success', 2000);
      setTimeout(() => window.location.reload(), 600);
    } else {
      const err = await res.json();
      showToast(`Failed: ${err.detail}`, 'error');
    }
  } catch (err) {
    showToast(`Error: ${err.message}`, 'error');
  }
}

// Live pace update
async function updateShowPace(showId, currentPace) {
  const paceStr = prompt('Enter manual episodes per week (e.g. 3.5, or leave blank to reset to auto):', currentPace || '');
  if (paceStr === null) return;
  const pace = paceStr.trim() === '' ? null : parseFloat(paceStr);
  if (pace !== null && (isNaN(pace) || pace <= 0)) {
    showToast('Pace must be a positive number', 'error');
    return;
  }

  try {
    const res = await fetch(`/api/v1/shows/${showId}`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ manual_episodes_per_week: pace })
    });
    if (res.ok) {
      showToast('Weekly velocity updated', 'success', 2000);
      setTimeout(() => window.location.reload(), 600);
    } else {
      const err = await res.json();
      showToast(`Failed: ${err.detail}`, 'error');
    }
  } catch (err) {
    showToast(`Error: ${err.message}`, 'error');
  }
}

// Toggle specials inclusion
async function toggleSpecials(showId, currentVal) {
  try {
    const res = await fetch(`/api/v1/shows/${showId}`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ include_specials: !currentVal })
    });
    if (res.ok) {
      showToast(`Specials ${!currentVal ? 'included' : 'excluded'}`, 'success', 1500);
      setTimeout(() => window.location.reload(), 500);
    }
  } catch (err) {
    showToast(`Error: ${err.message}`, 'error');
  }
}

// Send recommendation feedback with animated card dismissal
async function sendFeedback(runId, candidateId, action) {
  const safeId = candidateId.replace(':', '-');
  const card = document.getElementById(`rec-${safeId}`);
  if (card) {
    card.style.opacity = '0.4';
    card.style.pointerEvents = 'none';
  }

  try {
    const res = await fetch('/api/v1/recommendations/feedback', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ run_id: runId, candidate_id: candidateId, action })
    });
    if (res.ok) {
      if (card) {
        card.style.transform = 'scale(0.9) translateY(10px)';
        card.style.transition = 'all 0.3s ease';
        setTimeout(() => card.remove(), 300);
      }
      showToast(`Feedback recorded: ${action}`, 'success', 2000);
    } else {
      if (card) {
        card.style.opacity = '1';
        card.style.pointerEvents = 'auto';
      }
      showToast('Failed to record feedback', 'error');
    }
  } catch (err) {
    if (card) {
      card.style.opacity = '1';
      card.style.pointerEvents = 'auto';
    }
    showToast(`Error: ${err.message}`, 'error');
  }
}

// Live client-side instant search filter
function setupInstantSearch(inputId, targetSelector) {
  const input = document.getElementById(inputId);
  if (!input) return;

  input.addEventListener('input', (e) => {
    const query = e.target.value.toLowerCase().trim();
    const items = document.querySelectorAll(targetSelector);

    items.forEach((item) => {
      const text = item.textContent.toLowerCase();
      if (!query || text.includes(query)) {
        item.style.display = '';
      } else {
        item.style.display = 'none';
      }
    });
  });
}

document.addEventListener('DOMContentLoaded', () => {
  setupInstantSearch('shows-search-input', '.table-container tbody tr');
  setupInstantSearch('shows-grid-search', '.show-grid-card');
  setupInstantSearch('history-search-input', '.history-row');
});
