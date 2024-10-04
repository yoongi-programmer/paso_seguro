package com.app.pasoseguro

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
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar
import java.io.IOException
import java.util.UUID
import android.media.MediaPlayer
import android.net.Uri
import android.media.AudioManager
import android.media.SoundPool

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    //variables de bluetooth
    private val bluetoothPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions.all { it.value }) {
                Log.d("HomeFragment", "Bluetooth permissions granted")
                searchAndDisplayBluetoothDevice()
            } else {
                Log.d("HomeFragment", "Bluetooth permissions denied")
                Snackbar.make(requireView(), "Bluetooth permissions denied", Snackbar.LENGTH_SHORT)
                    .show()
            }
        }
    private val enableBluetoothLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
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
    val targetDeviceName = "XM-15"
    val targetMacAddress = "00:11:35:96:97:45"
    private lateinit var deviceBluetooth: MutableList<BluetoothDevice>//lista mutable de objetos tipo bt_device
    private lateinit var bluetoothDevice: BluetoothDevice
    var estadoSemaforo = 0
    private var semaforoActivo = true
    var estadoRojo = 0
    var estadoAmarillo = 0
    var estadoVerde = 0
    private var mediaPlayer : MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflar la interfaz para este fragmento
        _binding = FragmentHomeBinding.inflate(inflater, container, false)

        //SOLICITAR PERMISOS PARA UTILIZAR BLUETOOTH--------------------------------------
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {//Revisa version de android y solicita permisos de forma acorde
            Log.d("HomeFragment", "Requesting permissions for android 12+")
            requestBluetoothPermissions()// Solicitar permisos de Bluetooth para Android 12+
        } else {
            Log.d("HomeFragment", "Requesting permissions for android 11-")
            requestLegacyBluetoothPermissions()// Solicitar permisos de Bluetooth para Android 11 y anteriores
        }
        binding.btEscuchar.setOnClickListener{
            iniciarSemaforo()
        }
        playAudio(R.raw.bienvenido)

        Log.d("HomeFragment", "isConnected: $isConnected")
        if (isConnected){
            updateUI()
        }else{
            updateUIWithDeafultValues()
        }

        return binding.root


    }

    private fun requestLegacyBluetoothPermissions() {//Funcion para solicita permisos bt a android 11 e inferiores
        val requiredPermissions = arrayOf(
            android.Manifest.permission.BLUETOOTH,
            android.Manifest.permission.BLUETOOTH_ADMIN
        )
        Log.d("HomeFragment", "Checking permissions")
        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(
                requireContext(),
                it
            ) != PackageManager.PERMISSION_GRANTED
        }
        if (missingPermissions.isNotEmpty()) {
            bluetoothPermissionsLauncher.launch(missingPermissions.toTypedArray())
            Log.d("HomeFragment", "Missing Permission. Launching")
        } else {
            Log.d("HomeFragment", "All Bluetooth permissions already granted")
            searchAndDisplayBluetoothDevice()
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun requestBluetoothPermissions() {//Funcion para solicita permisos bt a android 12 y superiores
        val requiredPermissions = arrayOf(
            android.Manifest.permission.BLUETOOTH_CONNECT,
            android.Manifest.permission.BLUETOOTH_SCAN
        )
        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(
                requireContext(),
                it
            ) != PackageManager.PERMISSION_GRANTED
        }
        if (missingPermissions.isNotEmpty()) {
            Log.d("HomeFragment", "Launching Bluetooth permissions launcher")
            bluetoothPermissionsLauncher.launch(missingPermissions.toTypedArray())
        } else {
            Log.d("HomeFragment", "All Bluetooth permissions already granted")
            searchAndDisplayBluetoothDevice()
        }
    }

    private fun searchAndDisplayBluetoothDevice() {//Funcion para buscar y mostrar dispositivos BT
        // Verificar si el Bluetooth está habilitado
        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {// Verificar si el Bluetooth está habilitado
            enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))// Solicitar al usuario que habilite el Bluetooth
        } else {
            val pairedDevices: Set<BluetoothDevice>? =
                bluetoothAdapter?.bondedDevices// Obtener la lista de dispositivos Bluetooth emparejados
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
            while (!isConnected) {
                try {
                    val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
                    Log.d("HomeFragment", "Creando un socket")
                    val socket = device.createRfcommSocketToServiceRecord(uuid)
                    bluetoothSocket = socket
                    bluetoothAdapter?.cancelDiscovery()
                    Log.d("HomeFragment", "Intentando conectar")

                    // Mostrar mensaje de conexión en progreso solo una vez
                    requireActivity().runOnUiThread {
                        binding.txtConectado.text = "Buscando dispositivo..."
                    }

                    socket.connect() // Intentar la conexión

                    // Si la conexión es exitosa
                    requireActivity().runOnUiThread {
                        Log.d("HomeFragment", "Conectado al dispositivo")
                        Snackbar.make(requireView(), "Se conectó al dispositivo correctamente", Snackbar.LENGTH_SHORT).show()
                        binding.txtConectado.text = "Conectado"
                        isConnected = true
                        startDataReceiving() // Comenzar a recibir datos
                    }

                } catch (e: IOException) {
                    Log.e("HomeFragment", "Error al conectar: ${e.message}")
                    requireActivity().runOnUiThread {
                        // No mostrar el error si no se encuentra el dispositivo, solo continuar intentando
                        binding.txtConectado.text = "Buscando dispositivo..."
                    }
                    Thread.sleep(2000) // Esperar 2 segundos antes de reintentar

                } catch (e: Exception) {
                    isConnected = false
                    Log.e("HomeFragment", "Error general: ${e.message}")
                    requireActivity().runOnUiThread {
                        // Error inesperado, solo notificar si es necesario
                        Snackbar.make(requireView(), "Error desconocido", Snackbar.LENGTH_SHORT).show()
                    }
                    Thread.sleep(2000) // Esperar 2 segundos antes de reintentar
                }
            }
        }.start()
    }


    private fun startDataReceiving() {
        val socket = bluetoothSocket
        if (socket != null) {
            val inputStream = socket.inputStream
            val buffer = ByteArray(1024)
            val stringBuilder = StringBuilder()
            var bytes: Int

            Thread {
                while (true) {
                    try {
                        bytes = inputStream.read(buffer)
                        val data = String(buffer, 0, bytes)
                        stringBuilder.append(data)

                        val lines = stringBuilder.split("\n")
                        for (i in 0 until lines.size - 1) {
                            val line = lines[i].trim()
                            if (line.isNotEmpty()) {
                                Log.d("HomeFragment", "Received data: $line")
                                parseData(line)
                            }
                        }
                        stringBuilder.clear()
                        stringBuilder.append(lines.last())
                    } catch (e: IOException) {
                        Log.e("HomeFragment", "Error reading data: ${e.message}")
                        break
                    }
                }
            }.start()
        } else {
            Log.e("HomeFragment", "BluetoothSocket is null")
            Snackbar.make(requireView(), "Error al conectar al dispositivo", Snackbar.LENGTH_SHORT)
                .show()
        }
    }

    private fun parseData(data: String) {
        Log.d("HomeFragment", "Parsing data: $data")
        val parts = data.split("|")
        if (parts.size == 1) {
            try {
                // Parsear todos los datos a el formato correspondiente
                estadoVerde = parts[0].toInt()
                estadoAmarillo = parts[1].toInt()
            } catch (e: NumberFormatException) {
                Log.e("homeFragment", "Error parsing data: ${e.message}")
            }
        } else {
            Log.e("homeFragment", "Data format error: expected 2 parts, got ${parts.size}")
        }

    }


    private fun updateUI() {
        Log.d("HomeFragment", "Conectado")
        binding.txtConectado.text = "Conectado"
        binding.imgHablando.setImageResource(R.drawable.hablar_on)
        binding.estadoSemaforo.visibility = View.VISIBLE
        if (estadoSemaforo == 1) {
            binding.estadoSemaforo.text = "Estado del semaforo: Rojo"

        }
        else if (estadoSemaforo == 1) {
            binding.estadoSemaforo.text = "Estado del semaforo: Amarillo"
        }
        else if (estadoSemaforo == 1) {
            binding.estadoSemaforo.text = "Estado del semaforo: Verde"
        }


    }
    private fun cambiarColor(view: View, color: Int) {
        val background = view.background.mutate() // Asegúrate de no modificar el drawable globalmente
        background.setTint(ContextCompat.getColor(view.context, color)) // Cambia el colorthis, color)) // Cambia el color
    }
    private fun iniciarSemaforo() {
        if (semaforoActivo) {
            // Luz roja
            cambiarColor(binding.luzRoja, R.color.rojo_on)
            handler.postDelayed({
                cambiarColor(binding.luzRoja, R.color.rojo_off)
                // Luz amarilla
                cambiarColor(binding.luzAmarilla, R.color.amarillo_on)
                handler.postDelayed({
                    cambiarColor(binding.luzAmarilla, R.color.amarillo_off)
                    // Luz verde
                    cambiarColor(binding.luzVerde, R.color.verde_on)
                    handler.postDelayed({
                        cambiarColor(binding.luzVerde, R.color.verde_off)
                        iniciarSemaforo() // Reiniciar ciclo
                    }, 5000) // 3 segundos para verde
                }, 3000) // 1 segundo para amarillo
            }, 5000) // 5 segundos para rojo
        }
    }
    private fun updateUIWithDeafultValues(){
        binding.estadoSemaforo.visibility = View.INVISIBLE
        Log.d("HomeFragment", "Desconectado")
        binding.txtConectado.text = "Desconectado"

    }

    private fun playAudio(audioResID: Int) {
        mediaPlayer = MediaPlayer.create(requireContext(), audioResID)
        if (mediaPlayer == null) {
            Log.e("MediaPlayer", "Error al crear el MediaPlayer")
        } else {
            mediaPlayer?.start()
        }
    }

        override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        if (bluetoothSocket != null) {
            try {
                bluetoothSocket?.let{
                    Log.d("HomeFragment", "Closing Bluetooth socket")
                    it.close()
                }
            } catch (e: IOException) {
                Log.e("HomeFragment", "Error closing Bluetooth socket", e)
            }
        }
        mediaPlayer?.release() //liberar recursos
    }
}