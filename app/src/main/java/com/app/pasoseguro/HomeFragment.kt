package com.app.pasoseguro

import android.R
import android.app.Activity
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
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar
import java.io.IOException
import java.util.UUID

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
            searchAndDisplayBluetoothDevice()
        } else {
            Snackbar.make(requireView(), "Bluetooth not enabled", Snackbar.LENGTH_SHORT).show()
        }
    }
    private var bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var bluetoothSocket: BluetoothSocket? = null
    private lateinit var bluetoothManager: BluetoothManager
    private var isConnected: Boolean = false
    val targetDeviceName = "HC-05"
    val targetMacAddress = "00:14:03:19:24:58"
    private lateinit var deviceBluetooth: MutableList<BluetoothDevice>//lista mutable de objetos tipo bt_device
    private lateinit var bluetoothDevice: BluetoothDevice
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflar la interfaz para este fragmento
        _binding = FragmentHomeBinding.inflate(inflater,container,false)

        //SOLICITAR PERMISOS PARA UTILIZAR BLUETOOTH--------------------------------------
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {//Revisa version de android y solicita permisos de forma acorde
            Log.d("HomeFragment", "Requesting permissions for android 12+")
            requestBluetoothPermissions()// Solicitar permisos de Bluetooth para Android 12+
        } else {
            Log.d("HomeFragment", "Requesting permissions for android 11-")
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
    @RequiresApi(Build.VERSION_CODES.S)
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
    private fun searchAndDisplayBluetoothDevice() {//Funcion para buscar y mostrar dispositivos BT
        // Verificar si el Bluetooth está habilitado
        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {// Verificar si el Bluetooth está habilitado
            enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))// Solicitar al usuario que habilite el Bluetooth
        } else {
            val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter?.bondedDevices// Obtener la lista de dispositivos Bluetooth emparejados
            pairedDevices?.forEach { device ->
                if (device.name == targetDeviceName) {
                    // Intentar conectarse automáticamente al dispositivo encontrado
                    connectToDevice(device)
                }
            }
            val device: BluetoothDevice = bluetoothAdapter!!.getRemoteDevice(targetMacAddress)
            connectToDevice(device)

        }
    }
    private fun connectToDevice(device: BluetoothDevice) {
        Thread {
            try {
                val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
                Log.d("HomeFragment", "Creating a socket")
                val socket = device.createRfcommSocketToServiceRecord(uuid)
                bluetoothSocket = socket
                bluetoothAdapter?.cancelDiscovery()
                Log.d("HomeFragment", "Trying to connect")
                socket.connect()

                requireActivity().runOnUiThread {
                    Log.d("HomeFragment", "Connected to device")
                    Snackbar.make(requireView(), "Se conectó al dispositivo correctamente", Snackbar.LENGTH_SHORT).show()
                    //startDataReceiving()
                }
            } catch (e: IOException) {
                Log.e("HomeFragment", "Error connecting to device: ${e.message}")
                requireActivity().runOnUiThread {
                    Snackbar.make(requireView(), "Error al conectar al dispositivo", Snackbar.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("HomeFragment", "General error: ${e.message}")
                requireActivity().runOnUiThread {
                    Snackbar.make(requireView(), "Error desconocido", Snackbar.LENGTH_SHORT).show()
                }
            }
        }.start()
    }
}