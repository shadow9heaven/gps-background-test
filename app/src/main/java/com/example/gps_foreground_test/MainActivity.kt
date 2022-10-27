package com.example.gps_foreground_test


import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.LocationManager
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import org.java_websocket.WebSocket
import org.java_websocket.client.WebSocketClient
import org.java_websocket.drafts.Draft_6455
import org.java_websocket.handshake.ServerHandshake
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.URI
import java.util.*

lateinit var locationManager: LocationManager

//var temp = "test"

@SuppressLint("StaticFieldLeak")
lateinit var tv_websocket: TextView

@SuppressLint("StaticFieldLeak")
lateinit var tv_apiresponse: TextView
var oldColor: Int = Color.BLACK
private const val DEV_WEBSITE_URL = "https://portalapp-dev.wistron.com"
var webSocketInterval = 30 * 1000
var closed_By_User = false

class MainActivity : AppCompatActivity() {

    var latitude: Double = 0.0
    var longitude: Double = 0.0

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf<String>(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ), 100
            )
        }

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            ///request background permission
            if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            ) {
                ActivityCompat.requestPermissions(
                    this, arrayOf<String>(
                        Manifest.permission.ACCESS_BACKGROUND_LOCATION
                    ), 87
                )

                //Log.e("showpermissionrationale", backtutorial.toString())
                //getBackgroundPermissionOptionLabel()
            } else {

            }
        }
        setContentView(R.layout.activity_main)

        val intent = Intent()
        val pm: PowerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(this.packageName)) {
            //intent.action = Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
            //} else {
            intent.action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
            intent.data = Uri.parse("package:${this.packageName}")
            this.startActivity(intent)
        }

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        tv_websocket = findViewById(R.id.tv_WebSocket)
        tv_apiresponse = findViewById(R.id.tv_apiresponse)
        oldColor = tv_websocket.currentTextColor
        val tv_id = findViewById<TextView>(R.id.tv_id)
        tv_id.text = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        val SDK_INT = Build.VERSION.SDK_INT
        if (SDK_INT >= 26) {
            if (!shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
                tv_websocket.text =
                    "目前定位權限:一律許可"
            } else {
                tv_websocket.text =
                    "請將定位權限調整為一律許可"
            }
        } else {
            tv_websocket.text = "SDK版本過低"
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        @RequiresApi(Build.VERSION_CODES.Q)
        if (requestCode == 100 && grantResults[0] == 0 && grantResults[1] == 0) {
            if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
                ActivityCompat.requestPermissions(
                    this, arrayOf<String>(
                        Manifest.permission.ACCESS_BACKGROUND_LOCATION
                    ), 87
                )
            }
        } else if (requestCode == 87) {
            @RequiresApi(Build.VERSION_CODES.R)
            if (!shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
                tv_websocket.text =
                    "目前定位權限:一律許可"
            } else {
                tv_websocket.text =
                    "請將定位權限調整為一律許可"
            }
        }
    }

    override fun onResume() {
        super.onResume()
        tv_websocket = findViewById(R.id.tv_WebSocket)
        tv_apiresponse = findViewById(R.id.tv_apiresponse)
        val tv_id = findViewById<TextView>(R.id.tv_id)
        tv_id.text = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
    }

    private fun startServices() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this@MainActivity, "定位尚未取得", Toast.LENGTH_LONG).show()
            return
        }
        GlobalScope.launch {
            val dispatcher = this.coroutineContext
            CoroutineScope(dispatcher).launch {
                val serviceIntent = Intent(this@MainActivity, LocationUpdatesService::class.java)
                startService(serviceIntent)
            }

        }

    }

    private fun stopServices() {
        val serviceIntent = Intent(this, LocationUpdatesService::class.java)
        stopService(serviceIntent)
    }

    private fun isMyServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }


    open class LocationUpdatesService : Service() {
        //heartbeat connect
        private final val HEART_BEAT_RATE = 3L * 1000L

        //binder
        private val mBinder: JWebSocketClientBinder = JWebSocketClientBinder();

        class JWebSocketClientBinder : Binder() {
            public fun getService(): LocationUpdatesService {
                return LocationUpdatesService()
            }
        }

        override fun onBind(intent: Intent?): IBinder? = mBinder

        var WebSocketText = ""

        var gpsHandler = Handler()
        var gpsRunnable: Runnable = object : Runnable {
            override fun run() {
                getLocation()
                // Log.e("DATA", "threadgetlocation")
                gpsHandler.postDelayed(this, webSocketInterval.toLong())
            }
        }

        ///web socket
        //val GCF : GsonConverterFactory = GsonConverterFactory.create()
        val uri = URI.create("ws://10.37.36.61:7890/Android")

        lateinit var client: JWebSocketClient

        private val heartbeatHandler: Handler = Handler()
        private val heartBeatRunnable: Runnable = object : Runnable {
            override fun run() {
                if (closed_By_User) {
                    ///closed by user
                    closed_By_User = false
                    Log.e("heartbeat", "closed by user")
                } else {
                    ///not closedby user, needs to reconnect websocket
                    //Log.e("heartbeat", "not closed by user")
                    if (client != null) {
                        if (client.isClosed()) {
                                reconnectWs()
                        }
                    } else {
                            initSocketClient();
                            connectSocket(client)
                    //        Log.e("heartbeat", "not closed by user")
                    }
                    //Log.e("heartbeat", "heartbeat")
                    heartbeatHandler.postDelayed(this, HEART_BEAT_RATE);
                }
            }
        }

        private fun initSocketClient() {
            client = object : JWebSocketClient(uri) {
                override fun startConnectionLostTimer() {
                    super.startConnectionLostTimer()

                }

                override fun onError(ex: Exception) {
                    super.onError(ex)
                    WebSocketText = "WebSocket error:" + ex.message!!
                    Log.e("JWebOnError", ex.message!!)
                }

                override fun onMessage(message: String?) {
                    //message就是接收到的消息
                    if (message != null) {
                        Log.e("JWebMessage", message)
                        try {
                            //cant change view in other thread, save message first

                            val receive_array = message.split(',')
                            if(receive_array.size > 1){
                                val receive_device_id = receive_array[0].split(':')[1]
                                if(receive_device_id == Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)){
                                    //only show same ID message
                                    WebSocketText = "WebSocket " + currentTime() + " : " + message
                                }
                            }
                            else WebSocketText = message
                        } catch (e: Exception) {
                            Log.e("JWebOnMsnerror", e.message!!)
                        }
                    }
                }

                override fun onWebsocketClosing(
                    conn: WebSocket?,
                    code: Int,
                    reason: String?,
                    remote: Boolean
                ) {
                    super.onWebsocketClosing(conn, code, reason, remote)
                    try {
                        WebSocketText = "WebSocket closing " + currentTime() + " : " + reason
                        tv_websocket.text = WebSocketText
                        tv_websocket.setTextColor(Color.RED)
                        Toast.makeText(
                            applicationContext,
                            "WebSocket closing: " + reason,
                            Toast.LENGTH_LONG
                        ).show()
                        if (closed_By_User) {
                            ///closed by user
                            closed_By_User = false
                            Log.e("onWebsocketClose", "closed by user")
                        } else {
                            ///not closedby user, needs to reconnect websocket
                            this.connect()
                            Log.e("onWebsocketClose", "not closed by user")
                        }
                        Log.e("JWebSocketClient", "onClose()")
                        if (reason != null) {
                            Log.e("WebSocketonClose", reason)
                        }
                        //Toast.makeText(applicationContext,"WebSocket closing: " + reason,Toast.LENGTH_LONG).show()


                    } catch (e: Exception) {
                        Log.e("OnSocClose", e.message + reason!!)
                    }
                    Log.e("JWebOnWebClose", reason!!)
                }

                override fun onClose(code: Int, reason: String, remote: Boolean) {
                    super.onClose(code, reason, remote)

                    Log.e("JWebOnClose", reason)
                }
            }
        }


        override fun onCreate() {
            super.onCreate()
            //Log.e("thread", "onCreate")
        }

        override fun onDestroy() {
            super.onDestroy()
            //Log.e("thread", "onDestroy")
            closeSocket(client)
            gpsHandler.removeCallbacks(gpsRunnable)
            heartbeatHandler.removeCallbacks(heartBeatRunnable);
        }

        override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
            val SDK_INT = Build.VERSION.SDK_INT

            @RequiresApi(Build.VERSION_CODES.O)
            if (SDK_INT >= 8) {
                //to keep service alive
                val channelId = "kim.hsl"
                val chan = NotificationChannel(
                    channelId,
                    "ForegroundService",
                    NotificationManager.IMPORTANCE_NONE
                )

                val ss = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                ss.createNotificationChannel(chan)
                val builder = NotificationCompat.Builder(this, channelId)
                val notification = builder.setOngoing(true)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setPriority(10)
                    .setCategory(Notification.CATEGORY_SERVICE)
                    .build()
                startForeground(1, notification)
            }

            //build webSocket connection
            GlobalScope.launch {
                try{
                    initSocketClient()
                    connectSocket(client)
                }
                catch(e : Exception){
                }
            }
            gpsHandler.postDelayed(gpsRunnable, 0)
            heartbeatHandler.postDelayed(heartBeatRunnable, 0);

            //Process.setThreadPriority(Thread.currentThread().id.toInt(),Process.THREAD_PRIORITY_BACKGROUND)
            if (intent == null) {
                return START_NOT_STICKY
            }
            return START_REDELIVER_INTENT
        }

        private fun currentTime(): String? {
            val res =
                (Calendar.getInstance().get(Calendar.HOUR).toString() + ":" + Calendar.getInstance()
                    .get(Calendar.MINUTE).toString() + ":" + Calendar.getInstance()
                    .get(Calendar.SECOND)
                    .toString()
                        )
            return res
        }

        @SuppressLint("HardwareIds")
        private fun getLocation() {
            val SDK_INT = Build.VERSION.SDK_INT
            val jsonObject: JSONObject = JSONObject()
            if (SDK_INT >= 26) {
                jsonObject.put(
                    "DeviceID",
                    Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
                )
            } else {
                jsonObject.put("DeviceID", Settings.Secure.ANDROID_ID + " Android OS below 8")
            }
            //val currentLocationRequest = CurrentLocationRequest.Builder()
            val request = LocationRequest.create()
            request.apply {
                interval = 10000
                fastestInterval = 1000
                priority = Priority.PRIORITY_HIGH_ACCURACY
            }
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                val currpos = LocationServices.getFusedLocationProviderClient(this)
                currpos.requestLocationUpdates(request, object : LocationCallback() {
                    @SuppressLint("HardwareIds")
                    override fun onLocationResult(locationResult: LocationResult) {
                        currpos.removeLocationUpdates(this)
                        if (locationResult != null && locationResult.locations.size > 0) {
                            val index = locationResult.locations.size - 1
                            val latitude = locationResult.locations[index].latitude
                            val longitude = locationResult.locations[index].longitude
                            jsonObject.put("Latitude", latitude)
                            jsonObject.put("Longitude", longitude)

                            val outputstring = jsonObject.toString()
                            try {
                                //send data by websocket
                                Log.e("JSON", outputstring)
                                sendJSON(client, outputstring)
                            } catch (e: Exception) {
                                Log.e("handler", e.message!!)
                            }
                            try {
                                //send data by api
                                val GCF = GsonConverterFactory.create()
                                val retrofit = Retrofit
                                    .Builder()
                                    .addConverterFactory(GCF)
                                    .baseUrl(DEV_WEBSITE_URL)
                                    .build()
                                val appApi = retrofit.create(POCApi::class.java)
                                val JSON = "application/json; charset=utf-8".toMediaTypeOrNull();
                                val call =
                                    appApi.postPOC(RequestBody.create(JSON, jsonObject.toString()))
                                call.enqueue(object : Callback<POCResponse> {
                                    override fun onFailure(
                                        call: Call<POCResponse>?,
                                        t: Throwable?
                                    ) {
                                        tv_apiresponse.setTextColor(Color.RED)
                                        tv_apiresponse.text = "api" + call.toString()
                                        Log.e("POCapi", " call ")
                                    }

                                    override fun onResponse(
                                        call: Call<POCResponse>?,
                                        response: Response<POCResponse>
                                    ) {
                                        Log.e(
                                            "POCapi",
                                            "response " + response.body()
                                        )

                                        try {
                                            if (tv_apiresponse.currentTextColor == Color.RED) tv_apiresponse.setTextColor(
                                                oldColor
                                            )
                                            tv_apiresponse.text =
                                                "api" + currentTime() + " : " + response.body()
                                                    .toString()
                                            tv_websocket.text = WebSocketText
                                        } catch (e: java.lang.Exception) {
                                            Log.e("api", e.message!!)
                                        }

                                    }
                                })
                            } catch (e: Exception) {
                                Log.e("send api", e.message!!)
                            }
                            Log.e("DATA", latitude.toString() + "_" + longitude.toString())
                        }
                    }
                }, Looper.getMainLooper())
            }
        }

        fun connectSocket(cli: JWebSocketClient) {
            //建立websocket连接
            try {
                cli.connectBlocking()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }

        fun closeSocket(cli: JWebSocketClient) {
            try {
                cli.close()
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
            } finally {
                cli.close()
            }
        }

        fun sendJSON(cli: JWebSocketClient, message: String) {
            if (cli.isOpen) {
                cli.send(message);
            }
        }

        private fun reconnectWs() {
            heartbeatHandler.removeCallbacks(heartBeatRunnable);
            Thread() {
                try { //重連
                    client.reconnectBlocking();
                } catch (e: Exception) {
                    e.printStackTrace();
                }
            }.start();
        }
    }

    //雙進程用  目前沒有使用
    open class GuardService : Service() {
        override fun onBind(intent: Intent?): IBinder? = null
        override fun onCreate() {
            super.onCreate()
        }

        override fun onDestroy() {
            super.onDestroy()
        }

        override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
            return super.onStartCommand(intent, flags, startId)
        }
    }

    fun clickgetlocation(view: View) {
        //getLocation()
        if (!isMyServiceRunning(LocationUpdatesService::class.java)) {
            //textclick = true
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                startServices()
            } else {
                ActivityCompat.requestPermissions(
                    this, arrayOf<String>(
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ), 100
                )
            }
        } else {
            Toast.makeText(this@MainActivity, "定位已進行中,請先停止收集", Toast.LENGTH_LONG).show()
        }
    }

    fun clickstop(view: View) {
        closed_By_User = true
        stopServices()
    }

    fun clickreconnect(view: View) {}

    fun clickclear(view: View) {}

}


open class JWebSocketClient(serverUri: URI?) :
    WebSocketClient(serverUri, Draft_6455()) {
    override fun onOpen(handshakedata: ServerHandshake) {

    }

    override fun onMessage(message: String?) {
    }


    override fun onClose(code: Int, reason: String, remote: Boolean) {
    }

    override fun onError(ex: Exception) {
    }

}