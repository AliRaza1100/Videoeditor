// Optional ads build capabilities. Installed APK capabilities must be verified per device.
export const networks=[
 {id:'admob',name:'Google AdMob',route:'Primary SDK and mediation',guide:'https://developers.google.com/admob/android/quick-start'},
 {id:'applovin',name:'AppLovin',route:'AdMob mediation adapter',guide:'https://developers.google.com/admob/android/mediation/applovin'},
 {id:'unity',name:'Unity Ads',route:'AdMob mediation adapter',guide:'https://developers.google.com/admob/android/mediation/unity'},
 {id:'meta',name:'Meta Audience Network',route:'AdMob bidding adapter',guide:'https://developers.google.com/admob/android/mediation/meta'},
 {id:'ironsource',name:'ironSource Ads',route:'AdMob mediation adapter',guide:'https://developers.google.com/admob/android/mediation/ironsource'}
].map(n=>({...n,sdk_integrated:false,sdk_available_in_ads_build:true}));
export const placementMap=[
 {id:'home_banner',screen:'Home',action:'Open Home',location:'A reserved banner area below the content, above navigation.',rule:'Must not cover project cards or touch targets.'},
 {id:'projects_banner',screen:'Projects',action:'Open the project library',location:'A reserved footer above navigation.',rule:'Must not cover the New project button or project actions.'},
 {id:'export_complete',screen:'Export result',action:'Finish exporting and save the output successfully',location:'Fullscreen interstitial after completion.',rule:'Apply export frequency, cooldown and session caps. Never interrupt export.'},
 {id:'export_reward',screen:'Export options',action:'Explicitly tap Watch ad · pause ads for 30 minutes',location:'A clearly labelled optional reward action.',rule:'Grant 30 ad-free minutes in the current session only on the SDK reward callback. Cancellation keeps normal export available.'},
 {id:'preview_reward',screen:'Editor',action:'Explicitly tap Watch ad · pause ads for 30 minutes',location:'Optional action in the editor export-options sheet; never overlays preview playback.',rule:'Grant 30 ad-free minutes in the current session only on the SDK reward callback. Never trigger ads from Play, seeking or applying effects.'},
 {id:'app_open',screen:'Returning launch',action:'Return to the app and enter its loading screen',location:'App-open ad before loaded content is shown.',rule:'Skip first onboarding. Do not show over an active editor or after content is ready.'}
].map(p=>({...p,android_hook_integrated:true}));
