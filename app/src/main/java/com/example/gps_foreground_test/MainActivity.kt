package com.example.gps_foreground_test


import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.launch


var closed_By_User: Boolean = false

class MainActivity : AppCompatActivity(), View.OnClickListener {
    var apitext = "testtest123"
    var wstext = "testtest123"
    lateinit var receiver: MainReceiver
    var oldColor: Int = Color.BLACK
    var uiHandler: Handler = Handler()
    var uiRunnable: Runnable = object : Runnable {
        override fun run() {
            ///update UI
            if (apitext != receiver.apitext
                || wstext != receiver.wstext
            ) {
                apitext = receiver.apitext
                wstext = receiver.wstext
                runOnUiThread {
                    val tv_apiresponse = findViewById<TextView>(R.id.tv_apiresponse)
                    val tv_websocket = findViewById<TextView>(R.id.tv_WebSocket)
                    tv_apiresponse.text = apitext
                    tv_websocket.text = wstext
                }
            }
            uiHandler.postDelayed(this, 1000L)
        }
    }
    lateinit var locationManager: LocationManager

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        setContentView(R.layout.activity_main)
        startReceiver()
        check_permission()
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
        val tv_websocket = findViewById<TextView>(R.id.tv_WebSocket)
        oldColor = tv_websocket.currentTextColor
        val tv_id = findViewById<TextView>(R.id.tv_id)
        tv_id.text = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        val SDK_INT = Build.VERSION.SDK_INT

        if (SDK_INT >= 26) {
            if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
                wstext =
                    "目前定位權限:一律許可"
            } else {
                wstext =
                    "請將定位權限調整為一律許可"
            }
        } else {
            ///tv_websocket.text = "SDK版本過低"
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

            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
                wstext =
                    "目前定位權限:一律許可"
            } else {
                wstext =
                    "請將定位權限調整為一律許可"
            }
        }
    }

    override fun onResume() {
        super.onResume()
        //tv_websocket = findViewById(R.id.tv_WebSocket)
        //tv_apiresponse = findViewById(R.id.tv_apiresponse)
        val tv_id = findViewById<TextView>(R.id.tv_id)
        tv_id.text = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
    }

    private fun startReceiver() {
        receiver = MainReceiver()

        val filter = IntentFilter()
        filter.addAction("com.example.gps_foreground_test.LocationUpdateService")
        this@MainActivity.registerReceiver(receiver, filter)
        uiHandler.postDelayed(uiRunnable, 1000)
    }

    @SuppressLint("BatteryLife")
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun check_permission() {
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
                val serviceIntent = Intent(this@MainActivity, LocationUpdateService::class.java)
                startService(serviceIntent)
            }
        }
    }


    private fun stopServices() {
        val serviceIntent = Intent(this, LocationUpdateService::class.java)
        stopService(serviceIntent)
        @RequiresApi(Build.VERSION_CODES.Q)
        if(uiHandler.hasCallbacks(uiRunnable))uiHandler.removeCallbacks(uiRunnable)
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


    fun clickgetlocation(view: View) {
        //getLocation()
        if (!isMyServiceRunning(LocationUpdateService::class.java)) {
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

    fun clickreconnect(view: View) {

    }

    fun clickclear(view: View) {

    }


    override fun onClick(v: View?) {
        when (v?.id) {

        }
    }
}

open class MainReceiver : BroadcastReceiver() {
    var wstext = "testtest123"
    var apitext = "testtest123"

    override fun onReceive(context: Context, intent: Intent) {
        val bundle = intent.extras
        apitext = bundle!!.getString("apiText")!!
        wstext = bundle.getString("wsText")!!
        Log.e("receiver api", apitext)
        Log.e("receiver ws", wstext)

    }
}
