package com.yerbanalytics.camara.captura

import java.security.MessageDigest

/**
 * Hash hexadecimal en minúsculas de los bytes del JPEG, tal como los exige el contrato.
 *
 * El backend lo verifica contra lo que recibió y rechaza con 422 si no coincide, devolviendo la
 * orden al circuito de reintento. Por eso se calcula sobre los bytes **que se van a enviar**, no
 * sobre ninguna representación intermedia.
 */
object Sha256 {
    fun hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val sb = StringBuilder(digest.size * 2)
        for (b in digest) sb.append("%02x".format(b))
        return sb.toString()
    }
}
