/**
 * TVeaker Ultra-Polished Interactive Client • Apple Pro Design
 */

// Toast notification helper
function showToast(message, type = 'info', duration = 3500) {
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
    iconSvg =
      '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#10b981" stroke-width="2.5"><polyline points="20 6 9 17 4 12"></polyline></svg>';
  } else if (type === 'error') {
    iconSvg =
      '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#f43f5e" stroke-width="2.5"><circle cx="12" cy="12" r="10"></circle><line x1="15" y1="9" x2="9" y2="15"></line><line x1="9" y1="9" x2="15" y2="15"></line></svg>';
  } else {
    iconSvg =
      '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#38bdf8" stroke-width="2.5"><circle cx="12" cy="12" r="10"></circle><line x1="12" y1="16" x2="12" y2="12"></line><line x1="12" y1="8" x2="12.01" y2="8"></line></svg>';
  }

  toast.innerHTML = `
    ${iconSvg}
    <div style="flex-grow: 1;">${message}</div>
    <button onclick="this.parentElement.remove()" style="background: none; border: none; color: #94a3b8; cursor: pointer; padding: 4px;">✕</button>
  `;

  container.appendChild(toast);

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
    btn.innerHTML =
      '<span class="pulse-dot" style="display:inline-block; margin-right: 6px;"></span> Syncing...';
  }

  showToast(`Starting ${mode} sync with Trakt...`, 'info', 2500);

  try {
    const res = await fetch('/api/v1/sync/trigger', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ mode }),
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

// Quick Scrobble / Mark Next Ep Watched
async function quickScrobble(showId, showTitle) {
  showToast(`Recording scrobble for ${showTitle}...`, 'info', 1500);
  try {
    const res = await fetch(`/api/v1/shows/${showId}/quick-scrobble`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
    });
    const data = await res.json();
    if (res.ok) {
      const ep = data.scrobbled_episode;
      const epStr = `S${String(ep.season_number).padStart(2, '0')}E${String(ep.episode_number).padStart(2, '0')}`;
      showToast(`✓ Marked watched: ${showTitle} (${epStr})`, 'success', 2500);
      setTimeout(() => window.location.reload(), 700);
    } else {
      showToast(data.detail || 'Could not mark episode watched', 'error');
    }
  } catch (err) {
    showToast(`Error: ${err.message}`, 'error');
  }
}

// Mark specific episode watched
async function watchSpecificEpisode(showId, episodeId, epTitle) {
  try {
    const res = await fetch(`/api/v1/shows/${showId}/episodes/${episodeId}/watch`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
    });
    if (res.ok) {
      showToast(`✓ Watched: ${epTitle}`, 'success', 2000);
      const row = document.getElementById(`ep-row-${episodeId}`);
      if (row) {
        row.style.opacity = '0.3';
        row.style.textDecoration = 'line-through';
        row.querySelector('button')?.remove();
      }
      setTimeout(() => window.location.reload(), 700);
    } else {
      const err = await res.json();
      showToast(err.detail || 'Failed to mark episode', 'error');
    }
  } catch (e) {
    showToast(`Error: ${e.message}`, 'error');
  }
}

// Choose an exact episode to resume later. This remains local to TVeaker.
async function selectNowWatching(episodeId, epTitle) {
  try {
    const res = await fetch('/api/v1/now-watching', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ episode_id: episodeId }),
    });
    if (res.ok) {
      showToast(`Now watching: ${epTitle}`, 'success', 1800);
      setTimeout(() => window.location.reload(), 500);
    } else {
      const err = await res.json();
      showToast(err.detail || 'Could not choose this episode', 'error');
    }
  } catch (err) {
    showToast(`Error: ${err.message}`, 'error');
  }
}

// Apple Spatial Slide-over Drawer for Unwatched Episodes
async function openEpisodesDrawer(showId, showTitle) {
  let drawer = document.getElementById('episodes-drawer-container');
  if (drawer) drawer.remove();

  drawer = document.createElement('div');
  drawer.id = 'episodes-drawer-container';
  drawer.className = 'episodes-drawer';

  drawer.innerHTML = `
    <div class="episodes-drawer__panel">
      <div class="episodes-drawer__header">
        <div>
          <h2>${showTitle}</h2>
          <div class="episodes-drawer__eyebrow">REMAINING UNWATCHED EPISODES</div>
        </div>
        <button class="episodes-drawer__close" aria-label="Close episodes" onclick="document.getElementById('episodes-drawer-container').remove()">✕</button>
      </div>

      <div id="drawer-episodes-list" class="episodes-drawer__list">
        <div class="episodes-drawer__loading">
          <div class="pulse-dot"></div>
          Loading remaining episodes...
        </div>
      </div>
    </div>
  `;

  document.body.appendChild(drawer);

  // Fetch unwatched list
  try {
    const res = await fetch(`/api/v1/shows/${showId}/unwatched`);
    const data = await res.json();
    const listEl = document.getElementById('drawer-episodes-list');

    if (!data.unwatched_episodes || data.unwatched_episodes.length === 0) {
      listEl.innerHTML = `
        <div class="episodes-drawer__empty">
          <span class="material-symbols-rounded" aria-hidden="true">task_alt</span>
          <h3>Completely Caught Up!</h3>
          <p>You have watched all available episodes of this series.</p>
        </div>
      `;
      return;
    }

    let html = `
      <div class="episodes-drawer__summary">
        <div>
          <span><strong>${data.remaining_episodes}</strong> episodes remaining</span>
          <span class="episodes-drawer__runtime">~${Math.round(data.unwatched_minutes / 60 * 10) / 10}h total</span>
        </div>
      </div>
    `;

    data.unwatched_episodes.forEach((ep) => {
      const epCode = `S${String(ep.season_number).padStart(2, '0')}E${String(ep.episode_number).padStart(2, '0')}`;
      const epTitleSafe = (ep.title || 'Episode ' + ep.episode_number).replace("'", "\\'");
      html += `
        <div id="ep-row-${ep.id}" class="episodes-drawer__episode">
          <div class="episodes-drawer__episode-copy">
            <div class="episodes-drawer__episode-heading">
              <span class="episodes-drawer__episode-code">${epCode}</span>
              <strong>${ep.title || 'Untitled'}</strong>
            </div>
            <div class="episodes-drawer__episode-meta">
              ${ep.runtime_minutes}m ${ep.first_aired ? '• ' + ep.first_aired.substring(0, 10) : ''}
            </div>
          </div>
          <div class="episodes-drawer__actions">
            <button class="btn btn-sm episodes-drawer__watch episodes-drawer__select" onclick="selectNowWatching(${ep.id}, '${epTitleSafe}')">
              <span class="material-symbols-rounded" aria-hidden="true">play_arrow</span> Watch now
            </button>
            <button class="btn btn-sm btn-primary episodes-drawer__watch" onclick="watchSpecificEpisode(${showId}, ${ep.id}, '${epTitleSafe}')">
              <span class="material-symbols-rounded" aria-hidden="true">check</span> Watched
            </button>
          </div>
        </div>
      `;
    });

    listEl.innerHTML = html;
  } catch (e) {
    const listEl = document.getElementById('drawer-episodes-list');
    if (listEl) {
      listEl.innerHTML = `<div class="episodes-drawer__error">Failed to load episodes: ${e.message}</div>`;
    }
  }
}

// Live show status update
async function updateShowStatus(showId, status) {
  try {
    const res = await fetch(`/api/v1/shows/${showId}`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ status }),
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

// Interactive Pace Slider Modal Dialog
function openPaceModal(showId, showTitle, currentPace, remainingEps) {
  let modal = document.getElementById('pace-modal');
  if (modal) modal.remove();

  const paceVal = currentPace ? parseFloat(currentPace) : 1.0;
  const remEps = remainingEps ? parseInt(remainingEps) : 10;

  function calculateFinish(pace) {
    if (remEps <= 0) return 'Already Caught Up';
    if (!pace || pace <= 0) return '—';
    const weeks = remEps / pace;
    const days = Math.ceil(weeks * 7);
    const finishDate = new Date();
    finishDate.setDate(finishDate.getDate() + days);
    return `${finishDate.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })} (~${days} days)`;
  }

  modal = document.createElement('div');
  modal.id = 'pace-modal';
  modal.className = 'pace-modal';

  modal.innerHTML = `
    <div class="pace-modal__dialog">
      <div class="pace-modal__header">
        <h3>Target Velocity Calculator</h3>
        <button class="pace-modal__close" aria-label="Close velocity calculator" onclick="document.getElementById('pace-modal').remove()">✕</button>
      </div>

      <p class="pace-modal__description">
        Adjust your weekly viewing pace for <strong>${showTitle}</strong> (${remEps} eps remaining).
      </p>

      <div class="pace-modal__control">
        <div class="pace-modal__control-header">
          <span>EPISODES PER WEEK:</span>
          <span id="pace-slider-val">${paceVal} eps/wk</span>
        </div>
        <input type="range" id="pace-range" min="0.5" max="14" step="0.5" value="${paceVal}" />
      </div>

      <div class="pace-modal__projection">
        <div>Projected Finish Date</div>
        <div id="pace-projected-finish">
          ${calculateFinish(paceVal)}
        </div>
      </div>

      <div class="pace-modal__actions">
        <button id="pace-reset-btn" class="btn btn-secondary" style="flex: 1;">Reset to Auto</button>
        <button id="pace-save-btn" class="btn btn-primary" style="flex: 1; font-weight: 800;">Save Velocity</button>
      </div>
    </div>
  `;

  document.body.appendChild(modal);

  const range = document.getElementById('pace-range');
  const valDisplay = document.getElementById('pace-slider-val');
  const finishDisplay = document.getElementById('pace-projected-finish');

  range.addEventListener('input', (e) => {
    const val = parseFloat(e.target.value);
    valDisplay.innerText = `${val} eps/wk`;
    finishDisplay.innerText = calculateFinish(val);
  });

  document.getElementById('pace-save-btn').onclick = async () => {
    const pace = parseFloat(range.value);
    await applyPace(showId, pace);
  };

  document.getElementById('pace-reset-btn').onclick = async () => {
    await applyPace(showId, null);
  };
}

async function applyPace(showId, pace) {
  try {
    const res = await fetch(`/api/v1/shows/${showId}`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ manual_episodes_per_week: pace }),
    });
    if (res.ok) {
      showToast('Weekly velocity updated', 'success', 2000);
      const modal = document.getElementById('pace-modal');
      if (modal) modal.remove();
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
      body: JSON.stringify({ include_specials: !currentVal }),
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
      body: JSON.stringify({ run_id: runId, candidate_id: candidateId, action }),
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

// Keyboard shortcuts: '/' or '⌘K' focuses search, 'Esc' closes modals & drawers
document.addEventListener('keydown', (e) => {
  if (
    (e.key === '/' || (e.metaKey && e.key === 'k') || (e.ctrlKey && e.key === 'k')) &&
    document.activeElement.tagName !== 'INPUT'
  ) {
    e.preventDefault();
    const searchInput =
      document.getElementById('shows-search-input') ||
      document.getElementById('history-search-input');
    if (searchInput) searchInput.focus();
  } else if (e.key === 'Escape') {
    const modal = document.getElementById('pace-modal');
    if (modal) modal.remove();
    const drawer = document.getElementById('episodes-drawer-container');
    if (drawer) drawer.remove();
  }
});

document.addEventListener('DOMContentLoaded', () => {
  setupInstantSearch('shows-search-input', '.library-entry');
  setupInstantSearch('history-search-input', '.history-row');
  setupThemeControls();
  setupDensityControls();
});

function setupThemeControls() {
  const root = document.documentElement;
  const controls = document.querySelectorAll('[data-theme-toggle]');

  const render = () => {
    const current = root.dataset.theme === 'dark' ? 'dark' : 'light';
    controls.forEach((control) => {
      const icon = control.querySelector('[data-theme-icon]');
      const label = control.querySelector('[data-theme-label]');
      if (icon) icon.textContent = current === 'dark' ? 'light_mode' : 'dark_mode';
      if (label) label.textContent = current === 'dark' ? 'Light' : 'Dark';
      control.setAttribute('aria-label', `Switch to ${current === 'dark' ? 'light' : 'dark'} theme`);
    });
  };

  controls.forEach((control) => {
    control.addEventListener('click', () => {
      root.dataset.theme = root.dataset.theme === 'dark' ? 'light' : 'dark';
      localStorage.setItem('tveaker-theme', root.dataset.theme);
      render();
    });
  });

  render();
}

function setupDensityControls() {
  const root = document.documentElement;
  const controls = document.querySelectorAll('[data-density-toggle]');

  const render = () => {
    const compact = root.dataset.density === 'compact';
    controls.forEach((control) => {
      const icon = control.querySelector('[data-density-icon]');
      const label = control.querySelector('[data-density-label]');
      if (icon) icon.textContent = compact ? 'density_small' : 'density_medium';
      if (label) label.textContent = compact ? 'Roomy' : 'Compact';
      control.setAttribute('aria-label', compact ? 'Switch to comfortable mode' : 'Switch to compact mode');
      control.setAttribute('aria-pressed', String(compact));
    });
    document.querySelectorAll('[data-density-status]').forEach((status) => {
      status.textContent = compact ? 'Compact' : 'Comfortable';
    });
  };

  controls.forEach((control) => {
    control.addEventListener('click', () => {
      root.dataset.density = root.dataset.density === 'compact' ? 'comfortable' : 'compact';
      localStorage.setItem('tveaker-density', root.dataset.density);
      render();
    });
  });

  render();
}

async function triggerMetadataHydration(button) {
  const original = button.innerHTML;
  button.disabled = true;
  button.textContent = 'Queued…';
  try {
    const response = await fetch('/api/v1/metadata/hydrate', { method: 'POST' });
    if (!response.ok) throw new Error('Metadata refresh could not be queued.');
    button.textContent = 'Queued';
    window.setTimeout(() => {
      button.innerHTML = original;
      button.disabled = false;
    }, 1800);
  } catch (error) {
    button.innerHTML = original;
    button.disabled = false;
    alert(error.message || 'Metadata refresh could not be queued.');
  }
}
