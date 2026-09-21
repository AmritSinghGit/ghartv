import AppKit
import CoreGraphics
import Foundation

// The caller has already verified the current user's exact target process.
// No window names, screenshots, input events or permissions are collected.
var result: [String:Any] = ["status":"INVALID_TARGET", "window_observed":false, "app_active":false]
if CommandLine.arguments.count == 2, let pid = Int32(CommandLine.arguments[1]), pid > 0 {
    let app = NSRunningApplication(processIdentifier: pid)
    let registered = app != nil
    var accepted = false
    if let app = app {
        app.unhide()
        accepted = app.activate(options: [.activateAllWindows])
        Thread.sleep(forTimeInterval: 0.35)
    }
    // Deliberately outside the optional NSRunningApplication branch.
    let all = CGWindowListCopyWindowInfo([.optionAll, .excludeDesktopElements], kCGNullWindowID) as? [[String:Any]] ?? []
    let own = all.filter { row in
        let owner = (row[kCGWindowOwnerPID as String] as? NSNumber)?.int32Value
        let layer = (row[kCGWindowLayer as String] as? NSNumber)?.intValue
        let bounds = row[kCGWindowBounds as String] as? [String:Any] ?? [:]
        let width = (bounds["Width"] as? NSNumber)?.doubleValue ?? 0
        let height = (bounds["Height"] as? NSNumber)?.doubleValue ?? 0
        return owner == pid && layer == 0 && width >= 240 && height >= 160
    }
    let visible = own.filter { row in
        ((row[kCGWindowIsOnscreen as String] as? NSNumber)?.boolValue ?? false) &&
        ((row[kCGWindowAlpha as String] as? NSNumber)?.doubleValue ?? 1) > 0
    }
    let frontmost = NSWorkspace.shared.frontmostApplication?.processIdentifier == pid
    let status = !visible.isEmpty ? (frontmost ? "MAC_WINDOW_FRONTMOST_OBSERVED" : "MAC_WINDOW_ONSCREEN_OBSERVED") : (own.isEmpty ? "NO_TARGET_WINDOW_OBSERVED" : "TARGET_WINDOW_NOT_ONSCREEN")
    result = ["status":status,"app_registered":registered,"activation_accepted":accepted,
              "target_windows":own.count,"onscreen_windows":visible.count,"window_observed":!visible.isEmpty,"app_active":frontmost]
}
if let data = try? JSONSerialization.data(withJSONObject: result), let text = String(data:data,encoding:.utf8) { print(text) }
