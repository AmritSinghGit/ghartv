import AppKit
import WebKit

// Normal, attributed in-app provider view: no automation, script injection,
// API-key extraction, header rewriting, profile import or anti-detection changes.
final class FilmView: NSObject, NSApplicationDelegate, NSWindowDelegate, WKNavigationDelegate, WKUIDelegate {
    var window: NSWindow!
    var browser: WKWebView!
    let query = NSSearchField()
    let status = NSTextField(labelWithString: "FlixMomo · Direct connection · Provider controls and sign-in remain unchanged")
    var lastNavigationFailed = false
    func applicationDidFinishLaunching(_ notification: Notification) {
        NSApp.setActivationPolicy(.regular)
        let bar = NSMenu(); let menu = NSMenuItem(); bar.addItem(menu)
        let appMenu = NSMenu()
        appMenu.addItem(withTitle: "Quit GharTV Films", action: #selector(NSApplication.terminate(_:)), keyEquivalent: "q")
        menu.submenu = appMenu
        let editItem = NSMenuItem(title: "Edit", action: nil, keyEquivalent: "")
        let edit = NSMenu(title: "Edit")
        for (title, action, key) in [("Copy", "copy:", "c"), ("Paste", "paste:", "v"), ("Cut", "cut:", "x"), ("Select All", "selectAll:", "a")] {
            edit.addItem(withTitle: title, action: Selector(action), keyEquivalent: key)
        }
        editItem.submenu = edit; bar.addItem(editItem); NSApp.mainMenu = bar
        window = NSWindow(contentRect: NSRect(x: 70, y: 80, width: 1160, height: 760), styleMask: [.titled, .closable, .miniaturizable, .resizable], backing: .buffered, defer: false)
        window.title = "GharTV · FlixMomo"; window.minSize = NSSize(width: 760, height: 500)
        window.delegate = self; window.appearance = NSAppearance(named: .darkAqua)
        let content = NSView(); window.contentView = content
        let title = NSTextField(labelWithString: "GharTV  /  FlixMomo"); title.font = .boldSystemFont(ofSize: 19)
        query.placeholderString = "Search FlixMomo inside GharTV"; query.target = self; query.action = #selector(search)
        let row = NSStackView(views: [title, query,
            NSButton(title: "Search", target: self, action: #selector(search)),
            NSButton(title: "Back", target: self, action: #selector(goBack)),
            NSButton(title: "Browse", target: self, action: #selector(browse)),
            NSButton(title: "Reload", target: self, action: #selector(reload))])
        row.spacing = 10; row.orientation = .horizontal
        status.font = .systemFont(ofSize: 12); status.maximumNumberOfLines = 2
        let config = WKWebViewConfiguration(); config.websiteDataStore = .nonPersistent()
        config.preferences.javaScriptCanOpenWindowsAutomatically = false
        config.mediaTypesRequiringUserActionForPlayback = .all
        browser = WKWebView(frame: .zero, configuration: config)
        browser.navigationDelegate = self; browser.uiDelegate = self; browser.allowsBackForwardNavigationGestures = true
        for item in ([row, status, browser!] as [NSView]) { item.translatesAutoresizingMaskIntoConstraints = false; content.addSubview(item) }
        NSLayoutConstraint.activate([
            row.topAnchor.constraint(equalTo: content.topAnchor, constant: 14), row.leadingAnchor.constraint(equalTo: content.leadingAnchor, constant: 16), row.trailingAnchor.constraint(equalTo: content.trailingAnchor, constant: -16), query.widthAnchor.constraint(greaterThanOrEqualToConstant: 220),
            status.topAnchor.constraint(equalTo: row.bottomAnchor, constant: 8), status.leadingAnchor.constraint(equalTo: row.leadingAnchor), status.trailingAnchor.constraint(equalTo: row.trailingAnchor),
            browser.topAnchor.constraint(equalTo: status.bottomAnchor, constant: 10), browser.leadingAnchor.constraint(equalTo: content.leadingAnchor), browser.trailingAnchor.constraint(equalTo: content.trailingAnchor), browser.bottomAnchor.constraint(equalTo: content.bottomAnchor)
        ])
        window.center(); window.makeKeyAndOrderFront(nil); NSApp.activate(ignoringOtherApps: true)
        emit(["event":"window_ready", "windowVisible":window.isVisible])
        let args = CommandLine.arguments
        if args.count == 3, args[1] == "--query", let url = target("search", args[2]) {
            query.stringValue = args[2]; open(url)
        } else { open(URL(string: "https://flixmomo.app/")!) }

    }
    func target(_ action: String, _ text: String) -> URL? {
        if action == "browse" { return URL(string: "https://flixmomo.app/") }
        guard action == "search", text.count >= 2, text.count <= 120, !text.unicodeScalars.contains(where: { CharacterSet.controlCharacters.contains($0) }) else { return nil }
        var url = URLComponents(string: "https://flixmomo.app/search")!
        url.queryItems = [URLQueryItem(name: "q", value: text)]; return url.url
    }
    func open(_ url: URL) {
        lastNavigationFailed = false; window.deminiaturize(nil); window.makeKeyAndOrderFront(nil); NSApp.activate(ignoringOtherApps: true)
        status.stringValue = "Loading FlixMomo inside GharTV… Complete any provider verification here."
        browser.load(URLRequest(url: url))
    }
    @objc func search() {
        let text = query.stringValue.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let url = target("search", text) else { status.stringValue = "Enter 2–120 characters."; return }; open(url)
    }
    @objc func browse() { open(URL(string: "https://flixmomo.app/")!) }
    @objc func goBack() { if browser.canGoBack { browser.goBack() } }
    @objc func reload() { browser.reload() }
    func localHost(_ host: String) -> Bool {
        let h = host.lowercased()
        return h == "localhost" || h.hasSuffix(".local") || h.hasSuffix(".localhost") || h.hasSuffix(".internal") || h.contains(":") || h.range(of: "^[0-9.]+$", options: .regularExpression) != nil
    }
    func webView(_ webView: WKWebView, decidePolicyFor navigationAction: WKNavigationAction, decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
        guard let url = navigationAction.request.url else { decisionHandler(.cancel); return }
        let top = navigationAction.targetFrame?.isMainFrame ?? true
        if !top && ["about", "blob", "data"].contains(url.scheme ?? "") { decisionHandler(.allow); return }
        guard url.scheme == "https", url.user == nil, url.password == nil, url.port == nil, let host = url.host, !localHost(host), (!top || host == "flixmomo.app") else {
            if top { status.stringValue = "External navigation stayed blocked. Search and playback remain in the provider view." }; decisionHandler(.cancel); return
        }; decisionHandler(.allow)
    }
    func webView(_ webView: WKWebView, decidePolicyFor navigationResponse: WKNavigationResponse, decisionHandler: @escaping (WKNavigationResponsePolicy) -> Void) {
        if navigationResponse.isForMainFrame, let response = navigationResponse.response as? HTTPURLResponse, response.statusCode >= 400 {
            lastNavigationFailed = true; status.stringValue = "FlixMomo returned HTTP \(response.statusCode). Provider verification or availability may prevent this page."
            emit(["event":"provider_response","httpStatus":response.statusCode,"playbackVerified":false])
        }; decisionHandler(navigationResponse.canShowMIMEType ? .allow : .cancel)
    }
    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        if webView.url?.path == "/dummy" {
            status.stringValue = "FlixMomo refused this embedded session. Search/playback is not verified; no protection was changed."
            emit(["event":"provider_blocked","code":"DUMMY_REDIRECT","playbackVerified":false]); return
        }
        if !lastNavigationFailed { status.stringValue = "FlixMomo in GharTV · Select a result and use the provider’s player · Direct connection" }
        emit(["event":"page_finished","providerPage":webView.url?.host == "flixmomo.app","pageError":lastNavigationFailed,"providerPath":webView.url?.path == "/search" ? "search" : "other","playbackVerified":false])
    }
    func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) { failed(error) }
    func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) { failed(error) }
    func failed(_ error: Error) {
        if (error as NSError).code == NSURLErrorCancelled { return }
        status.stringValue = "The provider page could not load. Your query is preserved; use Reload to try again."
        emit(["event":"navigation_failed","code":(error as NSError).code,"playbackVerified":false])
    }
    func webViewWebContentProcessDidTerminate(_ webView: WKWebView) { status.stringValue = "The provider view stopped. Reload to reopen it; no automatic loop was started." }
    func webView(_ webView: WKWebView, createWebViewWith configuration: WKWebViewConfiguration, for navigationAction: WKNavigationAction, windowFeatures: WKWindowFeatures) -> WKWebView? {
        if navigationAction.navigationType == .linkActivated, let url = navigationAction.request.url, url.scheme == "https", url.host == "flixmomo.app", url.user == nil, url.password == nil { open(url) }; return nil
    }
    func webView(_ webView: WKWebView, requestMediaCapturePermissionFor origin: WKSecurityOrigin, initiatedByFrame frame: WKFrameInfo, type: WKMediaCaptureType, decisionHandler: @escaping (WKPermissionDecision) -> Void) { decisionHandler(.deny) }
    func applicationShouldTerminateAfterLastWindowClosed(_ sender: NSApplication) -> Bool { return true }
    func emit(_ value: [String:Any]) { if let data = try? JSONSerialization.data(withJSONObject:value), let text=String(data:data,encoding:.utf8) { print(text); fflush(stdout) } }
}
let app = NSApplication.shared
let delegate = FilmView()
app.delegate = delegate
app.run()
