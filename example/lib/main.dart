import 'package:flutter/material.dart';
import 'package:pedometer_history/pedometer_history.dart';

import 'dart:async';

void main() {
  runApp(MyApp());
}

class MyApp extends StatefulWidget {
  @override
  _MyAppState createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  final _stepCount = ValueNotifier<int>(null);

  @override
  void initState() {
    super.initState();
    initStepCount();
  }

  // Platform messages are asynchronous, so we initialize in an async method.
  Future<void> initStepCount() async {

    //final avail = await PedometerHistory.isAvailable();
    final pedom = await PedometerHistory.create();
    final to = DateTime.now();
    final from = to.subtract(Duration(days: 1));
    _stepCount.value = await pedom.getSteps(from, to);
    pedom.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(
          title: const Text('PedometerHistory example app'),
        ),
        body: Center(
          child: ValueListenableBuilder<int>(
            valueListenable: _stepCount,
            builder: (context, stepCount, child) => Text('Step count today: $stepCount')
          ),
        ),
      ),
    );
  }
}
