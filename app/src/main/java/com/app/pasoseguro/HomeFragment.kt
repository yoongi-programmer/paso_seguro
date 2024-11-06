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
import android.widget.AdapterView
import android.widget.ArrayAdapter

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
    val targetDeviceName = "HC-05"
    val targetMacAddress = "00:11:35:96:97:45"
    private lateinit var devicesBluetooth: MutableList<BluetoothDevice>//lista mutable de objetos tipo bt_device
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
        //SELECCION DE DISPOSITIVO BT AL QUE CONECTARSE------------------------------------
        binding.spinnerBT.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                if (devicesBluetooth.isNotEmpty()) {
                    Log.d("homeFragment", "Selected device: ${devicesBluetooth[position].name}")
                    val device = devicesBluetooth[position]
                    connectToDevice(device)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>) {
                Snackbar.make(requireView(), "Seleccione un dispositivo", Snackbar.LENGTH_SHORT).show()
            }
        }
        binding.btEscuchar.setOnClickListener{
            semaforoActivo = true
            iniciarSemaforo()
        }
        binding.btnPararSemaforo.setOnClickListener {
            detenerSemaforo()
        }
        playAudio(R.raw.buscando)
        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed({
            // Código que quieres ejecutar después del delay
            playAudio(R.raw.conexion)
            isConnected = true
            handler.postDelayed({
                // Código que quieres ejecutar después del delay
                playAudio(R.raw.calle)
                handler.postDelayed({
                    // Código que quieres ejecutar después del delay
                    playAudio(R.raw.cruce_dificil)

                }, 4000) // 4000 milisegundos = 4 segundos
            }, 4000)
        }, 4000)
        Log.d("HomeFragment", "isConnected: $isConnected")
        updateUI()
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
        Log.d("HomeFragment", "Searching for Bluetooth device")
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
            if (!pairedDevices.isNullOrEmpty()) {
                devicesBluetooth = pairedDevices.toMutableList()

                // Convertir la lista de dispositivos en una lista de nombres para mostrar, evitando nombres nulos
                val deviceNames = devicesBluetooth.mapNotNull { it.name ?: "Dispositivo desconocido" }

                // Si la lista tiene elementos, asigna el ArrayAdapter
                if (deviceNames.isNotEmpty()) {
                    val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_expandable_list_item_1, deviceNames)
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    binding.spinnerBT.adapter = adapter
                } else {
                    // Mostrar mensaje si no hay nombres válidos
                    Snackbar.make(requireView(), "No hay dispositivos con nombres válidos", Snackbar.LENGTH_SHORT).show()
                }
            } else {
                // Mostrar mensaje si no hay dispositivos emparejados
                Snackbar.make(requireView(), "No hay dispositivos Bluetooth emparejados", Snackbar.LENGTH_SHORT).show()
            }
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
                    Log.i("HomeFragment", "No se encontraron dispositivos: ${e.message}")
                    requireActivity().runOnUiThread {
                        // No mostrar el error si no se encuentra el dispositivo, solo continuar intentando
                        //binding.txtConectado.text = "Buscando dispositivo..."
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
        Log.d("HomeFragment", "Recibiendo datos:")
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
                                updateUI()
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
        Log.d("HomeFragment","Tamaño de partes: ${parts.size}")
        if (parts.size == 3) {
            try {
                // Parsear todos los datos a el formato correspondiente
                estadoVerde = parts[0].toInt()
                estadoAmarillo = parts[1].toInt()
                estadoRojo = parts[2].toInt()
            } catch (e: NumberFormatException) {
                Log.e("homeFragment", "Error parsing data: ${e.message}")
            }
        } else {
            Log.e("homeFragment", "Data format error: expected 3 parts, got ${parts.size}")
        }

    }

    private fun updateUI() {
        Log.d("HomeFragment", "Actualizando UI")
        binding.txtConectado.text = "Conectado"
        Log.d("HomeFragment","estado verde: $estadoVerde")
        Log.d("HomeFragment","estado amarillo: $estadoAmarillo")
        Log.d("HomeFragment","estado rojo: $estadoRojo")

//        if (estadoVerde == 1) {
//            playAudio(R.raw.semaforo_verde)
//        }
//        else if (estadoAmarillo == 1) {
//            playAudio(R.raw.semaforo_amarillo)
//        }
//        else if (estadoRojo == 1) {
//            playAudio(R.raw.semaforo_rojo)
//        }


    }
    private fun cambiarColor(view: View, color: Int) {
        val background = view.background.mutate() // Asegúrate de no modificar el drawable globalmente
        background.setTint(ContextCompat.getColor(view.context, color)) // Cambia el colorthis, color)) // Cambia el color
    }
    private fun iniciarSemaforo() {
        if (semaforoActivo) {
            // Luz roja
            cambiarColor(binding.luzRoja, R.color.rojo_on)
            playAudio(R.raw.semaforo_rojo)
            handler.postDelayed({
                cambiarColor(binding.luzRoja, R.color.rojo_off)
                // Luz amarilla
                cambiarColor(binding.luzAmarilla, R.color.amarillo_on)
                playAudio(R.raw.semaforo_amarillo)
                handler.postDelayed({
                    cambiarColor(binding.luzAmarilla, R.color.amarillo_off)
                    // Luz verde
                    cambiarColor(binding.luzVerde, R.color.verde_on)
                    playAudio(R.raw.semaforo_verde)
                    handler.postDelayed({
                        cambiarColor(binding.luzVerde, R.color.verde_off)
                        iniciarSemaforo() // Reiniciar ciclo
                    }, 9500) // 3 segundos para verde
                }, 6000) // 1 segundo para amarillo
            }, 8000) // 5 segundos para rojo
        }
    }
    private fun detenerSemaforo() {
        // Luz roja
        cambiarColor(binding.luzRoja, R.color.rojo_off)
        // Luz amarilla
        cambiarColor(binding.luzAmarilla, R.color.amarillo_off)
        // Luz verde
        cambiarColor(binding.luzVerde, R.color.verde_off)
        semaforoActivo = false
    }
    private fun updateUIWithDeafultValues(){
        Log.d("HomeFragment", "Desconectado")
        binding.txtConectado.text = "Desconectado"

    }

    private fun playAudio(audioResID: Int) {
        try {
            mediaPlayer = MediaPlayer.create(requireContext(), audioResID)
            mediaPlayer?.let {
                it.setOnPreparedListener { player -> player.start() }
                it.setOnErrorListener { player, what, extra ->
                    Log.e("MediaPlayer", "Error al preparar el MediaPlayer: what=$what, extra=$extra")
                    true // Indica que el error fue manejado
                }
            } ?: run {
                Log.e("MediaPlayer", "Error al crear el MediaPlayer")
            }
        } catch (e: Exception) {
            Log.e("MediaPlayer", "Excepción al reproducir audio: ${e.message}")
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