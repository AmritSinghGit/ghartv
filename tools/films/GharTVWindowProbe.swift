import AppKit
import CoreGraphics
import Foundation

// Invoked only by the local verified review command, with one already-validated PID.
// Output is restricted to booleans/counts; no window titles, pixels or names leave here.
let args = CommandLine.arguments
var result: [String:Any] = ["status":"MAC_WINDOW_UNCONFIRMED", "window_observed":false]
if args.count == 2, let pid = Int32(args[1]), pid > 0,
   let target = NSRunningApplication(processIdentifier: pid) {
    target.unhide()
    let accepted = target.activate(options: [.activateAllWindows, .activateIgnoringOtherApps])
    Thread.sleep(forTimeInterval: 0.6)
    let windows = CGWindowListCopyWindowInfo([.optionOnScreenOnly, .excludeDesktopElements], kCGNullWindowID) as? [[String:Any]] ?? []
    let own = windows.filter { row in
        guard let owner = row[kCGWindowOwnerPID as String] as? Int,
              let layer = row[kCGWindowLayer as String] as? Int,
              let bounds = row[kCGWindowBounds as String] as? [String:Any],
              let width = bounds["Width"] as? Double,
              let height = bounds["Height"] as? Double else { return false }
        let alpha = row[kCGWindowAlpha as String] as? Double ?? 1
        return owner == Int(pid) && layer == 0 && width >= 300 && height >= 180 && alpha > 0
    }
    let frontmost = NSWorkspace.shared.frontmostApplication?.processIdentifier == pid
    result = ["status":own.isEmpty ? "MAC_WINDOW_NOT_ONSCREEN" : (frontmost ? "MAC_WINDOW_FRONTMOST_OBSERVED" : "MAC_WINDOW_ONSCREEN_OBSERVED"),
              "activation_accepted":accepted,"onscreen_windows":own.count,"app_active":frontmost,"window_observed":!own.isEmpty]
}
if let data=try? JSONSerialization.data(withJSONObject:result), let text=String(data:data,encoding:.utf8) { print(text) }
