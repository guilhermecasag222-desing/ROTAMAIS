package br.com.rotamais.data

import android.content.Context

/**
 * Configuracoes do app.
 * SharedPreferences de proposito: leitura sincrona, leve, sem dependencia extra.
 */
class Prefs(ctx: Context) {

    private val sp = ctx.getSharedPreferences("rotamais", Context.MODE_PRIVATE)

    var tempoParadaMin: Double
        get() = sp.getFloat("tempoParadaMin", 3.0f).toDouble()
        set(v) { sp.edit().putFloat("tempoParadaMin", v.toFloat()).apply() }

    var velocidadeKmh: Double
        get() = sp.getFloat("velocidadeKmh", 35.0f).toDouble()
        set(v) { sp.edit().putFloat("velocidadeKmh", v.toFloat()).apply() }

    /** Multiplicador que converte linha reta em distancia de estrada. */
    var fatorRodoviario: Double
        get() = sp.getFloat("fatorRodoviario", 1.35f).toDouble()
        set(v) { sp.edit().putFloat("fatorRodoviario", v.toFloat()).apply() }

    var consumoKmL: Double
        get() = sp.getFloat("consumoKmL", 9.0f).toDouble()
        set(v) { sp.edit().putFloat("consumoKmL", v.toFloat()).apply() }

    var precoLitro: Double
        get() = sp.getFloat("precoLitro", 5.80f).toDouble()
        set(v) { sp.edit().putFloat("precoLitro", v.toFloat()).apply() }

    var tamanhoLote: Int
        get() = sp.getInt("tamanhoLote", 10)
        set(v) { sp.edit().putInt("tamanhoLote", v).apply() }

    var raioClusterKm: Double
        get() = sp.getFloat("raioClusterKm", 2.5f).toDouble()
        set(v) { sp.edit().putFloat("raioClusterKm", v.toFloat()).apply() }

    var preferirComercio: Boolean
        get() = sp.getBoolean("preferirComercio", true)
        set(v) { sp.edit().putBoolean("preferirComercio", v).apply() }

    var usarOsrm: Boolean
        get() = sp.getBoolean("usarOsrm", false)
        set(v) { sp.edit().putBoolean("usarOsrm", v).apply() }

    /** Sufixo colado no endereco quando ele nao traz cidade/UF. */
    var regiaoPadrao: String
        get() = sp.getString("regiaoPadrao", "Santa Catarina, Brasil") ?: "Santa Catarina, Brasil"
        set(v) { sp.edit().putString("regiaoPadrao", v).apply() }

    var metaMinutos: Int
        get() = sp.getInt("metaMinutos", 300)
        set(v) { sp.edit().putInt("metaMinutos", v).apply() }

    var inicioRotaEm: Long
        get() = sp.getLong("inicioRotaEm", 0L)
        set(v) { sp.edit().putLong("inicioRotaEm", v).apply() }

    var odometroInicial: Double
        get() = sp.getFloat("odometroInicial", 0f).toDouble()
        set(v) { sp.edit().putFloat("odometroInicial", v.toFloat()).apply() }

    var abastecimentoValor: Double
        get() = sp.getFloat("abastecimentoValor", 0f).toDouble()
        set(v) { sp.edit().putFloat("abastecimentoValor", v.toFloat()).apply() }

    /** Pacote do app que o servico de leitura observa. Vazio = qualquer app. */
    var pacoteAlvo: String
        get() = sp.getString("pacoteAlvo", "") ?: ""
        set(v) { sp.edit().putString("pacoteAlvo", v).apply() }

    /** Ultimo app visto pelo servico, para o usuario confirmar o alvo sem digitar nada. */
    var ultimoPacoteVisto: String
        get() = sp.getString("ultimoPacoteVisto", "") ?: ""
        set(v) { sp.edit().putString("ultimoPacoteVisto", v).apply() }

    var veiculo: String
        get() = sp.getString("veiculo", "Ford Fiesta Sedan 2013") ?: ""
        set(v) { sp.edit().putString("veiculo", v).apply() }

    /** Ultima posicao conhecida, usada enquanto o GPS nao responde. */
    var ultimaLat: Double
        get() = java.lang.Double.longBitsToDouble(sp.getLong("ultimaLat", 0L))
        set(v) { sp.edit().putLong("ultimaLat", java.lang.Double.doubleToRawLongBits(v)).apply() }

    var ultimaLon: Double
        get() = java.lang.Double.longBitsToDouble(sp.getLong("ultimaLon", 0L))
        set(v) { sp.edit().putLong("ultimaLon", java.lang.Double.doubleToRawLongBits(v)).apply() }
}
