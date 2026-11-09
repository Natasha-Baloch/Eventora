// ---- navbar scroll effect
(function () {
  const nav = document.querySelector('.ev-nav');
  if (nav && !nav.classList.contains('solid')) {
    const f = () => nav.classList.toggle('scrolled', window.scrollY > 40);
    f(); window.addEventListener('scroll', f);
  }
})();

// ---- confetti on hero
(function () {
  const c = document.getElementById('confetti');
  if (!c) return;
  const ctx = c.getContext('2d');
  const colors = ['#8b5cf6', '#ec4899', '#f97316', '#22d3ee', '#fde047', '#3b82f6'];
  let w, h, parts = [];
  function size() { w = c.width = c.offsetWidth; h = c.height = c.offsetHeight; }
  size(); window.addEventListener('resize', size);
  const n = window.innerWidth < 700 ? 45 : 110;
  for (let i = 0; i < n; i++) parts.push({
    x: Math.random() * w, y: Math.random() * h, r: 3 + Math.random() * 5, s: .6 + Math.random() * 1.6,
    d: Math.random() * Math.PI * 2, col: colors[i % colors.length], rot: Math.random() * 6, vr: (Math.random() - .5) * .1
  });
  (function draw() {
    ctx.clearRect(0, 0, w, h);
    parts.forEach(p => {
      p.y += p.s; p.x += Math.sin(p.d += .02) * .8; p.rot += p.vr;
      if (p.y > h + 10) { p.y = -10; p.x = Math.random() * w; }
      ctx.save(); ctx.translate(p.x, p.y); ctx.rotate(p.rot); ctx.fillStyle = p.col; ctx.globalAlpha = .85;
      ctx.fillRect(-p.r, -p.r / 2, p.r * 2, p.r); ctx.restore();
    });
    requestAnimationFrame(draw);
  })();
})();

// ---- hero video: show poster/animation if video can't play
document.addEventListener('DOMContentLoaded', () => {
  const v = document.querySelector('.hero video');
  if (v) { v.addEventListener('error', () => v.remove(), true); const s = v.querySelector('source'); if (s) s.addEventListener('error', () => v.remove()); }
});

// ---- dashboard tabs <-> URL hash
document.addEventListener('DOMContentLoaded', () => {
  const tabs = document.querySelectorAll('[data-tab]');
  if (!tabs.length) return;
  const h = location.hash.slice(1);
  const btn = h && document.querySelector('[data-tab="' + h + '"]');
  if (btn) bootstrap.Tab.getOrCreateInstance(btn).show();
  tabs.forEach(b => b.addEventListener('shown.bs.tab', () => history.replaceState(null, '', '#' + b.dataset.tab)));
});

// ---- interactive tables: live filter + click-to-sort
document.addEventListener('DOMContentLoaded', () => {
  document.querySelectorAll('input.table-filter').forEach(inp => {
    const table = document.querySelector(inp.dataset.target);
    inp.addEventListener('input', () => {
      const q = inp.value.toLowerCase();
      table.querySelectorAll('tbody tr').forEach(tr => tr.style.display = tr.textContent.toLowerCase().includes(q) ? '' : 'none');
    });
  });
  document.querySelectorAll('table.sortable thead th').forEach((th, idx) => {
    let asc = true;
    th.addEventListener('click', () => {
      const tb = th.closest('table').tBodies[0];
      const rows = [...tb.rows];
      const val = r => (r.cells[idx] ? r.cells[idx].dataset.sort || r.cells[idx].textContent.trim() : '');
      rows.sort((a, b) => {
        const x = val(a), y = val(b), nx = parseFloat(x.replace(/[^0-9.\-]/g, '')), ny = parseFloat(y.replace(/[^0-9.\-]/g, ''));
        const cmp = (!isNaN(nx) && !isNaN(ny) && /\d/.test(x) && /\d/.test(y)) ? nx - ny : x.localeCompare(y);
        return asc ? cmp : -cmp;
      });
      asc = !asc; rows.forEach(r => tb.appendChild(r));
    });
  });
});

// ---- countdown (payment window)
document.addEventListener('DOMContentLoaded', () => {
  const el = document.getElementById('countdown');
  if (!el) return;
  const end = new Date(el.dataset.end).getTime();
  const tick = () => {
    const s = Math.max(0, Math.floor((end - Date.now()) / 1000));
    el.textContent = String(Math.floor(s / 60)).padStart(2, '0') + ':' + String(s % 60).padStart(2, '0');
    if (s === 0) { el.textContent = 'Expired'; }
  };
  tick(); setInterval(tick, 1000);
});

// ---- ticket category price total on detail page
document.addEventListener('DOMContentLoaded', () => {
  const form = document.getElementById('bookForm');
  if (!form) return;
  const out = document.getElementById('bookTotal');
  const upd = () => {
    const t = form.querySelector('input[name=tierId]:checked');
    const q = parseInt(form.querySelector('[name=quantity]').value || '1', 10);
    if (t && out) out.textContent = (parseFloat(t.dataset.price) * q).toLocaleString();
  };
  form.addEventListener('input', upd); upd();
});

// ---- dynamic ticket-tier rows in organizer form
document.addEventListener('DOMContentLoaded', () => {
  const box = document.getElementById('tiers'), add = document.getElementById('addTier');
  if (!box || !add) return;
  add.addEventListener('click', () => {
    const row = box.querySelector('.tier-row').cloneNode(true);
    row.querySelectorAll('input').forEach(i => i.value = '');
    box.appendChild(row);
  });
  box.addEventListener('click', e => {
    if (e.target.closest('.rm-tier') && box.querySelectorAll('.tier-row').length > 1) e.target.closest('.tier-row').remove();
  });
});

// ---- QR scanner (organizer)
document.addEventListener('DOMContentLoaded', () => {
  const res = document.getElementById('scanResult');
  if (!res) return;
  const csrf = document.querySelector('meta[name=_csrf]').content, hdr = document.querySelector('meta[name=_csrf_header]').content;
  let busy = false;
  async function submit(code) {
    if (busy || !code) return; busy = true;
    try {
      const r = await fetch('/organizer/scan', { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded', [hdr]: csrf }, body: 'code=' + encodeURIComponent(code) });
      const j = await r.json();
      res.className = 'scan-result mt-3 ' + (j.ok ? 'scan-ok' : 'scan-bad');
      res.innerHTML = (j.ok ? '✅ ' : '❌ ') + j.message + (j.holder ? '<div class="small fw-normal mt-1">' + j.holder + ' · ' + (j.event || '') + ' · ' + (j.tier || '') + '</div>' : '');
    } catch (e) { res.className = 'scan-result mt-3 scan-bad'; res.textContent = 'Network error'; }
    setTimeout(() => busy = false, 1800);
  }
  document.getElementById('manualScan').addEventListener('submit', e => {
    e.preventDefault(); const i = document.getElementById('manualCode'); submit(i.value); i.value = '';
  });
  const startBtn = document.getElementById('startCam');
  if (startBtn && window.Html5Qrcode) {
    let scanner;
    startBtn.addEventListener('click', async () => {
      if (scanner) { await scanner.stop(); scanner = null; startBtn.textContent = 'Start camera'; return; }
      scanner = new Html5Qrcode('reader');
      try { await scanner.start({ facingMode: 'environment' }, { fps: 10, qrbox: 230 }, t => submit(t)); startBtn.textContent = 'Stop camera'; }
      catch (e) { res.className = 'scan-result mt-3 scan-bad'; res.textContent = 'Camera unavailable - use manual entry.'; scanner = null; }
    });
  }
});
