/**
 * TVeaker Frontend Interactive JavaScript
 */

async function triggerSync(mode = 'incremental') {
  const btn = document.getElementById('sync-trigger-btn');
  if (btn) {
    btn.disabled = true;
    btn.innerText = 'Syncing...';
  }

  try {
    const res = await fetch('/api/v1/sync/trigger', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ mode })
    });
    const data = await res.json();
    if (res.ok) {
      alert(`Sync completed: ${data.status} (Fetched: ${JSON.stringify(data.fetched)})`);
      window.location.reload();
    } else {
      alert(`Sync failed: ${data.detail || 'Unknown error'}`);
    }
  } catch (err) {
    alert(`Error: ${err.message}`);
  } finally {
    if (btn) {
      btn.disabled = false;
      btn.innerText = 'Sync Now';
    }
  }
}

async function updateShowStatus(showId, status) {
  try {
    const res = await fetch(`/api/v1/shows/${showId}`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ status })
    });
    if (res.ok) {
      window.location.reload();
    } else {
      const err = await res.json();
      alert(`Failed to update status: ${err.detail}`);
    }
  } catch (err) {
    alert(`Error: ${err.message}`);
  }
}

async function updateShowPace(showId) {
  const paceStr = prompt('Enter manual episodes per week (e.g. 3.5, or leave blank to reset):');
  if (paceStr === null) return;
  const pace = paceStr.trim() === '' ? null : parseFloat(paceStr);
  if (pace !== null && (isNaN(pace) || pace <= 0)) {
    alert('Pace must be a positive number');
    return;
  }

  try {
    const res = await fetch(`/api/v1/shows/${showId}`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ manual_episodes_per_week: pace })
    });
    if (res.ok) {
      window.location.reload();
    } else {
      const err = await res.json();
      alert(`Failed to update pace: ${err.detail}`);
    }
  } catch (err) {
    alert(`Error: ${err.message}`);
  }
}

async function toggleSpecials(showId, currentVal) {
  try {
    const res = await fetch(`/api/v1/shows/${showId}`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ include_specials: !currentVal })
    });
    if (res.ok) {
      window.location.reload();
    }
  } catch (err) {
    alert(`Error: ${err.message}`);
  }
}

async function sendFeedback(runId, candidateId, action) {
  try {
    const res = await fetch('/api/v1/recommendations/feedback', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ run_id: runId, candidate_id: candidateId, action })
    });
    if (res.ok) {
      const card = document.getElementById(`rec-${candidateId.replace(':', '-')}`);
      if (card) {
        card.style.opacity = '0.4';
        card.style.pointerEvents = 'none';
      }
    }
  } catch (err) {
    console.error('Feedback submission failed', err);
  }
}
