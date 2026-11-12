const V='growup-20261112114131';const PRE=["./", "index.html", "fbsdk.js", "manifest.webmanifest", "icons/icon-192.png", "fonts/sora-latin-800-normal.woff2", "fonts/sora-latin-500-normal.woff2", "fonts/oswald-latin-500-normal.woff2", "fonts/inter-latin-500-normal.woff2", "fonts/sora-latin-600-normal.woff2", "fonts/oswald-latin-700-normal.woff2", "fonts/inter-latin-600-normal.woff2", "fonts/inter-latin-700-normal.woff2", "fonts/inter-latin-400-normal.woff2", "fonts/oswald-latin-600-normal.woff2", "fonts/sora-latin-700-normal.woff2"];
self.addEventListener('install',e=>{self.skipWaiting();e.waitUntil(caches.open(V).then(c=>c.addAll(PRE)).catch(()=>{}));});
self.addEventListener('activate',e=>{e.waitUntil(caches.keys().then(ks=>Promise.all(ks.filter(k=>k!==V).map(k=>caches.delete(k)))).then(()=>self.clients.claim()));});
self.addEventListener('fetch',e=>{const r=e.request;if(r.method!=='GET')return;const u=new URL(r.url);if(u.origin!==location.origin)return;
  const html=r.mode==='navigate'||u.pathname.endsWith('/')||u.pathname.endsWith('.html');
  if(html){e.respondWith(fetch(r).then(res=>{const c=res.clone();caches.open(V).then(x=>x.put(r,c));return res;}).catch(()=>caches.match(r).then(m=>m||caches.match('index.html'))));return;}
  e.respondWith(caches.match(r).then(m=>m||fetch(r).then(res=>{if(res.ok){const c=res.clone();caches.open(V).then(x=>x.put(r,c));}return res;})));});
