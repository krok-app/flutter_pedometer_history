package jp.espressso3389.pedometer_history

import android.Manifest
import android.app.Instrumentation
import android.content.ContentValues.TAG
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.util.Log
import androidx.annotation.NonNull
import androidx.core.app.ActivityCompat
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.fitness.Fitness
import com.google.android.gms.fitness.FitnessOptions
import com.google.android.gms.fitness.HistoryClient
import com.google.android.gms.fitness.data.DataSet
import com.google.android.gms.fitness.data.DataType
import com.google.android.gms.fitness.data.DataType.TYPE_STEP_COUNT_DELTA
import com.google.android.gms.fitness.request.DataReadRequest
import com.google.android.gms.tasks.Tasks
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry
import io.flutter.plugin.common.PluginRegistry.Registrar
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.text.DateFormat.getTimeInstance
import java.util.concurrent.TimeUnit

/** PedometerHistoryPlugin */
public class PedometerHistoryPlugin: FlutterPlugin, MethodCallHandler, ActivityAware {
  /// The MethodChannel that will the communication between Flutter and native Android
  ///
  /// This local reference serves to register the plugin with the Flutter Engine and unregister it
  /// when the Flutter Engine is detached from the Activity
  private lateinit var channel : MethodChannel
  private lateinit var binding : FlutterPlugin.FlutterPluginBinding
  private var activityBinding : ActivityPluginBinding? = null
  private val historyClients = HashMap<Int, HistoryClient>()
  private var lastClientId = 0;

  override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    channel = MethodChannel(flutterPluginBinding.getFlutterEngine().getDartExecutor(), "pedometer_history")
    channel.setMethodCallHandler(this)
    binding = flutterPluginBinding
  }

  // This static function is optional and equivalent to onAttachedToEngine. It supports the old
  // pre-Flutter-1.12 Android projects. You are encouraged to continue supporting
  // plugin registration via this function while apps migrate to use the new Android APIs
  // post-flutter-1.12 via https://flutter.dev/go/android-project-migration.
  //
  // It is encouraged to share logic between onAttachedToEngine and registerWith to keep
  // them functionally equivalent. Only one of onAttachedToEngine or registerWith will be called
  // depending on the user's project. onAttachedToEngine or registerWith must both be defined
  // in the same class.
  companion object {
    @JvmStatic
    fun registerWith(registrar: Registrar) {
      val channel = MethodChannel(registrar.messenger(), "pedometer_history")
      channel.setMethodCallHandler(PedometerHistoryPlugin())
    }
  }

  override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
    if (call.method == "allocate") {
      allocate(result)
    } else if (call.method == "release") {
      val id = call.arguments as Int
      historyClients.remove(id)
      result.success(null)
    } else if (call.method == "get") {
      val args = call.arguments as Map<String, Any>
      val id = args["pedom"] as Int
      val from = (args["from"] as Double).toLong()
      val to = (args["to"] as Double).toLong()
      getSteps(historyClients[id]!!, from, to, result)
    } else {
      result.notImplemented()
    }
  }

  fun getSteps(historyClient: HistoryClient, from: Long, to: Long, @NonNull result: Result) {
    GlobalScope.launch {
      val readRequest = DataReadRequest.Builder()
        .aggregate(DataType.TYPE_STEP_COUNT_DELTA, DataType.AGGREGATE_STEP_COUNT_DELTA)
        .bucketByTime(1, TimeUnit.DAYS)
        .setTimeRange(from, to, TimeUnit.SECONDS)
        .build()
      val dataSets = Tasks.await(historyClient.readData(readRequest)).dataSets
      for (ds in dataSets) {
        dumpDataSet(ds)
      }

      launch(Dispatchers.Main) { result.success(0) }
    }
  }

  private fun dumpDataSet(dataSet: DataSet) {
    Log.i(TAG, "Data returned for Data type: " + dataSet.getDataType().getName())
    val dateFormat = getTimeInstance()
    for (dp in dataSet.getDataPoints()) {
      Log.i(TAG, "Data point:")
      Log.i(TAG, "\tType: " + dp.getDataType().getName())
      Log.i(TAG, "\tStart: " + dateFormat.format(dp.getStartTime(TimeUnit.MILLISECONDS)))
      Log.i(TAG, "\tEnd: " + dateFormat.format(dp.getEndTime(TimeUnit.MILLISECONDS)))
      for (field in dp.getDataType().getFields()) {
        Log.i(TAG, "\tField: " + field.getName().toString() + " Value: " + dp.getValue(field))
      }
    }
  }

  fun allocate(@NonNull result: Result) {
    GlobalScope.launch {
      try {
        //
        // Manifest.permission.ACTIVITY_RECOGNITION
        //
        if (ActivityCompat.checkSelfPermission(activityBinding!!.activity, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
          val results = RequestPermissionsResult().run(arrayOf(Manifest.permission.ACTIVITY_RECOGNITION))
          if (results == null || results[0] != PackageManager.PERMISSION_GRANTED) {
            launch(Dispatchers.Main) { result.success(0) }
            return@launch
          }
        }

        //
        val fitnessOptions =
          FitnessOptions.builder()
            .addDataType(TYPE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_READ)
            .build()

        //
        // Google sign-in
        //
        var account = GoogleSignIn.getAccountForExtension(binding.applicationContext, fitnessOptions)
        if (account == null) {
          // show Google sign-in dialog
          val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
          val client = GoogleSignIn.getClient(binding.applicationContext, gso)
          val signInIntent = client.signInIntent
          StartActivityForResult().await(signInIntent)
          account = GoogleSignIn.getAccountForExtension(binding.applicationContext, fitnessOptions)
          if (account == null) {
            launch(Dispatchers.Main) { result.success(0) }
            return@launch
          }
        }

        //
        // Google Fit permissions
        //
        if (!GoogleSignIn.hasPermissions(account, fitnessOptions)) {
          val requestCodeForFitnessPermissions = 13467
          GoogleSignIn.requestPermissions(
            activityBinding!!.activity,
            requestCodeForFitnessPermissions,
            account!!,
            fitnessOptions)
          StartActivityForResult(requestCodeForFitnessPermissions).await()
          if (!GoogleSignIn.hasPermissions(account, fitnessOptions)) {
            launch(Dispatchers.Main) { result.success(0) }
            return@launch
          }
        }

        historyClients[++lastClientId] = Fitness.getHistoryClient(binding.applicationContext, account!!)
        launch(Dispatchers.Main) { result.success(lastClientId) }

      } catch (e: Exception) {
        Log.e(TAG, "Exception: $e")
        launch(Dispatchers.Main) { result.success(0) }
        return@launch
      }
    }
  }

  override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
    channel.setMethodCallHandler(null)
  }

  override fun onDetachedFromActivity() {
    activityBinding = null
  }

  override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
    activityBinding = binding
  }

  override fun onAttachedToActivity(binding: ActivityPluginBinding) {
    activityBinding = binding
  }

  override fun onDetachedFromActivityForConfigChanges() {
  }


  inner class StartActivityForResult: PluginRegistry.ActivityResultListener {
    val requestCode: Int
    private val dr = CompletableDeferred<Instrumentation.ActivityResult>()

    constructor(requestCode: Int? = null) {
      this.requestCode = if (requestCode == null) requestCode!! else 9123456
    }

    suspend fun await(intent: Intent): Instrumentation.ActivityResult {
      activityBinding!!.addActivityResultListener(this)
      try {
        activityBinding!!.activity.startActivityForResult(intent, requestCode)
        return dr.await()
      } finally {
        activityBinding!!.removeActivityResultListener(this)
      }
    }

    suspend fun await(): Instrumentation.ActivityResult {
      activityBinding!!.addActivityResultListener(this)
      try {
        return dr.await()
      } finally {
        activityBinding!!.removeActivityResultListener(this)
      }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
      if (requestCode == requestCode) {
        dr.complete(Instrumentation.ActivityResult(resultCode, data))
        return true
      }
      return false
    }
  }

  inner class RequestPermissionsResult: PluginRegistry.RequestPermissionsResultListener {
    private val requestCode = 9123457
    private val dr = CompletableDeferred<IntArray?>()

    suspend fun run(permissions: Array<String>): IntArray? {
      activityBinding!!.addRequestPermissionsResultListener(this)
      try {
        ActivityCompat.requestPermissions(activityBinding!!.activity,
          permissions,
          requestCode)
        return dr.await()
      } finally {
        activityBinding!!.removeRequestPermissionsResultListener(this)
      }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>?, grantResults: IntArray?): Boolean {
      if (requestCode == requestCode) {
        dr.complete(grantResults)
        return true
      }
      return false
    }
  }

}
