package com.geofilho.dominogjieshu.update

import java.io.File

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val version: Long, val notes: String?) : UpdateState
    data class Downloading(val percent: Int) : UpdateState
    /** Baixou, mas falta permitir "instalar apps desconhecidos". */
    data class NeedPermission(val file: File) : UpdateState
    data class Failed(val message: String) : UpdateState
}
