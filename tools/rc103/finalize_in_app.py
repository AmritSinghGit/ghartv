from pathlib import Path
R=Path(__file__).resolve().parents[2]
p=R/'tools/films/GharTVFilmView.swift';s=p.read_text().replace('for item in [row, status, browser!] {','for item in ([row, status, browser!] as [NSView]) {');p.write_text(s)
p=R/'web-player/test/browser-security.test.mjs';s=p.read_text().replace('assert.match(films,/Search FlixMomo/)','assert.match(films,/Inside GharTV/)');p.write_text(s)
p=R/'android-tv/app/src/main/java/in/ghartv/nova/FlixMomoActivity.java';s=p.read_text()
if 'private boolean mainFrameError;' not in s:
 s=s.replace('private boolean pageReady;','private boolean pageReady;\n    private boolean mainFrameError;')
 s=s.replace('pageReady=false;status.setText("Opening provider','pageReady=false;mainFrameError=false;status.setText("Opening provider')
 s=s.replace('else if(pageReady)status.setText','else if(pageReady && !mainFrameError)status.setText')
 s=s.replace('@Override public void onReceivedSslError', '''@Override public void onReceivedHttpError(WebView v,WebResourceRequest request,WebResourceResponse response){
                if(request.isForMainFrame()){mainFrameError=true;status.setText("FlixMomo returned HTTP "+response.getStatusCode()+". Complete provider verification here if offered.");}
            }
            @Override public void onReceivedSslError''')
 p.write_text(s)
p=R/'tools/rc103/safari_smoke.py';s=p.read_text()
if "record['stage']='NATIVE_PROVIDER_FORM'" in s:
 a=s.index(" record['stage']='NATIVE_PROVIDER_FORM'");b=s.index(" record['stage']='NATIVE_HLS_TEST_MEDIA'",a)
 s=s[:a]+''' record['stage']='IN_APP_INFORMATION_PAGE';action('POST','/url',{'url':BASE+'/flixmomo.html'})
 wait_for(lambda:script("return document.body.innerText.includes('Inside GharTV')"))
 assert script("return !document.querySelector('form') && !document.querySelector('a[target=\\\"_blank\\\"]')")
 shot('safari-films.png');record['in_app_route']='PASS_NO_EXTERNAL_TAB_OR_NATIVE_EXECUTION'
'''+s[b:];p.write_text(s)
p=R/'tools/rc103/REVIEW.md';p.write_text('''# GharTV RC10.3 — in-app FlixMomo and visible TV review

This supersedes the unshipped external-tab proposal. The goal is search and playback inside GharTV, retaining FlixMomo's pages, attribution, account controls and player. No browser-detection flags, DRM, verification rules or response security headers are altered.

## Delivered components

Android code29 changes the existing FlixMomo activity to navigate directly to the provider's encoded /search?q= route. It no longer injects search scripts into the provider DOM. Search results, provider browsing, video/fullscreen controls and Back remain inside the GharTV activity. The existing TV guide and preview contract remain unchanged.

The Apple Silicon Mac review includes a compiled native GharTV FlixMomo view based on WebKit. It is launched only by the user's verified review command, not by HTTP requests. Its own Search/Browse/Back controls operate inside the GharTV window; the provider is not opened in an external browser and no browser profile is imported. The native component is ad-hoc signed, not notarized or an App Store release. The web-only viewer points to the native app instead of offering the rejected automated or external-tab substitute. Direct connection only; this is not Tor routing. A hosted browser-only edition still requires a permitted embed/API integration.

The same launcher now distinguishes the signed/installed Android package, Android activity foreground, and actual Mac window onscreen/frontmost observations. It fresh-checks the existing named Nova emulator on5580, starts only that existing AVD when resource admission allows, and brings forward its window. Headless, embedded, absent, blocked or unconfirmed states are not labelled visible-review success. No other VM, container or application is stopped.

## Run

Run GHARTV_REVIEW_RC10_3.command for the complete review: web, GharTV film window, original-key signing and the existing TV candidate. Use --tv-only to focus on TV review, --films-only to open only the native film window, or --web-only for the browser viewer. These are modes of the same delivery, not additional runtimes or products. Full local/Obsidian receipts and the existing safe GitHub mirror remain; no routine handoff paste is needed.

## Verification boundaries

Read VALIDATION.json for actual results. Cloud compilation, static/unit checks, native Mac window opening and provider-page navigation are separate from successfully playing a movie or a licensed channel. Provider human verification may still be required. Live provider video playback and physical-TV acceptance remain unverified until tested; no claim that every title is available. The prior saved code28 installation/foreground receipt is historical, not a statement that the owner's emulator is running now.

Public household feed and approved public homepage are unchanged. Analytics remains outside the viewer in the existing Operon Analytics lane. No signing key replacement, uninstall, data clearing, provider-account sharing, new database, AI upscaling, paid-service activation or production rollout is included.
''')
p=R/'GHARTV_LANE_PROGRESS.md';p.write_text('''<!-- GHARTV-MANAGED-LANE-NOTE:v1 -->
# GharTV current review — RC10.3 / code29

Search and provider playback remain inside GharTV's native provider view, not external tabs. Application, companion and launcher identities come from this release manifest. The public household feed is not changed. Observe fresh emulator state and Mac window visibility; an old foreground receipt is not a currently visible candidate. Full current runtime observations follow in the automatic receipt. Analytics stays in the existing Operon Analytics lane; no secrets or private account data are mirrored publicly.
''')
print('IN_APP_FINALIZATION_COMPLETE')
