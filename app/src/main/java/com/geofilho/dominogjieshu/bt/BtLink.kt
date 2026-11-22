package com.geofilho.dominogjieshu.bt

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

sealed interface LinkState {
    /** Sala fechada e sem conexões. */
    data object Idle : LinkState
    /** Sala aberta, aguardando amigos, sem conexões ainda. */
    data object Hosting : LinkState
    data class Connecting(val name: String) : LinkState
    /** Pelo menos uma conexão ativa (a sala continua aberta para mais amigos). */
    data object Connected : LinkState
}

data class FoundDevice(val name: String, val address: String, val device: BluetoothDevice)

/** Eventos das conexões: cada conexão tem um número próprio. */
sealed interface LinkEvent {
    /** Nova conexão. [accepted] = true se o amigo conectou neste aparelho. */
    data class Opened(val id: Int, val accepted: Boolean, val peerName: String) : LinkEvent
    data class Line(val id: Int, val text: String) : LinkEvent
    data class Closed(val id: Int) : LinkEvent
}

/**
 * Bluetooth clássico (RFCOMM) com várias conexões ao mesmo tempo (até 4 jogadores).
 * A sala fica aberta (aceitando conexões) e este aparelho também pode conectar em amigos.
 * Quem comanda o jogo e como as mensagens passam entre os aparelhos é decidido em [BtSession].
 * As permissões de Bluetooth são pedidas pela tela antes de chamar estes métodos.
 */
@SuppressLint("MissingPermission")
class BtLink(context: Context) {
    private val app = context.applicationContext
    private val adapter: BluetoothAdapter? =
        (app.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?)?.adapter
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<LinkState>(LinkState.Idle)
    val state: StateFlow<LinkState> = _state
    private val _events = MutableSharedFlow<LinkEvent>(extraBufferCapacity = 256)
    val events: SharedFlow<LinkEvent> = _events
    /** Avisos para a pessoa (ex.: falha ao conectar). */
    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val errors: SharedFlow<String> = _errors
    private val _found = MutableStateFlow<List<FoundDevice>>(emptyList())
    val found: StateFlow<List<FoundDevice>> = _found
    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning

    private class Conn(val id: Int, val socket: BluetoothSocket, val out: OutputStream, var job: Job? = null)
    private val conns = ConcurrentHashMap<Int, Conn>()
    private val ids = AtomicInteger()
    @Volatile private var server: BluetoothServerSocket? = null
    @Volatile private var connecting: String? = null
    private var receiverRegistered = false

    val available get() = adapter != null
    val enabled get() = adapter?.isEnabled == true
    val connectionCount get() = conns.size

    /** O celular já está visível para outros aparelhos Bluetooth? */
    val isDiscoverable: Boolean
        get() = runCatching { adapter?.scanMode == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE }.getOrDefault(false)

    private fun refreshState() {
        _state.value = when {
            conns.isNotEmpty() -> LinkState.Connected
            connecting != null -> LinkState.Connecting(connecting!!)
            server != null -> LinkState.Hosting
            else -> LinkState.Idle
        }
    }

    // ---------------- Descoberta ----------------
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val d: BluetoothDevice = if (Build.VERSION.SDK_INT >= 33)
                        i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java) ?: return
                    else @Suppress("DEPRECATION") (i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE) ?: return)
                    addFound(d)
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> _scanning.value = false
            }
        }
    }

    private fun addFound(d: BluetoothDevice) {
        val name = runCatching { d.name }.getOrNull() ?: return
        if (_found.value.any { it.address == d.address }) return
        _found.value = _found.value + FoundDevice(name, d.address, d)
    }

    fun startScan() {
        val a = adapter ?: return
        if (!receiverRegistered) {
            val f = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            }
            if (Build.VERSION.SDK_INT >= 33) app.registerReceiver(receiver, f, Context.RECEIVER_EXPORTED)
            else app.registerReceiver(receiver, f)
            receiverRegistered = true
        }
        _found.value = emptyList()
        runCatching { a.bondedDevices }.getOrNull()?.forEach(::addFound)
        if (a.isDiscovering) a.cancelDiscovery()
        _scanning.value = a.startDiscovery()
    }

    fun stopScan() {
        runCatching { adapter?.cancelDiscovery() }
        _scanning.value = false
        if (receiverRegistered) { runCatching { app.unregisterReceiver(receiver) }; receiverRegistered = false }
    }

    // ---------------- Sala (aceita várias conexões) ----------------
    /** Abre a sala: fica aceitando amigos até [closeAll]. */
    fun openRoom() {
        val a = adapter ?: return
        if (server != null) return
        val srv = try {
            a.listenUsingRfcommWithServiceRecord(SERVICE_NAME, APP_UUID)
        } catch (e: Exception) {
            _errors.tryEmit("Não foi possível abrir a sala. Confira se o Bluetooth está ligado.")
            return
        }
        server = srv
        refreshState()
        scope.launch {
            while (server === srv) {
                val s = try { srv.accept() } catch (e: Exception) { null } ?: break
                attach(s, accepted = true)
            }
            if (server === srv) server = null
            refreshState()
        }
    }

    /** Conecta no aparelho de um amigo encontrado na busca. */
    fun join(dev: FoundDevice) {
        stopScan()
        if (connecting != null) return
        connecting = dev.name
        refreshState()
        scope.launch {
            try {
                val s = dev.device.createRfcommSocketToServiceRecord(APP_UUID)
                s.connect()
                connecting = null
                attach(s, accepted = false)
            } catch (e: Exception) {
                connecting = null
                refreshState()
                _errors.tryEmit("Não foi possível conectar a ${dev.name}. Confirme que ele está na tela do Bluetooth.")
            }
        }
    }

    private fun attach(s: BluetoothSocket, accepted: Boolean) {
        val id = ids.incrementAndGet()
        val c = Conn(id, s, s.outputStream)
        conns[id] = c
        refreshState()
        val peer = runCatching { s.remoteDevice.name }.getOrNull() ?: "Amigo"
        _events.tryEmit(LinkEvent.Opened(id, accepted, peer))
        c.job = scope.launch {
            try {
                BufferedReader(InputStreamReader(s.inputStream, Charsets.UTF_8)).use { r ->
                    while (true) {
                        val line = r.readLine() ?: break
                        _events.emit(LinkEvent.Line(id, line))
                    }
                }
            } catch (_: Exception) { }
            if (conns.remove(id) != null) {
                runCatching { s.close() }
                refreshState()
                _events.emit(LinkEvent.Closed(id))
            }
        }
    }

    fun send(id: Int, line: String) {
        val c = conns[id] ?: return
        scope.launch {
            try {
                synchronized(c.out) { c.out.write((line + "\n").toByteArray(Charsets.UTF_8)); c.out.flush() }
            } catch (_: Exception) { close(id) }
        }
    }

    /** Fecha uma conexão (o outro lado percebe e o evento Closed é emitido). */
    fun close(id: Int) {
        val c = conns.remove(id) ?: return
        c.job?.cancel()
        runCatching { c.socket.close() }
        refreshState()
        _events.tryEmit(LinkEvent.Closed(id))
    }

    /** Fecha a sala e todas as conexões. */
    fun closeAll() {
        stopScan()
        val srv = server; server = null
        runCatching { srv?.close() }
        conns.keys.toList().forEach(::close)
        connecting = null
        refreshState()
    }

    companion object {
        const val SERVICE_NAME = "DominogSala"
        val APP_UUID: UUID = UUID.fromString("6f1c2a3e-8d4b-4e0a-9b7a-3d0c5e7f9a21")
    }
}
