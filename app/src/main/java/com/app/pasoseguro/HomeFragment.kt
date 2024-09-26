package com.app.pasoseguro

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.app.pasoseguro.databinding.FragmentHomeBinding
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    //variables de bluetooth
    private val bluetoothPermissionsLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions.all { it.value }) {
            Log.d("homeFragment", "Bluetooth permissions granted")
            //searchAndDisplayBluetoothDevices()
        } else {
            Log.d("homeFragment", "Bluetooth permissions denied")
            Snackbar.make(requireView(), "Bluetooth permissions denied", Snackbar.LENGTH_SHORT).show()
        }
    }
    private val enableBluetoothLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            //searchAndDisplayBluetoothDevices()
        } else {
            Snackbar.make(requireView(), "Bluetooth not enabled", Snackbar.LENGTH_SHORT).show()
        }
    }
    private var bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var bluetoothSocket: BluetoothSocket? = null
    private lateinit var bluetoothManager: BluetoothManager
    private var isConnected: Boolean = false
    private lateinit var devicesBluetooth: MutableList<BluetoothDevice>//lista mutable de objetos tipo bt_device
    private lateinit var bluetoothDevice: BluetoothDevice
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        _binding = FragmentHomeBinding.inflate(inflater,container,false)

        //SOLICITAR PERMISOS PARA UTILIZAR BLUETOOTH--------------------------------------
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {//Revisa version de android y solicita permisos de forma acorde
            Log.d("homeFragment", "Requesting permissions for android 12+")
            requestBluetoothPermissions()// Solicitar permisos de Bluetooth para Android 12+
        } else {
            Log.d("homeFragment", "Requesting permissions for android 11-")
            requestLegacyBluetoothPermissions()// Solicitar permisos de Bluetooth para Android 11 y anteriores
        }



        return binding.root
    }

    private fun requestLegacyBluetoothPermissions() {//Funcion para solicita permisos bt a android 11 e inferiores
        val requiredPermissions = arrayOf(
            android.Manifest.permission.BLUETOOTH,
            android.Manifest.permission.BLUETOOTH_ADMIN
        )
        Log.d("homeFragment", "Checking permissions")
        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED
        }
        if (missingPermissions.isNotEmpty()) {
            bluetoothPermissionsLauncher.launch(missingPermissions.toTypedArray())
            Log.d("homeFragment", "Missing Permission. Launching")
        } else {
            Log.d("homeFragment", "All Bluetooth permissions already granted")
            //searchAndDisplayBluetoothDevices()
        }
    }
    private fun requestBluetoothPermissions() {//Funcion para solicita permisos bt a android 12 y superiores
        val requiredPermissions = arrayOf(
            android.Manifest.permission.BLUETOOTH_CONNECT,
            android.Manifest.permission.BLUETOOTH_SCAN
        )
        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED
        }
        if (missingPermissions.isNotEmpty()) {
            Log.d("homeFragment", "Launching Bluetooth permissions launcher")
            bluetoothPermissionsLauncher.launch(missingPermissions.toTypedArray())
        } else {
            Log.d("homeFragment", "All Bluetooth permissions already granted")
            //searchAndDisplayBluetoothDevices()
        }
    }

}