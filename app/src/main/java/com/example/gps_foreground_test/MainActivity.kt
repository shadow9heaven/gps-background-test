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

class MainActivity : AppCompatActivity(), View.OnClickListener {

    var latitude: Double = 0.0
    var longitude: Double = 0.0
    lateinit var locationManager: LocationManager

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        check_permission()

        setContentView(R.layout.activity_main)
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
                    "??????????????????:????????????"
            } else {
                tv_websocket.text =
                    "???????????????????????????????????????"
            }
        } else {
            tv_websocket.text = "SDK????????????"
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
                    "??????????????????:????????????"
            } else {
                tv_websocket.text =
                    "???????????????????????????????????????"
            }
        }
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.bt_connect -> {
                clickgetlocation()
            }
            R.id.bt_close -> {
                clickstop()
            }
            R.id.bt_reconnet -> {
                clickreconnect()
            }
            R.id.bt_clear -> {
                clickclear()
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
        val intent = Intent()
        val pm: PowerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(this.packageName)) {
            //intent.action = Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
            //} else {
            intent.action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
            intent.data = Uri.parse("package:${this.packageName}")
            this.startActivity(intent)
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
            Toast.makeText(this@MainActivity, "??????????????????", Toast.LENGTH_LONG).show()
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

    fun clickgetlocation() {
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
            Toast.makeText(this@MainActivity, "??????????????????,??????????????????", Toast.LENGTH_LONG).show()
        }
    }

    fun clickstop() {
        closed_By_User = true
        stopServices()
    }

    fun clickreconnect() {

    }

    fun clickclear() {

    }


}