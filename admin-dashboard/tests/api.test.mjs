import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { createApp } from '../server.mjs';
let server,base,cookie;
before(async()=>{server=createApp({adminEmail:'owner@example.test',adminPassword:'Local-test-password'});await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));base=`http://127.0.0.1:${server.address().port}`;});
after(async()=>{await new Promise(resolve=>server.close(resolve));});
async function request(path,method='GET',data,{token,auth=true,origin=true}={}){
 const response=await fetch(base+path,{method,headers:{...(data?{'Content-Type':'application/json'}:{}),...(origin?{Origin:base}:{}),...(auth&&cookie?{Cookie:cookie}:{}),...(token?{Authorization:`Bearer ${token}`}:{})},body:data?JSON.stringify(data):undefined});
 return {status:response.status,data:await response.json(),cookie:response.headers.get('set-cookie')};
}
test('complete protected admin / anonymous installation workflow',async()=>{
 assert.equal((await request('/api/admin/overview')).status,401);
 assert.equal((await request('/api/app/public-config','GET',undefined,{auth:false})).data.delivery_requires_client_checks,true);
 assert.equal((await request('/api/admin/login','POST',{email:'owner@example.test',password:'Local-test-password'},{origin:false})).status,403);
 const login=await request('/api/admin/login','POST',{email:'owner@example.test',password:'Local-test-password'});assert.equal(login.status,200);cookie=login.cookie.split(';')[0];assert.ok(login.cookie.includes('HttpOnly'));
 const a=randomUUID(),b=randomUUID();const tokenA=(await request('/api/app/register','POST',{installation_id:a})).data.token;const tokenB=(await request('/api/app/register','POST',{installation_id:b})).data.token;
 assert.equal((await request('/api/app/register','POST',{installation_id:a})).status,409);
 assert.equal((await request('/api/app/events','POST',{events:[]},{token:tokenA})).status,403);
 const profile={analytics:true,brand:'Test',model:'Test Phone',api_level:36,density:420,locale:'en-PK',country:'PK',app_version:'test'};
 assert.equal((await request('/api/app/profile','PUT',profile,{token:tokenA})).status,200);
 const event={id:randomUUID(),type:'video_exported',feature:'',occurred_at:new Date().toISOString()};
 assert.equal((await request('/api/app/events','POST',{events:[event]},{token:tokenA})).data.accepted,1);
 assert.equal((await request('/api/app/events','POST',{events:[event]},{token:tokenA})).data.accepted,0);
 let overview=(await request('/api/admin/overview')).data;assert.equal(overview.installations,1);assert.equal(overview.exports_today,1);assert.deepEqual(overview.revenue,[]);
 const devices=(await request('/api/admin/devices?q=Test')).data;assert.equal(devices.total,1);assert.equal(devices.items[0].credential_hash,undefined);
 const id=randomUUID();assert.equal((await request('/api/app/tickets','POST',{id,subject:'Export help',description:'An export failed.'},{token:tokenA})).status,201);
 assert.equal((await request(`/api/app/tickets/${id}`,'GET',undefined,{token:tokenB})).status,404);
 assert.equal((await request(`/api/admin/tickets/${id}`,'POST',{reply:'Please try 720p.',status:'pending'})).status,200);
 const thread=(await request(`/api/app/tickets/${id}`,'GET',undefined,{token:tokenA})).data;assert.equal(thread.messages.length,2);assert.equal(thread.messages[1].body,'Please try 720p.');
 const config=(await request('/api/admin/config')).data;
 assert.equal((await request('/api/admin/config','PUT',{revision:config.revision+1,config:config.draft})).status,409);
 config.draft.placements[0].enabled=true;assert.equal((await request('/api/admin/config','PUT',{revision:config.revision,config:config.draft})).status,400);
 config.draft.placements[0].enabled=false;
 const saved=await request('/api/admin/config','PUT',{revision:config.revision,config:config.draft});assert.equal(saved.status,200);
 const published=await request('/api/admin/config/publish','POST',{revision:saved.data.revision});assert.equal(published.status,200);
 const effective=(await request('/api/app/config','GET',undefined,{token:tokenA})).data;assert.equal(effective.delivery_requires_client_checks,true);
 assert.equal((await request('/api/admin/config/rollback','POST',{version:1})).status,200);
 assert.equal((await request('/api/admin/revenue','POST',{day:'2026-10-20',currency:'USD',estimated_micros:1230000,impressions:20})).status,200);
 assert.equal((await request('/api/admin/revenue')).data.items[0].estimated_micros,1230000);
 assert.equal((await request('/api/app/profile','PUT',{analytics:false},{token:tokenA})).status,200);
 overview=(await request('/api/admin/overview')).data;assert.equal(overview.installations,0);assert.equal(overview.exports_today,0);
 assert.equal((await request(`/api/app/tickets/${id}`,'GET',undefined,{token:tokenA})).status,200);
 assert.equal((await request('/api/app/installation','DELETE',undefined,{token:tokenA})).status,200);
 assert.equal((await request('/api/app/tickets','GET',undefined,{token:tokenA})).status,401);
 assert.equal((await request('/api/admin/logout','POST',{})).status,200);
 assert.equal((await request('/api/admin/session')).status,401);
});

 test('network plans are validated, versioned and never activate SDKs',async()=>{
 const login=await request('/api/admin/login','POST',{email:'owner@example.test',password:'Local-test-password'});cookie=login.cookie.split(';')[0];
 const session=(await request('/api/admin/session')).data;
 assert.equal(session.networks.length,5);assert.equal(session.placement_map.length,6);
 assert.ok(session.networks.every(n=>n.sdk_integrated===false));assert.ok(session.placement_map.every(p=>p.android_hook_integrated===true));
 const draft=(await request('/api/admin/config')).data;
 assert.equal((await request('/api/admin/config','PUT',{revision:draft.revision,config:{...draft.draft,network_requests:['unknown']}})).status,400);
 assert.equal((await request('/api/admin/config','PUT',{revision:draft.revision,config:{...draft.draft,network_requests:['meta','meta']}})).status,400);
 const saved=await request('/api/admin/config','PUT',{revision:draft.revision,config:{...draft.draft,network_requests:['admob','unity','meta']}});assert.equal(saved.status,200);
 await request('/api/admin/config/publish','POST',{revision:saved.data.revision});
 const config=(await request('/api/app/public-config','GET',undefined,{auth:false})).data;
 assert.deepEqual(config.network_requests,['admob','unity','meta']);assert.equal(config.delivery_requires_client_checks,true);
 });

test('partner routing round-trip, receipt authentication and rollback',async()=>{
 const login=await request('/api/admin/login','POST',{email:'owner@example.test',password:'Local-test-password'});cookie=login.cookie.split(';')[0];
 let draft=(await request('/api/admin/config')).data;
 const config=structuredClone(draft.draft);config.network_requests=['admob','meta','ironsource','unity','applovin'];config.ads_enabled=true;
 const p=config.placements[0];p.enabled=true;p.network='ironsource';p.fallback_network='meta';p.ad_unit_id='ca-app-pub-1234567890123456/1234567890';p.network_ad_units={ironsource:p.ad_unit_id,meta:'ca-app-pub-1234567890123456/1234567891'};
 const invalid=structuredClone(config);invalid.placements[0].network_ad_units.meta='partner-placement-id';
 assert.equal((await request('/api/admin/config','PUT',{revision:draft.revision,config:invalid})).status,400);
 const badOpen=structuredClone(config);badOpen.placements.find(p=>p.id==='app_open').network='meta';
 assert.equal((await request('/api/admin/config','PUT',{revision:draft.revision,config:badOpen})).status,400);
 const saved=await request('/api/admin/config','PUT',{revision:draft.revision,config});assert.equal(saved.status,200);
 const published=await request('/api/admin/config/publish','POST',{revision:saved.data.revision});assert.equal(published.status,200);
 const live=(await request('/api/app/public-config','GET',undefined,{auth:false})).data;
 assert.equal(live.schema_version,1);assert.equal(live.delivery_mode,'admob_mediation');assert.equal(live.placements[0].network,'ironsource');assert.deepEqual(live.placements[0].network_ad_units,p.network_ad_units);
 assert.equal(live.ads_enabled,true);assert.equal(live.delivery_requires_client_checks,true);
 const token=(await request('/api/app/register','POST',{installation_id:randomUUID()})).data.token;
 const receipt={version:live.version,sdk_enabled:true,app_version:'test'};
 assert.equal((await request('/api/app/config-receipt','POST',receipt,{auth:false})).status,401);
 assert.equal((await request('/api/app/config-receipt','POST',receipt,{token})).status,403);
 await request('/api/app/profile','PUT',{analytics:true,brand:'Test',model:'Phone',api_level:36,density:420,locale:'en',country:'PK',app_version:'test'},{token});
 assert.equal((await request('/api/app/config-receipt','POST',{...receipt,version:999999},{token})).status,400);
 assert.equal((await request('/api/app/config-receipt','POST',receipt,{token})).data.version,live.version);
 let receipts=(await request('/api/admin/config/receipts')).data;assert.ok(receipts.items.some(r=>r.version===live.version&&r.sdk_enabled===1));
 await request('/api/app/profile','PUT',{analytics:false},{token});
 receipts=(await request('/api/admin/config/receipts')).data;assert.equal(receipts.items.length,0);
 await request('/api/app/installation','DELETE',undefined,{token});
 await request('/api/admin/config/rollback','POST',{version:1});
 assert.equal((await request('/api/app/public-config','GET',undefined,{auth:false})).data.ads_enabled,false);
});

test('export rules and backup network validation',async()=>{
 const draft=(await request('/api/admin/config')).data;
 const config=structuredClone(draft.draft);config.export_rules={offer_reward:true,resolutions:[480,1080,2000]};
 let saved=await request('/api/admin/config','PUT',{revision:draft.revision,config});assert.equal(saved.status,200);
 await request('/api/admin/config/publish','POST',{revision:saved.data.revision});
 assert.deepEqual((await request('/api/app/public-config','GET',undefined,{auth:false})).data.export_rules,config.export_rules);
 const latest=(await request('/api/admin/config')).data;
 const bad=structuredClone(latest.draft);bad.export_rules.resolutions=[999];
 assert.equal((await request('/api/admin/config','PUT',{revision:latest.revision,config:bad})).status,400);
 const missing=structuredClone(latest.draft);missing.ads_enabled=true;missing.placements[0].enabled=true;missing.placements[0].ad_unit_id='ca-app-pub-1234567890123456/1234567890';
 assert.equal((await request('/api/admin/config','PUT',{revision:latest.revision,config:missing})).status,400);
 const same=structuredClone(missing);same.placements[0].fallback_network='admob';
 assert.equal((await request('/api/admin/config','PUT',{revision:latest.revision,config:same})).status,400);
 await request('/api/admin/config/rollback','POST',{version:1});
});

test('production origin is pinned behind the proxy',async()=>{
 const app=createApp({adminEmail:'owner@example.test',adminPassword:'Production-test-password',production:true,publicOrigin:'https://admin.example.test',trustLoopbackProxy:true});
 await new Promise(resolve=>app.listen(0,'127.0.0.1',resolve));
 try {
  const url=`http://127.0.0.1:${app.address().port}/api/admin/login`;
  const credentials=JSON.stringify({email:'owner@example.test',password:'Production-test-password'});
  const bad=await fetch(url,{method:'POST',headers:{'Content-Type':'application/json',Origin:'https://evil.example',Host:'evil.example'},body:credentials});assert.equal(bad.status,403);
  const good=await fetch(url,{method:'POST',headers:{'Content-Type':'application/json',Origin:'https://admin.example.test'},body:credentials});assert.equal(good.status,200);assert.ok(good.headers.get('set-cookie').includes('Secure'));
 } finally { await new Promise(resolve=>app.close(resolve)); }
});
