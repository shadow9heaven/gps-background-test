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


//var temp = "test"

@SuppressLint("StaticFieldLeak")
lateinit var tv_websocket: TextView

@SuppressLint("StaticFieldLeak")
lateinit var tv_apiresponse: TextView
var oldColor: Int = Color.BLACK

const val DEV_WEBSITE_URL = "https://portalapp-dev.wistron.com"
val WEB_SOCKET_ADDRESS = "ws://10.37.36.61:7890/Android"
var closed_By_User = false

class MainActivity : AppCompatActivity() {

    var latitude: Double = 0.0
    var longitude: Double = 0.0
    lateinit var locationManager: LocationManager

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        setContentView(R.layout.activity_main)

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


    private fun check_permission(){
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

    fun clickreconnect(view: View) {}

    fun clickclear(view: View) {}

}