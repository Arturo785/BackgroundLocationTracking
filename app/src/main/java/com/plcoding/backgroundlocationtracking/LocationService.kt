package com.plcoding.backgroundlocationtracking

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class LocationService : Service() {

    // we create our own scope for managing the service calls
    // SupervisorJob is similar to a regularJob,
    // the only difference being that the cancellation is propagated only downwards.
    // Meaning that child coroutines that throw exceptions, won’t cancel their parent.

    // basically if one child job gets cancelled the other ones keep without issues
    // and we do our work on the IO dispatcher
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // our abstraction we did
    private lateinit var locationClient: LocationClient

    //These are used to be keys for the outside to manage the state of the service
    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
    }

    // we don't bind our service to anything in this case
    override fun onBind(p0: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        // here we can replace it with dependency injection
        locationClient = DefaultLocationClient(
            applicationContext,
            LocationServices.getFusedLocationProviderClient(applicationContext)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Here we check every intent we send to the service
        // We catch our own actions defined in the companion object
        when (intent?.action) {
            ACTION_START -> start()
            ACTION_STOP -> stop()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun start() {
        // in order to manage the foreground notification
        val notification = NotificationCompat.Builder(this, "location") // context and channel id
            .setContentTitle("Tracking location...")
            .setContentText("Location: null")
            .setSmallIcon(R.drawable.ic_launcher_background)
            .setOngoing(true) // can not be dismissed

        // we get the manager from our system
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // we call our abstraction to start observing the location
        // and on each emittion we update our notification with the new data
        locationClient
            .getLocationUpdates(10000L) // in here we pass the interval refresh
            .catch { e -> e.printStackTrace() } // in case exceptions happens, otherwise the scope gets cancelled
            .onEach { location ->
                val lat = location.latitude.toString()
                val long = location.longitude.toString()
                // we use the same constructor as above
                val updatedNotification = notification.setContentText(
                    "Location: ($lat, $long)"
                )
                // we use the same id in order to update the notification and not create another
                notificationManager.notify(1, updatedNotification.build())
            }
            .launchIn(serviceScope) // important to use the scope we did
        // in this way allows child cancellations and when on destroy called it does not still
        // listening for updates or leaking

        // this comes from the service parent class
        startForeground(1, notification.build())
    }

    private fun stop() {
        stopForeground(true) // use the function from the parent class
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        // we cancel our scope when the service is destroyed
        serviceScope.cancel()
    }

}