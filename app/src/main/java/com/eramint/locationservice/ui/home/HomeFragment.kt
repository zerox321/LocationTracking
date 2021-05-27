package com.eramint.locationservice.ui.home

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.eramint.locationservice.R
import com.eramint.locationservice.base.BaseFragment
import com.eramint.locationservice.data.DriverModel
import com.eramint.locationservice.databinding.FragmentHomeBinding
import com.eramint.locationservice.util.LatLngInterpolator
import com.eramint.locationservice.util.LocationModel
import com.eramint.locationservice.util.MapUtili.addCustomMarker
import com.eramint.locationservice.util.MapUtili.animate
import com.eramint.locationservice.util.MapUtili.getBitmapFromVectorDrawable
import com.eramint.locationservice.util.MapUtili.moveMapCamera
import com.eramint.locationservice.util.MapUtili.setMapStyle
import com.eramint.locationservice.util.toGSON
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.maps.android.ktx.awaitMap
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class HomeFragment : BaseFragment() {

    private val viewModel by viewModels<HomeViewModel>()
    private val driversMap: HashMap<Int, DriverModel> = HashMap()

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val mapFragment: SupportMapFragment? by lazy {
        childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment?
    }
    private val latLngInterpolator: LatLngInterpolator by lazy {
        LatLngInterpolator.Spherical()
    }
    private val userBitMap by lazy {
        context?.getBitmapFromVectorDrawable(R.drawable.ic_current_location)
    }
    private val driverBitMap by lazy {
        context?.getBitmapFromVectorDrawable(R.drawable.ic_taxi)
    }
    private var isFirst: Boolean = true
    private var userMarker: Marker? = null

    private var mMap: GoogleMap? = null
    private suspend fun getMap(): GoogleMap? {
        if (mMap == null) {
            isFirst = true
            mMap = mapFragment?.awaitMap()
            mMap?.let { map -> context?.setMapStyle(map) }
        }
        return mMap
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            viewModel.locationFlow
                .map { string -> string?.toGSON() }
                .collect { locationModel: LocationModel? ->
                    onNewLocation(locationModel = locationModel ?: return@collect)
                }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        _binding?.removeDriver?.setOnClickListener {
            removeDriver(driverID = 1)
        }
        _binding?.addDriver?.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val map = getMap() ?: return@launch
                map.addDriver(
                    driverID = 1,
                    driver = LocationModel(
                        fromLat = 30.786508 + 0.001,
                        fromLon = 31.000375,
                        toLat = 30.786508 + 0.001,
                        toLon = 31.000375 + 0.001
                    )
                )
            }
        }
        _binding?.updateDriver?.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val map = getMap() ?: return@launch
                val driverID = 1
                map.updateDriver(
                    driverID = driverID,
                    locationModel = LocationModel(
                        fromLat = 30.786508 - 0.001,
                        fromLon = 31.000375,
                        toLat = 30.786508 + 0.001,
                        toLon = 31.000375 - 0.001
                    )
                )

            }
        }
        _binding?.currentLocation?.setOnClickListener {
//            viewLifecycleOwner.lifecycleScope.launch {
//                Log.e(TAG, "currentLocation: " )
//                val model =
//                    viewModel.locationFlow.singleOrNull()?.toGSON() ?: return@launch
//                Log.e(TAG, "currentLocation: $model" )
//
//                val map = getMap() ?: return@launch
//                Log.e(TAG, "currentLocation: $map" )
//
//                map.moveMapCamera(userLocationLatLng = model.getToLatLng())
//
//            }
        }
        return binding.root
    }


    private suspend fun onNewLocation(locationModel: LocationModel) {
        Log.e(TAG, "onNewLocation:  $locationModel")
        val oldPosition = locationModel.getFromLatLng()

        val newPosition = locationModel.getToLatLng()

        val map = getMap() ?: return

        // Todo move Map Camera
        map.setupMapCameraView(position = oldPosition)
        // Todo add user location marker
        if (userMarker == null)
            userMarker = map.addCustomMarker(
                name = viewModel.driverName,
                oldPosition = oldPosition,
                newPosition = newPosition,
                icon = userBitMap ?: return
            )
        // Todo animate user location marker
        else userMarker?.animate(
            newPosition = newPosition,
            latLngInterpolator = latLngInterpolator
        )

    }

    private fun GoogleMap.updateDriver(
        driverID: Int,
        locationModel: LocationModel
    ) {
        val driverModel = driversMap[driverID]
        if (driverModel == null)
            addDriver(
                driverID = driverID,
                driver = LocationModel(
                    fromLat = 30.786508 + 0.001,
                    fromLon = 31.000375,
                    toLat = 30.786508 + 0.001,
                    toLon = 31.000375 + 0.001
                )
            )
        else {
            driverModel.driverMarker?.animate(
                newPosition = locationModel.getToLatLng(),
                latLngInterpolator = latLngInterpolator
            )
            driversMap[driverID]?.driver = locationModel
        }
    }


    private fun GoogleMap.addDriver(driverID: Int, driver: LocationModel) {
        // check if driver is Draw Before
        if (driversMap[driverID] != null) removeDriver(driverID = driverID)
        val driverMarker = addCustomMarker(
            name = viewModel.driverName,
            oldPosition = driver.getFromLatLng(),
            newPosition = driver.getToLatLng(),
            icon = driverBitMap ?: return
        )
        driversMap[driverID] =
            DriverModel(driverID = driverID, driver = driver, driverMarker = driverMarker)
    }

    private fun removeDriver(driverID: Int) {
        Log.e(TAG, "removeDriver: driverID $driverID")
        val driverModel: DriverModel = driversMap[driverID] ?: return
        driverModel.driverMarker?.remove()
        driversMap.remove(driverID)
        Log.e(TAG, "removeDriver: driverID $driverID removed Successfully")
    }


    private fun GoogleMap.setupMapCameraView(position: LatLng) {
        if (isFirst)
            moveMapCamera(position = position)
        isFirst = false
    }


    override fun onResume() {
        super.onResume()
        mapFragment?.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapFragment?.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapFragment?.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapFragment?.onLowMemory()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}