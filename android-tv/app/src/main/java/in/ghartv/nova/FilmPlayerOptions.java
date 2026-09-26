package in.ghartv.nova;

// GENERATED from tools/provider-access/player-options.mjs; do not edit separately.
final class FilmPlayerOptions {
    static final String JS="""
        const PlayerOptions=(()=>{
        /** Next-candidate normalization core. Input is bounded visible-control observations,
         * not a fetched catalogue or media list. It does not open URLs or select controls.
         * Android/WebView integration and live-provider acceptance are still separate work.
         */
        const APPROVED_ORIGINS = new Set([
          'https://flixmomo.app', 'https://flixmomo.st', 'https://www.flixmomo.st',
          'https://flixmomo.bet', 'https://www.flixmomo.bet',
        ]);
        const KINDS = new Set(['button', 'tab', 'select-option', 'link']);
        const clean = value => typeof value === 'string'
          ? value.replace(/[\\u0000-\\u001f\\u007f]/g, ' ').replace(/\\s+/gu, ' ').trim() : '';
        const NUMBERED = /^(player|server|source)\\s*#?\\s*([1-9][0-9]{0,2})(?=$|[\\s·•|])/iu;
        const BADGE = /^(?:OG|SD|HD|FHD|UHD|HDR|[248]K|BEST|GOOD|NEW|FAST|BETA|CC|🔥)$/iu;
        
        function approvedUrl(value) {
          try {
            const u = new URL(value);
            return !u.username && !u.password && !u.port && APPROVED_ORIGINS.has(u.origin) ? u : null;
          } catch { return null; }
        }
        
        function identifyPlayer(control) {
          if (!control || control.visible !== true || control.disabled === true || !KINDS.has(control.kind)) return null;
          const label = clean(control.text);
          if (!label || label.length > 220) return null;
          // directText excludes nested quality/status badges. Unknown badge text can be
          // shown as provider-supplied metadata; it does not change a stable player id.
          const direct = clean(control.directText);
          const directMatch = direct.match(/^(player|server|source)\\s*#?\\s*([1-9][0-9]{0,2})$/iu);
          const match = directMatch || label.match(NUMBERED);
          if (!match) return null;
          const suffix = directMatch ? '' : label.slice(match[0].length).replace(/^[\\s·•|]+/u, '').trim();
          if (!directMatch && suffix && !suffix.split(/[\\s·•|]+/u).every(x => BADGE.test(x))) return null;
          const type = match[1].toLowerCase(), number = Number(match[2]);
          const nested = Array.isArray(control.badges) ? control.badges.slice(0,8).map(clean).filter(x=>x&&x.length<=32) : [];
          const badges = [...new Set(nested.length ? nested : suffix ? suffix.split(/[\\s·•|]+/u) : [])];
          return Object.freeze({id: `${type}:${number}`, number,
            label: type[0].toUpperCase()+type.slice(1)+' '+number,
            providerLabel: label, badges, selected: control.selected === true,
            // A provider's BEST or 4K badge is not evidence of usable media or quality.
            playback: 'not-tested', quality: 'provider-claim-unverified'});
        }
        
        function enumeratePlayers(observations, pageUrl, {limit=48, scanLimit=700}={}) {
          const page = approvedUrl(pageUrl);
          if (!page) return {state:'origin-rejected', choices:[], detectedCount:0, truncated:false};
          if (!Array.isArray(observations) || !Number.isInteger(limit) || limit<1 || limit>64 || !Number.isInteger(scanLimit) || scanLimit<1 || scanLimit>1000)
            throw new TypeError('Bounded control observations and integer limits required');
          const choices=[], seen=new Set();
          for (const item of observations.slice(0,scanLimit)) {
            if (item?.kind==='link') {
              let u; try { u=approvedUrl(new URL(item.href,page).href); } catch { continue; }
              if (!u || u.origin!==page.origin || u.pathname!==page.pathname || u.search!==page.search) continue;
            }
            const p=identifyPlayer(item);
            if (!p || seen.has(p.id)) continue;
            seen.add(p.id);choices.push(p);
          }
          return {state:'observed',pageUrl:page.href,choices:choices.slice(0,limit),
            detectedCount:choices.length,returnedCount:Math.min(limit,choices.length),
            truncated:choices.length>limit || observations.length>scanLimit,
            providerAdvertisedTotal:null,workingSourceCount:null};
        }
        
        function resolvePlayer(selection, currentObservations, currentPageUrl) {
          // A refresh is required if navigation changed. Never click an old index after
          // the provider reorders its controls. The caller must also revalidate geometry.
          const page=approvedUrl(currentPageUrl);
          if (!page || !selection || selection.pageUrl!==page.href) return {state:'stale-page'};
          const snapshot=enumeratePlayers(currentObservations,currentPageUrl,{limit:64});
          const matches=snapshot.choices.filter(p=>p.id===selection.id);
          return matches.length===1 ? {state:'current-choice',choice:matches[0]} : {state:'choice-not-observed'};
        }
        
        return {identifyPlayer,enumeratePlayers,resolvePlayer};})();
        """;
}
