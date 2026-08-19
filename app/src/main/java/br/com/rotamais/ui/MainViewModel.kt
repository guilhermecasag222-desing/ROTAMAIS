package br.com.rotamais.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import br.com.rotamais.data.AppDatabase
import br.com.rotamais.data.Captura
import br.com.rotamais.data.Entrega
import br.com.rotamais.data.Prefs
import br.com.rotamais.data.RotaHistorico
import br.com.rotamais.data.StatusEntrega
import br.com.rotamais.data.TipoLocal
import br.com.rotamais.geo.Geo
import br.com.rotamais.geo.Geocodificador
import br.com.rotamais.geo.Localizacao
import br.com.rotamais.geo.Osrm
import br.com.rotamais.ocr.Confianca
import br.com.rotamais.ocr.EnderecoLido
import br.com.rotamais.ocr.LeitorEtiqueta
import br.com.rotamais.otim.Otimizador
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class ParadaUi(
    val entrega: Entrega,
    val ordem: Int,
    val kmDoAnterior: Double,
    val minutosAcumulados: Int
)

/** Uma etiqueta lida por OCR, aguardando conferencia antes de virar entrega. */
data class ItemRevisao(
    val id: Int,
    val texto: String,
    val confianca: Confianca,
    val destinatario: String?,
    val textoBruto: String,
    val origem: String,
    val incluir: Boolean = true
)

data class UiState(
    val carregando: Boolean = false,
    val progresso: String? = null,
    val revisao: List<ItemRevisao> = emptyList(),
    val mensagem: String? = null,
    val origemLat: Double? = null,
    val origemLon: Double? = null,
    val origemRotulo: String = "sem localizacao",
    val lote: List<ParadaUi> = emptyList(),
    val kmLote: Double = 0.0,
    val minutosLote: Int = 0,
    val litrosLote: Double = 0.0,
    val custoLote: Double = 0.0,
    val kmSemOtimizar: Double = 0.0,
    val minutosSemOtimizar: Int = 0,
    val fonteDistancia: String = "estimativa (linha reta x fator)"
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AppDatabase.obter(app)
    private val entregaDao = db.entregaDao()
    private val rotaDao = db.rotaDao()
    private val capturaDao = db.capturaDao()
    val prefs = Prefs(app)

    val entregas: StateFlow<List<Entrega>> = entregaDao.observarTodas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val historico: StateFlow<List<RotaHistorico>> = rotaDao.observarTodas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Telas capturadas pelo servico de leitura, ainda nao interpretadas. */
    val capturas: StateFlow<List<Captura>> = capturaDao.observarTodas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui

    init {
        val lat = prefs.ultimaLat
        val lon = prefs.ultimaLon
        if (lat != 0.0 || lon != 0.0) {
            _ui.value = _ui.value.copy(
                origemLat = lat, origemLon = lon,
                origemRotulo = "ultima posicao salva"
            )
        }
    }

    fun limparMensagem() { _ui.value = _ui.value.copy(mensagem = null) }

    // ------------------------------------------------------------ Localizacao

    fun atualizarLocalizacao(aoTerminar: (() -> Unit)? = null) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(carregando = true, progresso = "Buscando GPS...")
            val loc = Localizacao.atual(getApplication())
            if (loc != null) {
                prefs.ultimaLat = loc.latitude
                prefs.ultimaLon = loc.longitude
                _ui.value = _ui.value.copy(
                    origemLat = loc.latitude,
                    origemLon = loc.longitude,
                    origemRotulo = "GPS (%.5f, %.5f)".format(loc.latitude, loc.longitude),
                    carregando = false, progresso = null
                )
            } else {
                _ui.value = _ui.value.copy(
                    carregando = false, progresso = null,
                    mensagem = "Nao consegui a localizacao. Confira permissao e GPS ligado."
                )
            }
            aoTerminar?.invoke()
        }
    }

    fun definirOrigemManual(lat: Double, lon: Double, rotulo: String) {
        prefs.ultimaLat = lat
        prefs.ultimaLon = lon
        _ui.value = _ui.value.copy(origemLat = lat, origemLon = lon, origemRotulo = rotulo)
    }

    // -------------------------------------------------------------- Entregas

    /**
     * Aceita texto colado, uma entrega por linha. Formatos:
     *   "Rua X, 123, Centro, Ararangua"
     *   "22 | Rua X, 123, Centro, Ararangua"
     *   "22 | Rua X, 123 | -28.9351,-49.4917"     (coordenada ja pronta)
     */
    fun adicionarEnderecos(texto: String) {
        viewModelScope.launch {
            val linhas = texto.lines().map { it.trim() }.filter { it.isNotEmpty() }
            if (linhas.isEmpty()) return@launch

            val existentes = entregaDao.todas().size
            val novas = mutableListOf<Entrega>()

            linhas.forEachIndexed { i, linha ->
                val partes = linha.split("|").map { it.trim() }
                var codigo = "${existentes + i + 1}"
                var endereco = linha
                var lat: Double? = null
                var lon: Double? = null

                if (partes.size >= 2) {
                    if (partes[0].length <= 12) {
                        codigo = partes[0]
                        endereco = partes[1]
                    } else {
                        endereco = partes[0]
                    }
                    val ultimo = partes.last()
                    val coord = Regex("""^(-?\d+[.,]\d+)\s*,\s*(-?\d+[.,]\d+)$""").find(ultimo)
                    if (coord != null) {
                        lat = coord.groupValues[1].replace(",", ".").toDoubleOrNull()
                        lon = coord.groupValues[2].replace(",", ".").toDoubleOrNull()
                    }
                }

                novas += Entrega(
                    codigo = codigo,
                    enderecoBruto = endereco,
                    lat = lat,
                    lon = lon,
                    tipo = Geocodificador.classificar(endereco)
                )
            }
            entregaDao.inserirVarias(novas)
            _ui.value = _ui.value.copy(mensagem = "${novas.size} entrega(s) adicionada(s).")
        }
    }

    fun geocodificarPendentes() {
        viewModelScope.launch {
            val alvo = entregaDao.semCoordenada(StatusEntrega.PENDENTE)
            if (alvo.isEmpty()) {
                _ui.value = _ui.value.copy(mensagem = "Todas as entregas ja tem coordenada.")
                return@launch
            }
            _ui.value = _ui.value.copy(carregando = true, progresso = "Geocodificando 0/${alvo.size}")

            var ok = 0
            alvo.forEachIndexed { i, e ->
                _ui.value = _ui.value.copy(progresso = "Geocodificando ${i + 1}/${alvo.size}")
                val r = Geocodificador.geocodificar(
                    getApplication(), e.enderecoBruto, prefs.regiaoPadrao
                )
                if (r != null) {
                    ok++
                    entregaDao.atualizar(
                        e.copy(
                            lat = r.lat, lon = r.lon,
                            enderecoNormalizado = r.enderecoNormalizado,
                            bairro = r.bairro ?: e.bairro,
                            cidade = r.cidade ?: e.cidade,
                            cep = r.cep ?: e.cep,
                            tipo = if (e.tipo == TipoLocal.COMERCIO) TipoLocal.COMERCIO else r.tipo,
                            erroGeo = null
                        )
                    )
                } else {
                    entregaDao.atualizar(e.copy(erroGeo = "endereco nao encontrado"))
                }
            }
            _ui.value = _ui.value.copy(
                carregando = false, progresso = null,
                mensagem = "$ok de ${alvo.size} geocodificadas."
            )
        }
    }

    // ------------------------------------------------------ Captura por OCR

    /**
     * Le em lote as fotos das etiquetas. Nada e gravado direto: tudo cai na fila de
     * revisao, porque OCR erra e endereco errado custa quilometro.
     */
    fun importarImagens(uris: List<Uri>) {
        viewModelScope.launch {
            if (uris.isEmpty()) return@launch
            _ui.value = _ui.value.copy(carregando = true, progresso = "Lendo 0/${uris.size}")

            val achados = mutableListOf<ItemRevisao>()
            var proximoId = _ui.value.revisao.maxOfOrNull { it.id }?.plus(1) ?: 0

            uris.forEachIndexed { i, uri ->
                _ui.value = _ui.value.copy(progresso = "Lendo foto ${i + 1}/${uris.size}")
                val lidos = LeitorEtiqueta.ler(getApplication(), uri, "foto ${i + 1}")
                lidos.forEach { l ->
                    achados += ItemRevisao(
                        id = proximoId++,
                        texto = montarTexto(l),
                        confianca = l.confianca,
                        destinatario = l.destinatario,
                        textoBruto = l.textoBruto,
                        origem = l.origem
                    )
                }
            }

            val duvidosos = achados.count { it.confianca != Confianca.ALTA }
            _ui.value = _ui.value.copy(
                carregando = false, progresso = null,
                revisao = _ui.value.revisao + achados,
                mensagem = "${achados.size} endereco(s) lido(s) em ${uris.size} foto(s)." +
                        if (duvidosos > 0) " $duvidosos precisa(m) de conferencia." else ""
            )
        }
    }

    // -------------------------------------------- Leitura automatica da tela

    /** Interpreta o que o servico pescou e joga na fila de conferencia. */
    fun processarCapturas() {
        viewModelScope.launch {
            val capturado = capturaDao.todas()
            if (capturado.isEmpty()) {
                _ui.value = _ui.value.copy(
                    mensagem = "Nenhuma tela capturada ainda. Abra o app de entregas e " +
                            "navegue por ele com a leitura ligada."
                )
                return@launch
            }
            _ui.value = _ui.value.copy(carregando = true, progresso = "Interpretando...")

            val jaSalvas = entregaDao.todas().map { normalizar(it.enderecoBruto) }.toMutableSet()
            val fila = _ui.value.revisao.toMutableList()
            fila.forEach { jaSalvas.add(normalizar(it.texto)) }
            var proximoId = fila.maxOfOrNull { it.id }?.plus(1) ?: 0

            capturado.forEach { c ->
                LeitorEtiqueta.interpretar(c.texto, "tela").forEach { l ->
                    val t = montarTexto(l)
                    if (t.isBlank() || !jaSalvas.add(normalizar(t))) return@forEach
                    fila += ItemRevisao(
                        id = proximoId++,
                        texto = t,
                        confianca = l.confianca,
                        destinatario = l.destinatario,
                        textoBruto = l.textoBruto,
                        origem = "tela"
                    )
                }
            }

            capturaDao.apagarTudo()
            _ui.value = _ui.value.copy(
                carregando = false, progresso = null,
                revisao = fila,
                mensagem = if (fila.isEmpty())
                    "Li ${capturado.size} tela(s), mas nao reconheci endereco. " +
                            "Use 'ver texto lido' para eu ajustar o interpretador."
                else "${fila.size} endereco(s) para conferir."
            )
        }
    }

    fun limparCapturas() = viewModelScope.launch { capturaDao.apagarTudo() }

    /** Fixa como alvo o ultimo app que o servico viu, sem o usuario digitar pacote. */
    fun fixarAppAlvo(): String {
        val p = prefs.ultimoPacoteVisto
        if (p.isNotBlank()) prefs.pacoteAlvo = p
        return p
    }

    fun liberarAppAlvo() { prefs.pacoteAlvo = "" }

    /**
     * Recebe cada leitura do scanner continuo. Roda na thread da camera, entao usa
     * update{} para nao perder leitura concorrente. Endereco repetido e descartado
     * em silencio -- a mesma etiqueta passa por dezenas de frames.
     */
    fun processarLeituraScanner(texto: String) {
        val achados = LeitorEtiqueta.interpretar(texto, "scanner")
            .filter { it.confianca != Confianca.BAIXA }
        if (achados.isEmpty()) return

        _ui.update { atual ->
            val vistos = (atual.revisao.map { normalizar(it.texto) } +
                    entregas.value.map { normalizar(it.enderecoBruto) }).toMutableSet()
            var proximoId = atual.revisao.maxOfOrNull { it.id }?.plus(1) ?: 0
            val novos = mutableListOf<ItemRevisao>()

            achados.forEach { l ->
                val t = montarTexto(l)
                if (t.isBlank() || !vistos.add(normalizar(t))) return@forEach
                novos += ItemRevisao(
                    id = proximoId++,
                    texto = t,
                    confianca = l.confianca,
                    destinatario = l.destinatario,
                    textoBruto = l.textoBruto,
                    origem = "scanner"
                )
            }
            if (novos.isEmpty()) atual else atual.copy(revisao = atual.revisao + novos)
        }
    }

    private fun montarTexto(l: EnderecoLido): String {
        val partes = mutableListOf(l.endereco)
        l.cidade?.let { if (!l.endereco.contains(it, ignoreCase = true)) partes.add(it) }
        return partes.joinToString(", ").replace(Regex("""\s*,\s*,+"""), ", ").trim()
    }

    fun editarRevisao(id: Int, texto: String) {
        _ui.value = _ui.value.copy(
            revisao = _ui.value.revisao.map { if (it.id == id) it.copy(texto = texto) else it }
        )
    }

    fun alternarRevisao(id: Int) {
        _ui.value = _ui.value.copy(
            revisao = _ui.value.revisao.map { if (it.id == id) it.copy(incluir = !it.incluir) else it }
        )
    }

    fun descartarRevisao() {
        _ui.value = _ui.value.copy(revisao = emptyList())
    }

    /** Confirma a fila de revisao, grava como entregas e ja dispara a geocodificacao. */
    fun confirmarRevisao() {
        viewModelScope.launch {
            val itens = _ui.value.revisao.filter { it.incluir && it.texto.isNotBlank() }
            if (itens.isEmpty()) {
                _ui.value = _ui.value.copy(mensagem = "Nada selecionado para importar.")
                return@launch
            }
            val jaExistem = entregaDao.todas()
            val vistos = jaExistem.map { normalizar(it.enderecoBruto) }.toMutableSet()
            var proximoCodigo = jaExistem.size

            val novas = mutableListOf<Entrega>()
            var repetidos = 0
            itens.forEach { item ->
                val chave = normalizar(item.texto)
                if (!vistos.add(chave)) { repetidos++; return@forEach }
                proximoCodigo++
                novas += Entrega(
                    codigo = "$proximoCodigo",
                    enderecoBruto = item.texto,
                    tipo = Geocodificador.classificar(
                        listOfNotNull(item.destinatario, item.texto).joinToString(" ")
                    )
                )
            }

            entregaDao.inserirVarias(novas)
            _ui.value = _ui.value.copy(
                revisao = emptyList(),
                mensagem = "${novas.size} entrega(s) importada(s)." +
                        if (repetidos > 0) " $repetidos repetida(s) ignorada(s)." else ""
            )
            geocodificarPendentes()
        }
    }

    /** Chave de deduplicacao: ignora acento, pontuacao e caixa. */
    private fun normalizar(s: String): String =
        s.lowercase().replace(Regex("""[^a-z0-9]"""), "")

    fun apagarEntrega(e: Entrega) = viewModelScope.launch { entregaDao.apagar(e) }

    fun apagarTudo() = viewModelScope.launch {
        entregaDao.apagarTudo()
        _ui.value = _ui.value.copy(lote = emptyList(), kmLote = 0.0, minutosLote = 0)
    }

    fun corrigirCoordenada(e: Entrega, lat: Double, lon: Double) = viewModelScope.launch {
        entregaDao.atualizar(e.copy(lat = lat, lon = lon, erroGeo = null))
    }

    fun alternarTipo(e: Entrega) = viewModelScope.launch {
        val novo = when (e.tipo) {
            TipoLocal.COMERCIO -> TipoLocal.RESIDENCIAL
            TipoLocal.RESIDENCIAL -> TipoLocal.DESCONHECIDO
            TipoLocal.DESCONHECIDO -> TipoLocal.COMERCIO
        }
        entregaDao.atualizar(e.copy(tipo = novo))
    }

    // ------------------------------------------------------------- Otimizacao

    fun otimizar() {
        viewModelScope.launch {
            val origemLat = _ui.value.origemLat
            val origemLon = _ui.value.origemLon
            if (origemLat == null || origemLon == null) {
                _ui.value = _ui.value.copy(mensagem = "Preciso da sua localizacao antes de otimizar.")
                return@launch
            }

            val pendentes = entregaDao.porStatus(StatusEntrega.PENDENTE)
                .filter { it.lat != null && it.lon != null }

            if (pendentes.isEmpty()) {
                _ui.value = _ui.value.copy(
                    lote = emptyList(),
                    mensagem = "Nenhuma entrega pendente com coordenada."
                )
                return@launch
            }

            _ui.value = _ui.value.copy(carregando = true, progresso = "Otimizando...")

            val pontos = pendentes.map {
                Otimizador.Ponto(it.id, it.lat!!, it.lon!!, it.tipo == TipoLocal.COMERCIO)
            }

            var fonte = "estimativa (linha reta x ${prefs.fatorRodoviario})"
            var matriz = Otimizador.matrizHaversine(
                origemLat, origemLon, pontos, prefs.fatorRodoviario, prefs.velocidadeKmh
            )

            if (prefs.usarOsrm && pontos.size <= 89) {
                _ui.value = _ui.value.copy(progresso = "Buscando distancias reais (OSRM)...")
                val coords = mutableListOf(origemLat to origemLon)
                pontos.forEach { coords.add(it.lat to it.lon) }
                val m = Osrm.matriz(coords)
                if (m != null) {
                    matriz = Otimizador.Matriz(m.km, m.minutos)
                    fonte = "OSRM (rota real)"
                } else {
                    fonte = "estimativa (OSRM indisponivel)"
                }
            }

            val selecionados = Otimizador.selecionarLote(
                origemLat, origemLon, pontos, prefs.tamanhoLote, prefs.raioClusterKm
            )
            val ordenados = Otimizador.ordenar(
                matriz, selecionados, pontos, prefs.preferirComercio
            )

            // Comparativo honesto: MESMAS entregas, ordem de captura x ordem otimizada.
            val ordemCaptura = selecionados.sortedBy { pendentes[it].capturadaEm }
            val kmSem = Otimizador.custoKm(matriz, ordemCaptura)
            val minSem = Otimizador.custoMinutos(matriz, ordemCaptura) +
                    ordemCaptura.size * prefs.tempoParadaMin

            // Monta a UI e persiste a ordem no banco.
            entregaDao.limparLote()
            val paradas = mutableListOf<ParadaUi>()
            var anterior = 0
            var kmAcum = 0.0
            var minAcum = 0.0
            ordenados.forEachIndexed { pos, idx ->
                val km = matriz.km[anterior][idx + 1]
                kmAcum += km
                minAcum += matriz.minutos[anterior][idx + 1] + prefs.tempoParadaMin
                anterior = idx + 1
                val e = pendentes[idx]
                entregaDao.definirOrdem(e.id, pos + 1)
                paradas += ParadaUi(
                    entrega = e.copy(ordemNoLote = pos + 1),
                    ordem = pos + 1,
                    kmDoAnterior = km,
                    minutosAcumulados = minAcum.roundToInt()
                )
            }

            val litros = if (prefs.consumoKmL > 0) kmAcum / prefs.consumoKmL else 0.0

            _ui.value = _ui.value.copy(
                carregando = false, progresso = null,
                lote = paradas,
                kmLote = kmAcum,
                minutosLote = minAcum.roundToInt(),
                litrosLote = litros,
                custoLote = litros * prefs.precoLitro,
                kmSemOtimizar = kmSem,
                minutosSemOtimizar = minSem.roundToInt(),
                fonteDistancia = fonte
            )

            if (prefs.inicioRotaEm == 0L) prefs.inicioRotaEm = System.currentTimeMillis()
        }
    }

    /**
     * Conclui a parada, grava km/tempo reais e move a origem para o ponto da entrega.
     * E isso que faz a rota ser recalculada a partir de onde voce esta agora,
     * e nao do ponto inicial do dia.
     */
    fun concluir(parada: ParadaUi) {
        viewModelScope.launch {
            val e = parada.entrega
            val agora = System.currentTimeMillis()

            val origemLat = _ui.value.origemLat
            val origemLon = _ui.value.origemLon
            val destLat = e.lat
            val destLon = e.lon
            val kmReal = if (origemLat != null && origemLon != null && destLat != null && destLon != null)
                Geo.haversineKm(origemLat, origemLon, destLat, destLon) * prefs.fatorRodoviario
            else parada.kmDoAnterior

            val ultimaConclusao = entregaDao
                .concluidasDesde(prefs.inicioRotaEm, StatusEntrega.CONCLUIDA)
                .maxByOrNull { it.concluidaEm ?: 0L }?.concluidaEm
                ?: prefs.inicioRotaEm.takeIf { it > 0L }
                ?: agora

            val minutos = ((agora - ultimaConclusao) / 60000.0).roundToInt().coerceIn(0, 240)

            entregaDao.atualizar(
                e.copy(
                    status = StatusEntrega.CONCLUIDA,
                    concluidaEm = agora,
                    kmPercorrido = kmReal,
                    minutosGastos = minutos,
                    ordemNoLote = null
                )
            )

            if (destLat != null && destLon != null) {
                prefs.ultimaLat = destLat
                prefs.ultimaLon = destLon
                _ui.value = _ui.value.copy(
                    origemLat = destLat,
                    origemLon = destLon,
                    origemRotulo = "apos entrega ${e.codigo}"
                )
            }
            otimizar()
        }
    }

    fun pular(parada: ParadaUi) = viewModelScope.launch {
        entregaDao.atualizar(parada.entrega.copy(status = StatusEntrega.PULADA, ordemNoLote = null))
        otimizar()
    }

    fun reabrir(e: Entrega) = viewModelScope.launch {
        entregaDao.atualizar(e.copy(status = StatusEntrega.PENDENTE, concluidaEm = null))
    }

    // -------------------------------------------------------------- Historico

    fun tempoMedioRealPorParada(): Double? {
        val lista = entregas.value.filter { it.status == StatusEntrega.CONCLUIDA && it.minutosGastos > 0 }
        if (lista.size < 5) return null
        return lista.sumOf { it.minutosGastos }.toDouble() / lista.size
    }

    fun finalizarRota(regiao: String) {
        viewModelScope.launch {
            val inicio = prefs.inicioRotaEm.takeIf { it > 0L } ?: System.currentTimeMillis()
            val concluidas = entregaDao.concluidasDesde(inicio, StatusEntrega.CONCLUIDA)
            if (concluidas.isEmpty()) {
                _ui.value = _ui.value.copy(mensagem = "Nenhuma entrega concluida nesta rota.")
                return@launch
            }
            val fim = System.currentTimeMillis()
            val km = concluidas.sumOf { it.kmPercorrido }
            val litros = if (prefs.consumoKmL > 0) km / prefs.consumoKmL else 0.0
            rotaDao.inserir(
                RotaHistorico(
                    inicioEm = inicio,
                    fimEm = fim,
                    regiao = regiao.ifBlank { concluidas.firstOrNull()?.cidade ?: "-" },
                    qtdEntregas = concluidas.size,
                    km = km,
                    minutos = ((fim - inicio) / 60000.0).roundToInt(),
                    litros = litros,
                    custo = litros * prefs.precoLitro
                )
            )
            prefs.inicioRotaEm = 0L
            _ui.value = _ui.value.copy(
                mensagem = "Rota salva no historico: ${concluidas.size} entregas, %.1f km".format(km)
            )
        }
    }

    fun limparConcluidas() = viewModelScope.launch {
        entregaDao.apagarPorStatus(StatusEntrega.CONCLUIDA)
    }
}
