import Flutter
import UIKit
import CoreMotion

public class SwiftPedometerHistoryPlugin: NSObject, FlutterPlugin {
  public static func register(with registrar: FlutterPluginRegistrar) {
    let channel = FlutterMethodChannel(name: "pedometer_history", binaryMessenger: registrar.messenger())
    let instance = SwiftPedometerHistoryPlugin()
    registrar.addMethodCallDelegate(instance, channel: channel)
  }
  
  public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
    if call.method == "isAvailable" {
      result(CMPedometer.isStepCountingAvailable())
      return
    } else if call.method == "allocate" {
      let p = CMPedometer()
      result(pedom2Int(p))
      return
    } else if call.method == "release" {
      guard call.arguments is UInt64 else {
        result(nil)
        return
      }
      intRelease(call.arguments as! UInt64)
      result(nil)
      return
    } else if call.method == "get" {
      let args = call.arguments as! [String: Any]
      let p = args["pedom"]
      guard p is UInt64 else {
        result(nil)
        return
      }
      let pedom = int2Pedom(p as! UInt64)
      guard pedom != nil else {
        result(nil)
        return
      }
      let from = Date(timeIntervalSince1970: args["from"] as! Double)
      let to = Date(timeIntervalSince1970: args["to"] as! Double)
      pedom!.queryPedometerData(from: from, to: to, withHandler: { (data, error) in
        DispatchQueue.main.async {
            result(data?.numberOfSteps)
        }
      })
      return
    } else {
      result(nil)
    }
  }
  
  func pedom2Int(_ pedom: CMPedometer) -> UInt64 {
    let unmanaged = Unmanaged<CMPedometer>.passRetained(pedom)
    let p = unmanaged.toOpaque()
    let u = UInt64(UInt(bitPattern: p))
    print("pedom2Int: \(u)")
    return u
  }
  
  func int2Pedom(_ data: UInt64) -> CMPedometer? {
    print("int2Pedom: \(data)")
    guard let p = UnsafeMutableRawPointer(bitPattern: UInt(truncatingIfNeeded: data)) else { return nil }
    return Unmanaged<CMPedometer>.fromOpaque(p).takeUnretainedValue()
  }
  
  func intRelease(_ data: UInt64) {
    guard let p = UnsafeMutableRawPointer(bitPattern: UInt(truncatingIfNeeded: data)) else { return }
    Unmanaged<CMPedometer>.fromOpaque(p).release()
  }
}
