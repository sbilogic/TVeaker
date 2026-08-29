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
    } else {
      const err = await res.json();
      showToast(err.detail || 'Failed to mark episode', 'error');
    }
  } catch (e) {
    showToast(`Error: ${e.message}`, 'error');
  }
}

// Apple Spatial Slide-over Drawer for Unwatched Episodes
async function openEpisodesDrawer(showId, showTitle) {
  let drawer = document.getElementById('episodes-drawer-container');
  if (drawer) drawer.remove();

  drawer = document.createElement('div');
  drawer.id = 'episodes-drawer-container';
  drawer.style.cssText = `
    position: fixed; inset: 0; background: rgba(0, 0, 0, 0.75); backdrop-filter: blur(16px);
    display: flex; justify-content: flex-end; z-index: 10000;
  `;

  drawer.innerHTML = `
    <div style="background: rgba(14, 18, 30, 0.96); border-left: 1px solid rgba(255, 255, 255, 0.12); width: 100%; max-width: 520px; height: 100vh; display: flex; flex-direction: column; box-shadow: -20px 0 60px rgba(0,0,0,0.8);">
      <div style="padding: 24px; border-bottom: 1px solid rgba(255, 255, 255, 0.08); display: flex; justify-content: space-between; align-items: center;">
        <div>
          <h2 style="font-size: 1.3rem; font-weight: 800; color: #fff;">${showTitle}</h2>
          <div style="font-size: 0.8rem; color: #38bdf8; font-weight: 700; margin-top: 2px;">REMAINING UNWATCHED EPISODES</div>
        </div>
        <button onclick="document.getElementById('episodes-drawer-container').remove()" style="background: rgba(255,255,255,0.08); border: none; color: #94a3b8; width: 34px; height: 34px; border-radius: 50%; cursor: pointer; font-size: 1rem;">✕</button>
      </div>

      <div id="drawer-episodes-list" style="flex-grow: 1; overflow-y: auto; padding: 20px; display: flex; flex-direction: column; gap: 12px;">
        <div style="text-align: center; padding: 40px; color: #94a3b8;">
          <div class="pulse-dot" style="margin: 0 auto 12px auto;"></div>
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
        <div style="text-align: center; padding: 60px 20px; color: #10b981;">
          <div style="font-size: 2.5rem; margin-bottom: 12px;">🎉</div>
          <h3 style="font-size: 1.2rem; font-weight: 800; color: #fff;">Completely Caught Up!</h3>
          <p style="font-size: 0.88rem; color: #94a3b8; margin-top: 6px;">You have watched all available episodes of this series.</p>
        </div>
      `;
      return;
    }

    let html = `
      <div style="background: rgba(56, 189, 248, 0.08); border: 1px solid rgba(56, 189, 248, 0.2); border-radius: 12px; padding: 14px; margin-bottom: 8px;">
        <div style="display: flex; justify-content: space-between; font-size: 0.85rem; color: #cbd5e1;">
          <span><strong>${data.remaining_episodes}</strong> episodes remaining</span>
          <span style="color: #38bdf8; font-weight: 700; font-family: monospace;">~${Math.round(data.unwatched_minutes / 60 * 10) / 10}h total</span>
        </div>
      </div>
    `;

    data.unwatched_episodes.forEach((ep) => {
      const epCode = `S${String(ep.season_number).padStart(2, '0')}E${String(ep.episode_number).padStart(2, '0')}`;
      const epTitleSafe = (ep.title || 'Episode ' + ep.episode_number).replace("'", "\\'");
      html += `
        <div id="ep-row-${ep.id}" style="background: rgba(255, 255, 255, 0.03); border: 1px solid rgba(255, 255, 255, 0.07); border-radius: 12px; padding: 14px 16px; display: flex; justify-content: space-between; align-items: center; gap: 12px;">
          <div style="flex-grow: 1;">
            <div style="display: flex; align-items: center; gap: 8px; margin-bottom: 4px;">
              <span style="font-family: monospace; font-weight: 800; color: #38bdf8; font-size: 0.84rem; background: rgba(56, 189, 248, 0.12); padding: 2px 6px; border-radius: 4px;">${epCode}</span>
              <strong style="color: #fff; font-size: 0.92rem;">${ep.title || 'Untitled'}</strong>
            </div>
            <div style="font-size: 0.76rem; color: #94a3b8;">
              ${ep.runtime_minutes}m ${ep.first_aired ? '• ' + ep.first_aired.substring(0, 10) : ''}
            </div>
          </div>
          <button class="btn btn-sm btn-primary" style="font-size: 0.76rem; padding: 6px 12px; font-weight: 800;" onclick="watchSpecificEpisode(${showId}, ${ep.id}, '${epTitleSafe}')">
            ✓ Watched
          </button>
        </div>
      `;
    });

    listEl.innerHTML = html;
  } catch (e) {
    const listEl = document.getElementById('drawer-episodes-list');
    if (listEl) {
      listEl.innerHTML = `<div style="color: #f43f5e; padding: 20px;">Failed to load episodes: ${e.message}</div>`;
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
  modal.style.cssText = `
    position: fixed; inset: 0; background: rgba(0, 0, 0, 0.75); backdrop-filter: blur(16px);
    display: flex; align-items: center; justify-content: center; z-index: 10000;
  `;

  modal.innerHTML = `
    <div style="background: rgba(14, 18, 30, 0.96); border: 1px solid rgba(56, 189, 248, 0.3); box-shadow: 0 20px 60px rgba(0,0,0,0.8); border-radius: 20px; padding: 28px; width: 90%; max-width: 440px; color: #fff;">
      <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px;">
        <h3 style="font-size: 1.2rem; font-weight: 800;">Target Velocity Calculator</h3>
        <button onclick="document.getElementById('pace-modal').remove()" style="background: none; border: none; color: #94a3b8; font-size: 1.2rem; cursor: pointer;">✕</button>
      </div>

      <p style="font-size: 0.88rem; color: #94a3b8; margin-bottom: 20px;">
        Adjust your weekly viewing pace for <strong style="color: #f8fafc;">${showTitle}</strong> (${remEps} eps remaining).
      </p>

      <div style="margin-bottom: 20px;">
        <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px;">
          <span style="font-size: 0.82rem; color: #94a3b8; font-weight: 600;">EPISODES PER WEEK:</span>
          <span id="pace-slider-val" style="font-size: 1.2rem; font-weight: 800; color: #38bdf8; font-family: monospace;">${paceVal} eps/wk</span>
        </div>
        <input type="range" id="pace-range" min="0.5" max="14" step="0.5" value="${paceVal}" style="width: 100%; accent-color: #38bdf8; cursor: pointer;" />
      </div>

      <div style="background: rgba(0, 0, 0, 0.4); border: 1px solid rgba(255, 255, 255, 0.08); border-radius: 12px; padding: 14px; margin-bottom: 24px;">
        <div style="font-size: 0.78rem; color: #94a3b8; text-transform: uppercase; font-weight: 700;">Projected Finish Date</div>
        <div id="pace-projected-finish" style="font-size: 1.1rem; font-weight: 800; color: #38bdf8; margin-top: 4px;">
          ${calculateFinish(paceVal)}
        </div>
      </div>

      <div style="display: flex; gap: 10px;">
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
  setupInstantSearch('shows-search-input', '.table-container tbody tr');
  setupInstantSearch('history-search-input', '.history-row');
});
