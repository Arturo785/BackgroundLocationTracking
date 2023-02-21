package com.plcoding.backgroundlocationtracking

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

class DefaultLocationClient(
    private val context: Context,
    private val client: FusedLocationProviderClient // this comes from a dependency
): LocationClient {

    // does not recognize that our extension function checks for the permission
    @SuppressLint("MissingPermission")
    override fun getLocationUpdates(interval: Long): Flow<Location> {
        // we use callback flow when we deal with callbacks and want to transform it into flows
        // also if we want to model callbacks that have lifecycle like this one that deals with location
        return callbackFlow {

            // the extension fun we did
            if(!context.hasLocationPermission()) {
                // the exception we did
                throw LocationClient.LocationException("Missing location permission")
            }
            // we have the permission, and get the location manager from android system
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            // this ones get extracted from our manager extracted from the system
            // with this ones we make sure that the user has the gps and the network active
            val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)


            if(!isGpsEnabled && !isNetworkEnabled) {
                // the exception we did
                throw LocationClient.LocationException("GPS is disabled")
            }

            // defines how often we want our request to be fired
            val request = LocationRequest.create()
                .setInterval(interval)
                .setFastestInterval(interval)

            // we create our callback to trigger what happens when retrieved a new location
            // this callback does not do anything until used below
            val locationCallback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    super.onLocationResult(result)
                    // we "emit" the new retrieved location
                    // the last one is the most recent location
                    result.locations.lastOrNull()?.let { location ->
                        // we are able to launch a coroutine because of the callbackFlow
                        launch { send(location) }
                    }
                }
            }

            // we use the client from the constructor and send
            client.requestLocationUpdates(
                request, // how often request
                locationCallback, // what to do when requesting
                Looper.getMainLooper()
            )

            // what happens when our stream stops, or when we stop collecting
            awaitClose {
                client.removeLocationUpdates(locationCallback) // removes our callback
            }
        }
    }
}