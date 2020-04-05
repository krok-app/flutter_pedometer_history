#import "PedometerHistoryPlugin.h"
#if __has_include(<pedometer_history/pedometer_history-Swift.h>)
#import <pedometer_history/pedometer_history-Swift.h>
#else
// Support project import fallback if the generated compatibility header
// is not copied when this plugin is created as a library.
// https://forums.swift.org/t/swift-static-libraries-dont-copy-generated-objective-c-header/19816
#import "pedometer_history-Swift.h"
#endif

@implementation PedometerHistoryPlugin
+ (void)registerWithRegistrar:(NSObject<FlutterPluginRegistrar>*)registrar {
  [SwiftPedometerHistoryPlugin registerWithRegistrar:registrar];
}
@end
