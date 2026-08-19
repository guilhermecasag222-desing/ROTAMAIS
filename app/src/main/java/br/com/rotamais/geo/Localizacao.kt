package br.com.rotamais.geo

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object Localizacao {

    fun temPermissao(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

    suspend fun atual(ctx: Context): Location? {
        if (!temPermissao(ctx)) return null
        val client = LocationServices.getFusedLocationProviderClient(ctx)

        val fresca = suspendCancellableCoroutine<Location?> { cont ->
            val cts = CancellationTokenSource()
            try {
                client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                    .addOnSuccessListener { if (cont.isActive) cont.resume(it) }
                    .addOnFailureListener { if (cont.isActive) cont.resume(null) }
                cont.invokeOnCancellation { cts.cancel() }
            } catch (e: SecurityException) {
                if (cont.isActive) cont.resume(null)
            }
        }
        if (fresca != null) return fresca

        // GPS demorou / sem sinal: usa a ultima conhecida do sistema.
        return suspendCancellableCoroutine { cont ->
            try {
                client.lastLocation
                    .addOnSuccessListener { if (cont.isActive) cont.resume(it) }
                    .addOnFailureListener { if (cont.isActive) cont.resume(null) }
            } catch (e: SecurityException) {
                if (cont.isActive) cont.resume(null)
            }
        }
    }

    /** Abre o destino no Google Maps (ou em qualquer app de navegacao instalado). */
    fun abrirNavegacao(ctx: Context, lat: Double, lon: Double, rotulo: String) {
        val label = Uri.encode(rotulo.take(60))
        val intentMaps = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("google.navigation:q=$lat,$lon&mode=d")
        ).setPackage("com.google.android.apps.maps")

        if (intentMaps.resolveActivity(ctx.packageManager) != null) {
            ctx.startActivity(intentMaps.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        val generico = Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lon?q=$lat,$lon($label)"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (generico.resolveActivity(ctx.packageManager) != null) ctx.startActivity(generico)
    }
}
