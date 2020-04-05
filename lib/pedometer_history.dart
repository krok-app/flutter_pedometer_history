import 'dart:async';

import 'package:flutter/services.dart';

class PedometerHistory {
  static const MethodChannel _channel =
      const MethodChannel('pedometer_history');

  int handle;

  PedometerHistory._(this.handle);

  static Future<bool> isAvailable() async {
    return await _channel.invokeMethod('isAvailable') as bool;
  }

  static Future<PedometerHistory> create() async {
    return PedometerHistory._(await _channel.invokeMethod('allocate') as int);
  }

  Future<void> dispose() async {
    if (handle != null) {
      await _channel.invokeMethod('release', handle);
      handle = null;
    }
  }

  static final _epoch = DateTime.fromMillisecondsSinceEpoch(0, isUtc: true);

  Future<int> getSteps(DateTime from, DateTime to) async {
    return await _channel.invokeMethod('get', <String, dynamic>{
      'pedom': handle,
      'from': from.difference(_epoch).inMilliseconds / 1000,
      'to': to.difference(_epoch).inMilliseconds / 1000
    }) as int;
  }
}
