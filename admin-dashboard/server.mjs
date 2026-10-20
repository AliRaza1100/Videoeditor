import http from 'node:http';
import { isIP } from 'node:net';
import {networks,placementMap} from './ad-catalog.mjs';
import { DatabaseSync } from 'node:sqlite';
import { randomBytes, createHash, scryptSync, timingSafeEqual } from 'node:crypto';
import { readFileSync, mkdirSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root=dirname(fileURLToPath(import.meta.url));
const now=()=>new Date().toISOString();
const digest=value=>createHash('sha256').update(value).digest('hex');
const uuid=/^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/i;
const placementDefinitions=[
 ['home_banner','Home','banner'],['projects_banner','Projects','banner'],
 ['export_complete','Export completed','interstitial'],['export_reward','Export reward button','rewarded'],
 ['preview_reward','Preview reward button','rewarded'],['app_open','Returning app launch','app_open']
].map(([id,label,type])=>({id,label,type}));
const defaultConfig=()=>({network_requests:['admob'],ads_enabled:false,test_mode:true,cooldown_seconds:120,max_per_session:3,every_n_exports:3,
 placements:placementDefinitions.map(p=>({...p,enabled:false,ad_unit_id:''}))});
class ApiError extends Error { constructor(status,message) { super(message);this.status=status; } }
const check=(condition,message,status=400)=>{if(!condition) throw new ApiError(status,message);};
const text=(value,min,max,label)=>{check(typeof value==='string' && value.trim().length>=min && value.trim().length<=max,`Invalid ${label}`);return value.trim();};
const integer=(v,min,max,label)=>{check(Number.isSafeInteger(v)&&v>=min&&v<=max,`Invalid ${label}`);return v;};
const validId=v=>text(v,36,36,'ID') && check(uuid.test(v),'Invalid installation/message ID');
function validateConfig(input,requireBackups=true) {
 check(input && typeof input.ads_enabled==='boolean' && typeof input.test_mode==='boolean','Invalid ads switches');
 const requested=input.network_requests ?? ['admob'];
 check(Array.isArray(requested) && requested.length<=networks.length && new Set(requested).size===requested.length && requested.every(id=>networks.some(n=>n.id===id)),'Invalid requested ad networks');
 const result={network_requests:requested,ads_enabled:input.ads_enabled,test_mode:input.test_mode,
  cooldown_seconds:integer(input.cooldown_seconds,60,86400,'cooldown'),max_per_session:integer(input.max_per_session,1,10,'session cap'),every_n_exports:integer(input.every_n_exports,2,100,'export frequency'),placements:[]};
 const exportRules=input.export_rules??{offer_reward:false,resolutions:[480,1080,2000]};
 check(typeof exportRules.offer_reward==='boolean'&&Array.isArray(exportRules.resolutions)&&exportRules.resolutions.length<=5&&new Set(exportRules.resolutions).size===exportRules.resolutions.length&&exportRules.resolutions.every(r=>[360,480,720,1080,2000].includes(r)),'Invalid export advertising rules');
 result.export_rules={offer_reward:exportRules.offer_reward,resolutions:exportRules.resolutions};
 check(!exportRules.offer_reward||exportRules.resolutions.length>0,'Choose at least one resolution for the export reward offer');
 check(Array.isArray(input.placements)&&input.placements.length===placementDefinitions.length,'All supported placements are required');
 for(const definition of placementDefinitions) {
  const matches=input.placements.filter(p=>p.id===definition.id);check(matches.length===1,'Duplicate/missing placement');const p=matches[0];
  check(p.type===definition.type && typeof p.enabled==='boolean','Unsupported ad format for placement');
  const unit=text(p.ad_unit_id,0,80,'ad unit');check(!unit || /^ca-app-pub-\d{16}\/\d{10}$/.test(unit),'Invalid AdMob unit ID');
  check(!p.enabled||unit.length>0,'Enabled placements require an AdMob unit ID');
  const network=p.network ?? 'admob';
  check(networks.some(n=>n.id===network),'Unknown placement network');
  check(definition.type!=='app_open'||network==='admob','App-open supports the AdMob route only');
  check(!p.enabled||requested.includes(network),'Enable the selected network in Ad networks first');
  const units=p.network_ad_units ?? {[network]:unit};
  check(units && typeof units==='object' && !Array.isArray(units),'Invalid network ad units');
  const normalized={};
  for(const [id,value] of Object.entries(units)) {
   check(networks.some(n=>n.id===id),'Unknown network unit');
   const v=text(value,0,80,'mediated AdMob unit');
   check(!v||/^ca-app-pub-\d{16}\/\d{10}$/.test(v),'Use an AdMob ad unit mapped to this partner, not a partner placement ID');
   normalized[id]=v;
  }
  normalized[network]=unit;
  const fallback=p.fallback_network ?? '';
  check(typeof fallback==='string' && (!fallback||networks.some(n=>n.id===fallback)),'Invalid fallback network');
  check(!fallback||fallback!==network,'Primary and fallback networks must differ');
  check(!fallback||definition.type!=='app_open','App-open fallback is handled by the AdMob mediation group');
  const fallbackUnit=fallback ? normalized[fallback] ?? '' : '';
  check(!fallback||requested.includes(fallback),'Enable the fallback network first');
  check(!fallback||fallbackUnit.length>0 && fallbackUnit!==unit,'Fallback requires a different valid mediated AdMob unit');
  check(!requireBackups||!input.ads_enabled||!p.enabled||definition.type==='app_open'||fallback,'Enabled delivery requires a primary and a backup network route');
  result.placements.push({...definition,enabled:p.enabled,network,network_ad_units:normalized,ad_unit_id:unit,fallback_network:fallback,fallback_ad_unit_id:fallbackUnit});
 }
 check(!requireBackups||!result.ads_enabled||!exportRules.offer_reward||result.placements.find(p=>p.id==='export_reward').enabled,'Enable and configure the Export reward placement before enabling export reward offers');
 return result;
}

export function createApp({databasePath=':memory:',adminEmail,adminPassword,production=false,publicOrigin='',trustLoopbackProxy=false}={}) {
 if(publicOrigin) {const origin=new URL(publicOrigin);check(origin.protocol==='https:'&&origin.origin===publicOrigin,'PUBLIC_ORIGIN must be an HTTPS origin without a trailing slash or path');}
 const clientAddress=req=>{
  const remote=req.socket.remoteAddress;
  const loopback=['127.0.0.1','::1','::ffff:127.0.0.1'].includes(remote);
  const forwarded=req.headers['x-real-ip'];
  return trustLoopbackProxy&&loopback&&typeof forwarded==='string'&&isIP(forwarded) ? forwarded : remote;
 };
 if(databasePath!==':memory:') mkdirSync(dirname(resolve(databasePath)),{recursive:true});
 const db=new DatabaseSync(databasePath);db.exec(readFileSync(join(root,'schema.sql'),'utf8'));
 const get=(sql,...args)=>db.prepare(sql).get(...args);
 const all=(sql,...args)=>db.prepare(sql).all(...args);
 const run=(sql,...args)=>db.prepare(sql).run(...args);
 const tx=fn=>{db.exec('BEGIN IMMEDIATE');try{const result=fn();db.exec('COMMIT');return result;}catch(e){db.exec('ROLLBACK');throw e;}};
 const adminCount=get('SELECT COUNT(*) n FROM admins').n;
 if(!adminCount) {
  check(typeof adminEmail==='string'&&/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(adminEmail),'Set LUMEN_ADMIN_EMAIL');
  check(typeof adminPassword==='string'&&adminPassword.length>=(production?14:8),'Set a suitable LUMEN_ADMIN_PASSWORD');
  check(!production || adminPassword!=='Password','Test password is not permitted in production');
  const salt=randomBytes(24).toString('hex');run('INSERT INTO admins VALUES(?,?,?)',adminEmail.toLowerCase(),salt,scryptSync(adminPassword,salt,64).toString('hex'));
 }
 if(production) for(const account of all('SELECT * FROM admins')) check(!timingSafeEqual(scryptSync('Password',account.salt,64),Buffer.from(account.password_hash,'hex')),'Replace the local test administrator before production');
 if(!get('SELECT version FROM config_versions LIMIT 1')) tx(()=>{
  const body=JSON.stringify(defaultConfig());run('INSERT INTO config_versions(body,actor,created_at) VALUES(?,?,?)',body,'system',now());
  run('INSERT INTO config_draft VALUES(1,1,?)',body);
 });
 const rate=new Map();
 function throttle(key,max) {
  const minute=Math.floor(Date.now()/60000);const entry=rate.get(key);const count=entry?.minute===minute?entry.count+1:1;
  rate.set(key,{minute,count});check(count<=max,'Too many requests; try again later',429);
 }
 const purge=setInterval(()=>{
  const cutoff=new Date(Date.now()-90*86400000).toISOString();
  run('DELETE FROM sessions WHERE expires<?',Date.now());
  run('DELETE FROM events WHERE received_at<?',cutoff);
  run('DELETE FROM usage_daily WHERE day<?',new Date(Date.now()-365*86400000).toISOString().slice(0,10));
  run('DELETE FROM tickets WHERE updated_at<?',new Date(Date.now()-180*86400000).toISOString());
  run('DELETE FROM devices WHERE last_seen<?',new Date(Date.now()-365*86400000).toISOString());
  for(const [key,v] of rate) if(v.minute<Math.floor(Date.now()/60000)-2) rate.delete(key);
 },60000);purge.unref();
 function admin(req) {
  const cookie=req.headers.cookie?.match(/(?:^|; )lumen_session=([a-f0-9]{64})(?:;|$)/)?.[1];
  const session=cookie&&get('SELECT email FROM sessions WHERE token_hash=? AND expires>?',digest(cookie),Date.now());
  check(session,'Sign in to continue',401);return session.email;
 }
 function device(req) {
  const token=req.headers.authorization?.match(/^Bearer ([a-f0-9]{64})$/)?.[1];
  const value=token&&get('SELECT * FROM devices WHERE credential_hash=?',digest(token));check(value,'Invalid installation credential',401);run('UPDATE devices SET last_seen=? WHERE id=?',now(),value.id);return value;
 }
 async function body(req) {
  check(req.headers['content-type']?.startsWith('application/json'),'Use application/json',415);
  let bytes=0;const chunks=[];for await(const chunk of req){bytes+=chunk.length;check(bytes<=65536,'Request too large',413);chunks.push(chunk);}
  try{return JSON.parse(Buffer.concat(chunks).toString('utf8'));}catch{throw new ApiError(400,'Invalid JSON');}
 }
 const send=(res,status,data)=>{res.writeHead(status,{'Content-Type':'application/json; charset=utf-8','Cache-Control':'no-store'});res.end(JSON.stringify(data));};
 const audit=(actor,action,detail)=>run('INSERT INTO audit(actor,action,detail,created_at) VALUES(?,?,?,?)',actor,action,detail,now());
 const published=()=>{const row=get('SELECT * FROM config_versions ORDER BY version DESC LIMIT 1');return {schema_version:1,delivery_mode:'admob_mediation',version:row.version,...validateConfig(JSON.parse(row.body),false),published_at:row.created_at};};
 const pageParams=url=>({page:integer(Number(url.searchParams.get('page')||1),1,100000,'page'),limit:25,q:(url.searchParams.get('q')||'').slice(0,100)});
 const ticket=(id,d)=>{const row=get('SELECT * FROM tickets WHERE id=?',id);check(row && (!d || row.device_id===d.id),'Ticket not found',404);return row;};
 const messages=id=>all('SELECT * FROM (SELECT * FROM messages WHERE ticket_id=? ORDER BY created_at DESC LIMIT 100) ORDER BY created_at',id);
 const server=http.createServer(async(req,res)=>{
  res.setHeader('X-Content-Type-Options','nosniff');res.setHeader('X-Frame-Options','DENY');res.setHeader('Referrer-Policy','no-referrer');
  res.setHeader('Content-Security-Policy',"default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'");
  try {
   const url=new URL(req.url,'http://localhost');const path=url.pathname;
   throttle(`ip:${clientAddress(req)}`,300);
   if(req.method==='GET'&&path==='/api/health') return send(res,200,{ok:true,storage:'SQLite',media_uploads:false});
   if(req.method==='GET'&&path==='/api/app/public-config') return send(res,200,{...published(),delivery_requires_client_checks:true,sdk_required:'Ads-enabled build, partner mediation mapping and UMP consent'});
   if(path.startsWith('/api/admin/') && req.method!=='GET') {
    check(req.headers.origin===(publicOrigin||`${production?'https':'http'}://${req.headers.host}`),'Invalid request origin',403);
   }
   if(path==='/api/admin/login'&&req.method==='POST') {
    throttle(`login:${clientAddress(req)}`,8);const input=await body(req);
    const email=text(input.email,3,254,'email').toLowerCase();const password=text(input.password,1,256,'password');
    const record=get('SELECT * FROM admins WHERE email=?',email);const salt=record?.salt||'missing-user';
    const hash=scryptSync(password,salt,64);check(record&&timingSafeEqual(hash,Buffer.from(record.password_hash,'hex')),'Incorrect email or password',401);
    const token=randomBytes(32).toString('hex');run('INSERT INTO sessions VALUES(?,?,?)',digest(token),email,Date.now()+8*3600000);
    res.setHeader('Set-Cookie',`lumen_session=${token}; HttpOnly; SameSite=Strict; Path=/; Max-Age=28800${production?'; Secure':''}`);
    return send(res,200,{email});
   }
   if(path.startsWith('/api/admin/')) {
    const actor=admin(req);
    if(path==='/api/admin/session'&&req.method==='GET') return send(res,200,{email:actor,storage:'SQLite',firebase_connected:false,sdk_integrated:false,networks,placement_map:placementMap,placements:placementDefinitions});
    if(path==='/api/admin/logout'&&req.method==='POST') {const token=req.headers.cookie?.match(/lumen_session=([a-f0-9]{64})/)?.[1];if(token)run('DELETE FROM sessions WHERE token_hash=?',digest(token));res.setHeader('Set-Cookie','lumen_session=; HttpOnly; SameSite=Strict; Path=/; Max-Age=0');return send(res,200,{ok:true});}
    if(path==='/api/admin/overview'&&req.method==='GET') {
     const today=now().slice(0,10);const activeSince=new Date(Date.now()-7*86400000).toISOString();
     return send(res,200,{timezone:'UTC',installations:get('SELECT COUNT(*) n FROM devices WHERE analytics=1').n,
      active:get('SELECT COUNT(*) n FROM devices WHERE analytics=1 AND last_seen>=?',activeSince).n,
      exports_today:get("SELECT COALESCE(SUM(count),0) n FROM usage_daily WHERE day=? AND type='video_exported'",today).n,
      support_open:get("SELECT COUNT(*) n FROM tickets WHERE status!='closed'").n,
      activity:all('SELECT day,type,SUM(count) count FROM usage_daily WHERE day>=? GROUP BY day,type ORDER BY day',new Date(Date.now()-6*86400000).toISOString().slice(0,10)),
      features:all("SELECT feature,SUM(count) count FROM usage_daily WHERE type='feature_selected' GROUP BY feature ORDER BY count DESC LIMIT 10"),
      revenue:all('SELECT currency,SUM(estimated_micros) estimated_micros,SUM(impressions) impressions FROM revenue_reports GROUP BY currency')});
    }
    if(path==='/api/admin/devices'&&req.method==='GET') {
     const {page,limit,q}=pageParams(url);const term=`%${q.replace(/[\\%_]/g,'\\$&')}%`;
     const where="d.analytics=1 AND (d.id LIKE ? ESCAPE '\\' OR d.model LIKE ? ESCAPE '\\' OR d.brand LIKE ? ESCAPE '\\' OR d.country LIKE ? ESCAPE '\\')";
     const params=[term,term,term,term];
     return send(res,200,{page,limit,total:get(`SELECT COUNT(*) n FROM devices d WHERE ${where}`,...params).n,
      items:all(`SELECT d.id,d.brand,d.model,d.api_level,d.density,d.locale,d.country,d.app_version,d.first_seen,d.last_seen,COALESCE((SELECT SUM(count) FROM usage_daily u WHERE u.device_id=d.id),0) usage FROM devices d WHERE ${where} ORDER BY d.last_seen DESC,d.id LIMIT ? OFFSET ?`,...params,limit,(page-1)*limit)});
    }
    if(path==='/api/admin/tickets'&&req.method==='GET') {const {page,limit,q}=pageParams(url);const pattern=`%${q}%`;return send(res,200,{page,limit,total:get('SELECT COUNT(*) n FROM tickets WHERE subject LIKE ?',pattern).n,items:all('SELECT * FROM tickets WHERE subject LIKE ? ORDER BY updated_at DESC LIMIT ? OFFSET ?',pattern,limit,(page-1)*limit)});}
    const support=path.match(/^\/api\/admin\/tickets\/([a-f0-9-]+)$/);
    if(support&&req.method==='GET') {const t=ticket(support[1]);return send(res,200,{...t,messages:messages(t.id)});}
    if(support&&req.method==='POST') {
     const t=ticket(support[1]);const input=await body(req);check(['open','pending','closed'].includes(input.status),'Invalid ticket status');
     const reply=text(input.reply??'',0,4000,'reply');tx(()=>{if(reply)run('INSERT INTO messages VALUES(?,?,?,?,?)',randomBytes(16).toString('hex'),t.id,'admin',reply,now());run('UPDATE tickets SET status=?,updated_at=? WHERE id=?',input.status,now(),t.id);audit(actor,'support_reply',t.id);});return send(res,200,{ok:true});
    }
    if(path==='/api/admin/config'&&req.method==='GET') {const draft=get('SELECT * FROM config_draft WHERE id=1');return send(res,200,{revision:draft.revision,draft:JSON.parse(draft.body),published:published()});}
    if(path==='/api/admin/config'&&req.method==='PUT') {
     const input=await body(req);const config=validateConfig(input.config);const revision=integer(input.revision,1,Number.MAX_SAFE_INTEGER,'revision');
     const result=run('UPDATE config_draft SET body=?,revision=revision+1 WHERE id=1 AND revision=?',JSON.stringify(config),revision);check(result.changes===1,'Draft changed elsewhere. Reload before saving.',409);audit(actor,'save_draft',String(revision+1));return send(res,200,{revision:revision+1});
    }
    if(path==='/api/admin/config/publish'&&req.method==='POST') {
     const input=await body(req);const version=tx(()=>{const draft=get('SELECT * FROM config_draft WHERE id=1');check(input.revision===draft.revision,'Draft changed. Reload before publishing.',409);const config=validateConfig(JSON.parse(draft.body));const result=run('INSERT INTO config_versions(body,actor,created_at) VALUES(?,?,?)',JSON.stringify(config),actor,now());audit(actor,'publish',String(result.lastInsertRowid));return Number(result.lastInsertRowid);});return send(res,200,{version});
    }
    if(path==='/api/admin/config/history'&&req.method==='GET') return send(res,200,{items:all('SELECT version,actor,created_at FROM config_versions ORDER BY version DESC LIMIT 100')});
    if(path==='/api/admin/config/receipts'&&req.method==='GET') return send(res,200,{published_version:published().version,items:all('SELECT r.* FROM ad_config_receipts r JOIN devices d ON d.id=r.device_id WHERE d.analytics=1 ORDER BY r.received_at DESC LIMIT 100')});
    if(path==='/api/admin/config/rollback'&&req.method==='POST') {const input=await body(req);const source=get('SELECT * FROM config_versions WHERE version=?',integer(input.version,1,Number.MAX_SAFE_INTEGER,'version'));check(source,'Version not found',404);const result=tx(()=>{const r=run('INSERT INTO config_versions(body,actor,created_at) VALUES(?,?,?)',source.body,actor,now());run('UPDATE config_draft SET body=?,revision=revision+1 WHERE id=1',source.body);audit(actor,'rollback',String(source.version));return Number(r.lastInsertRowid);});return send(res,200,{version:result});}
    if(path==='/api/admin/revenue'&&req.method==='GET') return send(res,200,{source:'Manual AdMob report entries; no live reporting connection',items:all('SELECT * FROM revenue_reports ORDER BY day DESC LIMIT 100')});
    if(path==='/api/admin/revenue'&&req.method==='POST') {
     const input=await body(req);check(/^\d{4}-\d{2}-\d{2}$/.test(input.day)&&Number.isFinite(Date.parse(input.day))&&new Date(input.day).toISOString().slice(0,10)===input.day,'Invalid date');check(/^[A-Z]{3}$/.test(input.currency),'Use an ISO currency code');
     const amount=integer(input.estimated_micros,0,1e15,'estimated earnings');const impressions=integer(input.impressions,0,1e12,'impressions');
     run('INSERT INTO revenue_reports VALUES(?,?,?,?,?,?) ON CONFLICT(day,currency) DO UPDATE SET estimated_micros=excluded.estimated_micros,impressions=excluded.impressions,updated_at=excluded.updated_at',input.day,input.currency,amount,impressions,'Admin-entered AdMob report',now());audit(actor,'revenue_report',input.day);return send(res,200,{ok:true});
    }
    if(path==='/api/admin/audit'&&req.method==='GET') return send(res,200,{items:all('SELECT * FROM audit ORDER BY id DESC LIMIT 100')});
    throw new ApiError(404,'Admin endpoint not found');
   }
   if(path==='/api/app/register'&&req.method==='POST') {
    throttle(`register:${clientAddress(req)}`,30);const input=await body(req);validId(input.installation_id);check(!get('SELECT id FROM devices WHERE id=?',input.installation_id),'Installation already registered; use its credential',409);
    const token=randomBytes(32).toString('hex');run('INSERT INTO devices(id,credential_hash,first_seen,last_seen) VALUES(?,?,?,?)',input.installation_id,digest(token),now(),now());return send(res,201,{token});
   }
   if(path.startsWith('/api/app/')) {
    const d=device(req);throttle(`device:${d.id}`,60);
    if(path==='/api/app/profile'&&req.method==='PUT') {
     const input=await body(req);check(typeof input.analytics==='boolean','Analytics choice required');
     if(!input.analytics) tx(()=>{run('DELETE FROM ad_config_receipts WHERE device_id=?',d.id);run('DELETE FROM events WHERE device_id=?',d.id);run('DELETE FROM usage_daily WHERE device_id=?',d.id);run("UPDATE devices SET analytics=0,brand='',model='',api_level=NULL,density=NULL,locale='',country='',app_version='',last_seen=? WHERE id=?",now(),d.id);});
     else run('UPDATE devices SET analytics=1,brand=?,model=?,api_level=?,density=?,locale=?,country=?,app_version=?,last_seen=? WHERE id=?',text(input.brand,0,80,'brand'),text(input.model,0,120,'model'),integer(input.api_level,26,200,'API level'),integer(input.density,50,2000,'density'),text(input.locale,0,40,'locale'),text(input.country,0,3,'region'),text(input.app_version,0,40,'app version'),now(),d.id);
     return send(res,200,{ok:true});
    }
    if(path==='/api/app/events'&&req.method==='POST') {
     check(d.analytics===1,'Analytics is not enabled for this installation',403);const input=await body(req);check(Array.isArray(input.events)&&input.events.length<=100,'Use a batch of at most 100 events');
     const accepted=tx(()=>{let count=0;for(const e of input.events){validId(e.id);check(['app_opened','video_exported','image_edit_started','feature_selected','ad_impression'].includes(e.type),'Unknown event type');const feature=text(e.feature??'',0,100,'feature');const at=new Date(e.occurred_at);check(Number.isFinite(at.getTime())&&at.getTime()>=Date.now()-30*86400000&&at.getTime()<=Date.now()+300000,'Invalid event time');
      const result=run('INSERT OR IGNORE INTO events VALUES(?,?,?,?,?,?)',d.id,e.id,e.type,feature,at.toISOString(),now());if(result.changes){run('INSERT INTO usage_daily VALUES(?,?,?,?,1) ON CONFLICT(device_id,day,type,feature) DO UPDATE SET count=count+1',d.id,at.toISOString().slice(0,10),e.type,feature);count++;}}
      run('UPDATE devices SET last_seen=? WHERE id=?',now(),d.id);return count;});return send(res,200,{accepted});
    }
    if(path==='/api/app/config'&&req.method==='GET') return send(res,200,{...published(),sdk_required:'Use an ads-enabled build, configured mediation groups and UMP consent',delivery_requires_client_checks:true});
    if(path==='/api/app/config-receipt'&&req.method==='POST') {
     check(d.analytics===1,'Usage analytics consent required',403);const input=await body(req);
     const version=integer(input.version,1,Number.MAX_SAFE_INTEGER,'configuration version');
     check(get('SELECT version FROM config_versions WHERE version=?',version),'Unknown published version');
     check(typeof input.sdk_enabled==='boolean','Invalid SDK flag');
     run('INSERT INTO ad_config_receipts VALUES(?,?,?,?,?) ON CONFLICT(device_id) DO UPDATE SET version=excluded.version,sdk_enabled=excluded.sdk_enabled,app_version=excluded.app_version,received_at=excluded.received_at',d.id,version,input.sdk_enabled?1:0,text(input.app_version,1,40,'app version'),now());
     return send(res,200,{accepted:true,version});
    }
    if(path==='/api/app/tickets'&&req.method==='GET') return send(res,200,{items:all('SELECT * FROM tickets WHERE device_id=? ORDER BY updated_at DESC LIMIT 50',d.id)});
    if(path==='/api/app/tickets'&&req.method==='POST') {
     throttle(`ticket:${d.id}`,5);const input=await body(req);validId(input.id);const subject=text(input.subject,1,120,'reason');const description=text(input.description,1,4000,'description');
     const old=get('SELECT device_id FROM tickets WHERE id=?',input.id);check(!old||old.device_id===d.id,'Ticket conflict',409);
     if(!old)tx(()=>{run('INSERT INTO tickets VALUES(?,?,?,?,?,?)',input.id,d.id,subject,'open',now(),now());run('INSERT INTO messages VALUES(?,?,?,?,?)',input.id,input.id,'user',description,now());});return send(res,201,{id:input.id});
    }
    const support=path.match(/^\/api\/app\/tickets\/([a-f0-9-]+)$/);
    if(support&&req.method==='GET') {const t=ticket(support[1],d);return send(res,200,{...t,messages:messages(t.id)});}
    if(support&&req.method==='POST') {const t=ticket(support[1],d);const input=await body(req);validId(input.id);const reply=text(input.reply,1,4000,'reply');tx(()=>{run('INSERT OR IGNORE INTO messages VALUES(?,?,?,?,?)',input.id,t.id,'user',reply,now());run("UPDATE tickets SET status='open',updated_at=? WHERE id=?",now(),t.id);});return send(res,200,{ok:true});}
    if(path==='/api/app/installation'&&req.method==='DELETE') {run('DELETE FROM devices WHERE id=?',d.id);return send(res,200,{ok:true});}
    throw new ApiError(404,'App endpoint not found');
   }
   if(req.method==='GET' && ['/', '/app.js','/style.css'].includes(path)) {
    const name=path==='/'?'index.html':path.slice(1);res.writeHead(200,{'Content-Type':name.endsWith('.js')?'text/javascript; charset=utf-8':name.endsWith('.css')?'text/css; charset=utf-8':'text/html; charset=utf-8','Cache-Control':'no-cache'});return res.end(readFileSync(join(root,'public',name)));
   }
   throw new ApiError(404,'Not found');
  } catch(error) {if(!res.headersSent) send(res,error.status||500,{error:error.status?error.message:'Server error'});else res.end();if(!error.status)console.error(error.message);}
 });
 server.on('close',()=>{clearInterval(purge);db.close();});return server;
}
if(process.argv[1] && resolve(process.argv[1])===fileURLToPath(import.meta.url)) {
 const production=process.env.NODE_ENV==='production';
 if(production&&(!process.env.PUBLIC_ORIGIN||!process.env.LUMEN_DATABASE)) throw new Error('Production requires PUBLIC_ORIGIN and a persistent LUMEN_DATABASE path');
 const server=createApp({databasePath:process.env.LUMEN_DATABASE||join(root,'data','lumen.sqlite'),adminEmail:process.env.LUMEN_ADMIN_EMAIL,adminPassword:process.env.LUMEN_ADMIN_PASSWORD,production,publicOrigin:process.env.PUBLIC_ORIGIN||'',trustLoopbackProxy:process.env.TRUST_LOOPBACK_PROXY==='true'});
 delete process.env.LUMEN_ADMIN_PASSWORD;
 server.listen(Number(process.env.PORT||4173),process.env.HOST||'127.0.0.1',()=>console.log(`Lumen Admin listening on port ${server.address().port}`));
}
