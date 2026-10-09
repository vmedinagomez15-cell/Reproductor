// Run with Playwright installed. Optional: FLAC_BROWSER=/path/to/chromium node tests/ui-smoke.cjs
const fs=require('fs'),path=require('path'),assert=require('assert');
const {chromium}=require(process.env.CODEX_PRIMARY_RUNTIME_NODE_MODULES?process.env.CODEX_PRIMARY_RUNTIME_NODE_MODULES+'/playwright':'playwright');
const wait=ms=>new Promise(resolve=>setTimeout(resolve,ms));
(async()=>{
 const browser=await chromium.launch({headless:true,...(process.env.FLAC_BROWSER?{executablePath:process.env.FLAC_BROWSER}:{}),args:['--no-sandbox']});
 const page=await browser.newPage({viewport:{width:390,height:816}}),errors=[];
 page.on('pageerror',e=>errors.push(e.message));
 await page.setContent(fs.readFileSync(path.join(__dirname,'../app/src/main/assets/index.html'),'utf8'));
 await page.evaluate(()=>{
  window.calls=[];window.Native={};
  ['artwork','chooseFolder','rescan','playTrack','toggle','skip','appearance','favorite','importLyrics','seek','lyricOffset'].forEach(name=>window.Native[name]=(...args)=>window.calls.push([name,...args]));
  const song={uri:'content://test/song.flac',title:'Entre luces',album:'Horizontes',artist:'Sesión local',duration:252000};
  window.onLibrary([song]);window.onTrack(song);
  window.onPosition({ready:true,playing:true,position:83000,duration:252000});
  window.onSettings({dark:true,glass:true,hue:2});
  window.onLyrics({lines:Array.from({length:120},(_,i)=>({time:i*2000,text:'Línea de prueba '+(i+1)})),offset:0});
 });
 assert(await page.evaluate(()=>calls.some(x=>x[0]==='artwork')),'Artwork requested before playing');
 await page.locator('.mini-open').click();
 await wait(140);assert(await page.locator('.motion-layer>*').count()>0,'Shared-element transition present');
 await page.waitForFunction(()=>!document.querySelector('.phone').classList.contains('transitioning'));
 async function layout(){return page.evaluate(()=>{
  const q=s=>document.querySelector(s),r=s=>q(s).getBoundingClientRect().toJSON();
  return{viewport:[innerWidth,innerHeight],pane:r('.full-player'),cover:r('.bigart'),progress:r('.progress'),controls:r('.play-controls'),footer:r('.extra-controls'),scroll:[q('.full-player').scrollHeight,q('.full-player').clientHeight],overflow:document.documentElement.scrollWidth>innerWidth};
 });}
 const initial=await layout();
 assert(Math.abs(initial.cover.width-initial.cover.height)<1,'Square cover');
 assert(initial.pane.bottom-initial.footer.bottom<=16,'Footer uses available height');
 assert(initial.scroll[0]<=initial.scroll[1]+1,'No portrait scrolling');
 if(process.env.FLAC_SCREENSHOTS)await page.screenshot({path:path.join(process.env.FLAC_SCREENSHOTS,'v04-player.png')});
 await page.locator('.lyrics-toggle').click();await wait(120);
 assert(await page.evaluate(()=>document.querySelector('.lyrics-panel').getAnimations().length>0),'Lyrics reveal animation');
 await wait(550);
 assert(await page.locator('.player-content').isHidden(),'Artwork retired');
 assert(await page.locator('.lyrics-panel').isVisible(),'Lyrics visible');
 assert.equal(await page.locator('.lyric-line.active').innerText(),'Línea de prueba 42');
 const active=await page.locator('.lyric-line.active').boundingBox(),lyrics=await page.locator('.lyrics-content').boundingBox();
 assert(active.y>=lyrics.y&&active.y<lyrics.y+lyrics.height,'Current lyric scrolled into view');
 const afterLyrics=await layout();assert.equal(afterLyrics.controls.y,initial.controls.y,'Controls stay in place');
 if(process.env.FLAC_SCREENSHOTS)await page.screenshot({path:path.join(process.env.FLAC_SCREENSHOTS,'v04-lyrics.png')});
 await page.locator('.lyric-line.active').click();assert(await page.evaluate(()=>calls.some(x=>x[0]==='seek'&&x[1]===82000)),'Seek from lyric');
 await page.locator('#lyric-earlier').click();assert(await page.evaluate(()=>calls.some(x=>x[0]==='lyricOffset'&&x[1]===500)));
 assert(await page.evaluate(()=>window.goBack()));await wait(600);assert(await page.locator('.lyrics-panel').isHidden(),'Back closes lyrics first');
 // Rapid toggles and resize must leave exactly one visual panel and no stuck transition.
 await page.evaluate(()=>{document.querySelector('.lyrics-toggle').click();setTimeout(()=>document.querySelector('.lyrics-toggle').click(),70)});await wait(750);
 assert(await page.locator('.lyrics-panel').isHidden());assert(await page.locator('.player-content').isVisible());
 await page.locator('.more').click();assert.equal(await page.locator('.song-menu button').count(),1);await page.locator('#import-lyrics').click();assert(await page.evaluate(()=>calls.some(x=>x[0]==='importLyrics')));
 await page.locator('.lyrics-toggle').click();await wait(600);
 await page.locator('.close-player').click();await page.waitForFunction(()=>document.querySelector('.full-player').classList.contains('hidden'));
 assert.equal(await page.locator('.motion-layer>*').count(),0);assert(await page.locator('.mini-open').isVisible());
 await page.locator('.mini-open').click();await page.waitForFunction(()=>!document.querySelector('.phone').classList.contains('transitioning'));
 assert(await page.locator('.lyrics-panel').isVisible(),'Lyrics state survives minimization');
 await page.locator('.lyrics-toggle').click();await wait(600);
 for(const viewport of [{width:320,height:640},{width:390,height:844},{width:430,height:900},{width:736,height:390}]){
  await page.setViewportSize(viewport);await wait(100);const m=await layout();
  assert(!m.overflow,'No horizontal overflow '+JSON.stringify(viewport));
  assert(Math.abs(m.cover.width-m.cover.height)<1,'Square cover after resize');
  assert(m.footer.bottom<=m.pane.bottom+1,'Footer within viewport');
  assert(m.scroll[0]<=m.scroll[1]+1,'Controls fit viewport '+JSON.stringify(viewport));
 }
 await page.setViewportSize({width:390,height:816});
 await page.evaluate(()=>window.onSettings({dark:false,glass:false,hue:0}));
 if(process.env.FLAC_SCREENSHOTS)await page.screenshot({path:path.join(process.env.FLAC_SCREENSHOTS,'v04-light.png')});
 // Empty lyrics and playback callbacks remain usable with reduced motion.
 await page.emulateMedia({reducedMotion:'reduce'});
 await page.evaluate(()=>window.onLyrics({lines:[],offset:0}));
 await page.locator('.lyrics-toggle').click();await wait(150);assert(await page.locator('.lyrics-content .empty').isVisible());
 await page.locator('.lyrics-toggle').click();await wait(150);
 await page.locator('.close-player').click();await wait(150);assert(await page.locator('.full-player').isHidden());
 await page.locator('[data-nav="settings"]').click();await page.locator('#choose-folder').click();await page.locator('#rescan').click();
 assert(await page.evaluate(()=>calls.some(x=>x[0]==='chooseFolder')&&calls.some(x=>x[0]==='rescan')));
 await page.locator('.mini-open').click();await wait(150);await page.locator('.main-play').click();assert(await page.evaluate(()=>calls.some(x=>x[0]==='toggle')));
 await page.evaluate(()=>window.onTrack(null));await wait(150);assert(await page.locator('.full-player').isHidden());
 assert.equal(await page.locator('.motion-layer>*').count(),0);assert.deepEqual(errors,[]);
 await browser.close();console.log('UI 0.4: transitions, lyrics, playback bridge, safe viewport layouts and reduced motion passed.');
})().catch(error=>{console.error(error);process.exit(1)});
