package com.app.pasoseguro
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.app.pasoseguro.databinding.FragmentHomeBinding
import com.google.android.material.snackbar.Snackbar
import java.io.IOException
import java.util.UUID

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
    val targetMacAddress = "98:D3:32:30:D4:5B"
    private lateinit var devicesBluetooth: MutableList<BluetoothDevice>//lista mutable de objetos tipo bt_device
    private lateinit var bluetoothDevice: BluetoothDevice
    var estadoSemaforo = 0
    private var semaforoActivo = true
    var estadoRojo = 0
    var estadoAmarillo = 0
    var estadoVerde = 0
    @Volatile
    var recibir = 0
    var device : BluetoothDevice? = null
    // Sincronizar el acceso a 'recibir'
    private val recibirLock = Any()

    private var mediaPlayer : MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private val dataReceiverHandler = Handler(Looper.getMainLooper())
    private var isReceivingData = false


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
                    device = devicesBluetooth[position]
                    connectToDevice(device!!)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>) {
                Snackbar.make(requireView(), "Seleccione un dispositivo", Snackbar.LENGTH_SHORT).show()
            }
        }

        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed({
            // Código que quieres ejecutar después del delay
            playAudio(R.raw.calle)
            handler.postDelayed({
                playAudio(R.raw.cruce_dificil)
                // Actualización de la variable sincronizada
                synchronized (recibirLock) {
                    recibir = 1
                }
            }, 4000) // 4000 milisegundos = 4 segundos
        }, 4000)
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
        }
        else {
            playAudio(R.raw.buscando)
            val pairedDevices: Set<BluetoothDevice>? =
                bluetoothAdapter?.bondedDevices// Obtener la lista de dispositivos Bluetooth emparejados

            pairedDevices?.forEach { device ->
                if (device.name == targetDeviceName) {
                    // Intentar conectarse automáticamente al dispositivo encontrado
                    connectToDevice(device)
                }
            }

            //val device: BluetoothDevice = bluetoothAdapter!!.getRemoteDevice(targetMacAddress)
            //connectToDevice(device)
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
            }
            else {
                // Mostrar mensaje si no hay dispositivos emparejados
                Snackbar.make(requireView(), "No hay dispositivos Bluetooth emparejados", Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun connectToDevice(device: BluetoothDevice) {
        Thread {
            try {
                val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
                val socket = device.createRfcommSocketToServiceRecord(uuid)
                socket.connect()
                bluetoothSocket = socket // Guardar el socket conectado

                // Notificar que la conexión fue exitosa
                requireActivity().runOnUiThread {
                    Log.d("Bluetooth", "Conectado al dispositivo")
                    Snackbar.make(requireView(), "Conexión exitosa", Snackbar.LENGTH_SHORT).show()
                }
                Log.d("recibir","recibir es igual: $recibir")
                // Iniciar la recepción de datos
//                if(recibir == 1){
                    Log.d("recibir","entro al recibir==1")
                    playAudio(R.raw.conexion)
                    Handler(Looper.getMainLooper()).postDelayed({
                        startDataReceiving()
                    }, 5000) // Espera 4000ms (4 segundos)
//                }
            } catch (e: IOException) {
                requireActivity().runOnUiThread {
                    Log.e("Bluetooth","Error al conectar o leer datos: " + e.message
                    )
                }
            }
        }.start()
    }

    private fun parseData(data: String) {
        Log.d("HomeFragment", "Parsing data: $data")
        val parts = data.split("|")
        if (parts.size == 3) {
            try {
                // Parsear todos los datos a el formato correspondiente
                estadoRojo = parts[0].toInt()
                estadoAmarillo = parts[1].toInt()
                estadoVerde = parts[2].toInt()
            } catch (e: NumberFormatException) {
                Log.e("homeFragment", "Error parsing data: ${e.message}")
            }
        } else {
            Log.e("homeFragment", "Data format error: expected 3 parts, got ${parts.size}")
        }

    }

    private fun startDataReceiving() {
        Log.d("HomeFragment", "Recibiendo datos:")
        val socket = bluetoothSocket
        if (socket != null) {
            val inputStream = socket.inputStream
            val buffer = ByteArray(1024)
            val stringBuilder = StringBuilder()

            // Evitar ciclo infinito y controlar la recepción de datos con Handler
            isReceivingData = true
            dataReceiverHandler.post(object : Runnable {
                override fun run() {
                    if (isReceivingData) {
                        try {
                            val bytes = inputStream.read(buffer)
                            val data = String(buffer, 0, bytes)
                            stringBuilder.append(data)

                            val lines = stringBuilder.split("\n")
                            for (i in 0 until lines.size - 1) {
                                val line = lines[i].trim()
                                if (line.isNotEmpty()) {
                                    Log.d("HomeFragment", "Received data: $line")
                                    parseData(line)
                                    cambiarColor(binding.luzVerde, R.color.verde_off)
                                    cambiarColor(binding.luzAmarilla, R.color.amarillo_off)
                                    cambiarColor(binding.luzRoja, R.color.rojo_off)
                                    updateUI()
                                }
                            }
                            stringBuilder.clear()
                            stringBuilder.append(lines.last()) // Mantener la última línea incompleta

                        } catch (e: IOException) {
                            Log.e("HomeFragment", "Error leyendo datos: ${e.message}")
                            // Intentar reconectar si el socket se cierra
                            handleBluetoothDisconnection()
                        }

                        // Reintentar recibir datos después de un breve retraso (1 segundo)
                        dataReceiverHandler.postDelayed(this, 1000) // 1 segundo de retraso
                    }
                }
            })
        } else {
            Log.e("HomeFragment", "BluetoothSocket es nulo")
            Snackbar.make(requireView(), "Error al conectar al dispositivo", Snackbar.LENGTH_SHORT).show()
        }
    }

    // Función para manejar la desconexión del Bluetooth y reconectar
    private fun handleBluetoothDisconnection() {
        Log.e("HomeFragment", "Conexión Bluetooth perdida. Intentando reconectar...")
        try {
            bluetoothSocket?.close()
            // Intenta reconectar, por ejemplo, al Bluetooth nuevamente
            device?.let { connectToDevice(it) } // Implementa la lógica de reconexión aquí
        } catch (e: IOException) {
            Log.e("HomeFragment", "Error al cerrar el socket: ${e.message}")
        }
    }

    // Función para detener la recepción de datos
    fun stopDataReceiving() {
        isReceivingData = false
        dataReceiverHandler.removeCallbacksAndMessages(null)
    }


    private fun updateUI() {
        requireActivity().runOnUiThread {
            Log.d("HomeFragment", "Actualizando UI")
            binding.txtConectado.text = "Conectado"

            when {
                estadoVerde == 1 -> {
                    playAudio(R.raw.semaforo_verde)
                    cambiarColor(binding.luzVerde, R.color.verde_on)
                }
                estadoAmarillo == 1 -> {
                    playAudio(R.raw.semaforo_amarillo)
                    cambiarColor(binding.luzAmarilla, R.color.amarillo_on)
                }
                estadoRojo == 1 -> {
                    playAudio(R.raw.semaforo_rojo)
                    cambiarColor(binding.luzRoja, R.color.rojo_on)
                }
            }
        }
    }

    private fun cambiarColor(view: View, color: Int) {
        val background = view.background.mutate() // Asegúrate de no modificar el drawable globalmente
        background.setTint(ContextCompat.getColor(view.context, color)) // Cambia el colorthis, color)) // Cambia el color
    }
    private fun playAudio(audioResID: Int) {
        // Verificar si ya hay un MediaPlayer reproduciendo
        if (mediaPlayer?.isPlaying == true) {
            // Si ya está reproduciendo, no hacer nada
            Log.d("MediaPlayer", "Ya hay un audio reproduciéndose, no se reproducirá uno nuevo.")
            return
        }

        try {
            // Crear un nuevo MediaPlayer para el audio
            mediaPlayer = MediaPlayer.create(requireContext(), audioResID)

            mediaPlayer?.let { player ->
                // Establecer un listener para cuando el MediaPlayer esté preparado
                player.setOnPreparedListener {
                    it.start()  // Inicia la reproducción tan pronto como esté listo
                }

                // Listener para manejar errores en el MediaPlayer
                player.setOnErrorListener { _, what, extra ->
                    Log.e("MediaPlayer", "Error al preparar el MediaPlayer: what=$what, extra=$extra")
                    true // Indica que el error fue manejado
                }

                // Listener para cuando el MediaPlayer termine de reproducir el audio
                player.setOnCompletionListener {
                    Log.d("MediaPlayer", "Reproducción terminada.")
                    // Liberar el MediaPlayer después de que termine de reproducirse
                    it.release()
                    mediaPlayer = null // Liberar la referencia del MediaPlayer
                    Log.d("MediaPlayer", "MediaPlayer liberado.")

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