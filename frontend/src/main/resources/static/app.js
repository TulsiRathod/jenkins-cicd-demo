// Store App - talks only to its own server (/api/...), which forwards to the
// inventory and order services of the same environment.

const $ = (id) => document.getElementById(id);
const money = (n) => Number(n).toLocaleString('en-US', { style: 'currency', currency: 'USD' });
const esc = (s) => String(s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

let products = [];
const lastVersions = {};

async function api(path, options) {
  const res = await fetch(path, options);
  const text = await res.text();
  let body = null;
  try { body = text ? JSON.parse(text) : null; } catch { body = text; }
  return { ok: res.ok, status: res.status, body };
}

function showBanner(message) {
  $('banner').textContent = message;
  $('banner').hidden = !message;
}

// ---------- header: environment + running versions ----------
async function loadStatus() {
  const { ok, body } = await api('/api/status');
  if (!ok) return;
  const env = body.environment || 'unknown';
  $('env').textContent = env;
  $('env').className = 'env env-' + env;
  document.title = 'Store App - ' + env.toUpperCase();

  for (const name of ['frontend', 'inventory', 'order']) {
    const el = $('v-' + name);
    const v = body[name];
    el.textContent = v;
    el.classList.toggle('down', v === 'unavailable');
    if (lastVersions[name] && lastVersions[name] !== v && v !== 'unavailable') {
      el.classList.add('flash');                        // a new version was just deployed
      setTimeout(() => el.classList.remove('flash'), 4000);
      refreshData();
    }
    lastVersions[name] = v;
  }
}

// ---------- inventory ----------
async function loadProducts() {
  const { ok, body } = await api('/api/products');
  if (!ok) {
    $('products').innerHTML = `<tr><td colspan="4" class="muted">${esc(body?.error || 'Could not load products')}</td></tr>`;
    return false;
  }
  products = body;
  $('t-products').textContent = products.length;
  $('products').innerHTML = products.map((p) => {
    const pill = p.quantity === 0 ? 'pill-bad' : p.quantity < 5 ? 'pill-warn' : 'pill-ok';
    return `<tr>
      <td><code>${esc(p.sku)}</code></td>
      <td>${esc(p.name)}</td>
      <td class="num"><span class="pill ${pill}">${p.quantity}</span></td>
      <td class="num">${money(p.price)}</td>
    </tr>`;
  }).join('');

  const selected = $('f-sku').value;
  $('f-sku').innerHTML = products
    .map((p) => `<option value="${esc(p.sku)}">${esc(p.name)} (${p.quantity} in stock)</option>`)
    .join('');
  if (selected) $('f-sku').value = selected;
  return true;
}

async function loadInventoryValue() {
  const { ok, body } = await api('/api/inventory/value');
  $('t-value').textContent = ok ? money(body.totalValue) : '\u2013';
}

async function loadLowStock() {
  const { ok, status, body } = await api('/api/products/low-stock');
  if (status === 404) {
    // Endpoint doesn't exist in this version of inventory -> feature not deployed here yet
    $('low-stock').innerHTML = '<div class="not-deployed">Not available in this environment yet.<br><small>The low-stock feature has not been deployed here.</small></div>';
    return;
  }
  if (!ok) {
    $('low-stock').innerHTML = `<div class="not-deployed">${esc(body?.error || 'Could not load')}</div>`;
    return;
  }
  $('low-stock').innerHTML = body.length === 0
    ? '<div class="muted">All products are well stocked.</div>'
    : '<ul class="low-list">' + body.map((p) =>
        `<li><span>${esc(p.name)} <code class="muted">${esc(p.sku)}</code></span><span class="pill pill-warn">${p.quantity} left</span></li>`
      ).join('') + '</ul>';
}

// ---------- orders ----------
async function loadOrders() {
  const { ok, body } = await api('/api/orders');
  if (!ok) {
    $('orders').innerHTML = `<tr><td colspan="7" class="muted">${esc(body?.error || 'Could not load orders')}</td></tr>`;
    return false;
  }
  $('t-orders').textContent = body.filter((o) => o.status === 'PLACED').length;
  $('orders').innerHTML = body.length === 0
    ? '<tr><td colspan="7" class="muted">No orders yet</td></tr>'
    : body.map((o) => {
        const placed = o.status === 'PLACED';
        const total = o.unitPrice * o.quantity;
        return `<tr>
          <td><code>${esc(o.id)}</code></td>
          <td>${esc(o.customer)}</td>
          <td><code>${esc(o.sku)}</code></td>
          <td class="num">${o.quantity}</td>
          <td class="num">${money(total)}</td>
          <td><span class="pill ${placed ? 'pill-ok' : 'pill-bad'}">${esc(o.status)}</span></td>
          <td>${placed ? `<button class="link" data-cancel="${esc(o.id)}">Cancel</button>` : ''}</td>
        </tr>`;
      }).join('');
  return true;
}

async function loadRevenue() {
  const { ok, body } = await api('/api/orders/revenue');
  $('t-revenue').textContent = ok ? money(body.totalRevenue) : '\u2013';
}

$('orders').addEventListener('click', async (e) => {
  const id = e.target.dataset.cancel;
  if (!id) return;
  await api(`/api/orders/${encodeURIComponent(id)}/cancel`, { method: 'POST' });
  refreshData();
});

function formMessage(text, kind) {
  $('form-msg').textContent = text;
  $('form-msg').className = 'form-msg ' + kind;
  $('form-msg').hidden = false;
}

$('order-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  const sku = $('f-sku').value;
  const quantity = Number($('f-qty').value);
  const product = products.find((p) => p.sku === sku);
  if (!product) return;

  // 1) reserve stock in inventory
  const reserve = await api(`/api/products/${encodeURIComponent(sku)}/reserve?quantity=${quantity}`, { method: 'POST' });
  if (!reserve.ok) {
    formMessage(reserve.body?.error || 'Could not reserve stock', 'err');
    return;
  }
  // 2) create the order
  const order = await api('/api/orders', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ customer: $('f-customer').value.trim(), sku, quantity, unitPrice: product.price }),
  });
  if (!order.ok) {
    formMessage(order.body?.error || 'Could not place order', 'err');
  } else {
    formMessage(`Order ${order.body.id} placed \u2013 ${money(product.price * quantity)}`, 'ok');
    $('f-qty').value = 1;
  }
  refreshData();
});

// ---------- refresh ----------
async function refreshData() {
  const [productsOk, ordersOk] = await Promise.all([loadProducts(), loadOrders()]);
  await Promise.all([loadInventoryValue(), loadLowStock(), loadRevenue()]);
  const down = [!productsOk && 'inventory', !ordersOk && 'order'].filter(Boolean);
  showBanner(down.length ? `The ${down.join(' and ')} service is not reachable in this environment.` : '');
}

loadStatus();
refreshData();
setInterval(loadStatus, 5000);   // pick up new deployments automatically
