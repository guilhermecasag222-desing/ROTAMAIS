package br.com.rotamais.captura

import android.accessibilityservice.AccessibilityService
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import br.com.rotamais.data.AppDatabase
import br.com.rotamais.data.Captura
import br.com.rotamais.data.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Le passivamente o texto que aparece na tela de outro app enquanto o entregador
 * o usa normalmente. Nao toca, nao clica, nao altera nada: so escuta.
 *
 * Limites deliberados:
 *  - grava apenas o que parece endereco (logradouro + CEP ou UF);
 *  - ignora o proprio ROTA+ e a interface do sistema;
 *  - se um app alvo estiver escolhido, ignora todos os outros;
 *  - dedup por texto identico, para nao encher o banco com o mesmo quadro.
 *
 * Nao faz engenharia reversa nem intercepta trafego -- le a mesma tela que o
 * usuario esta vendo, com permissao dada por ele nas configuracoes do Android.
 */
class ServicoLeitura : AccessibilityService() {

    private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs by lazy { Prefs(this) }

    private var ultimoProcessado = 0L
    private var ultimoTexto = ""

    companion object {
        private const val INTERVALO_MS = 700L
        private const val MAX_NOS = 400

        private val RE_LOGRADOURO = Regex(
            """(?i)\b(rua|r\.|av\.|avenida|trav\.|travessa|rod\.|rodovia|estrada|estr\.|""" +
                    """servid[aã]o|alameda|pra[cç]a|linha|beco|marginal)\b"""
        )
        private val RE_CEP = Regex("""\b\d{5}[-.\s]?\d{3}\b""")
        private val RE_UF = Regex("""\b(SC|RS|PR|SP|RJ|MG|BA|PE|CE|GO|MT|MS|DF|ES|PA|AM)\b""")

        /** O usuario ativou o ROTA+ em Configuracoes > Acessibilidade? */
        fun ativo(ctx: android.content.Context): Boolean {
            val habilitados = Settings.Secure.getString(
                ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return habilitados.contains(ctx.packageName)
        }
    }

    override fun onAccessibilityEvent(evento: AccessibilityEvent?) {
        if (evento == null) return
        val pacote = evento.packageName?.toString() ?: return

        if (pacote == packageName) return
        if (pacote.startsWith("com.android.systemui")) return

        val alvo = prefs.pacoteAlvo
        if (alvo.isNotBlank() && pacote != alvo) return

        prefs.ultimoPacoteVisto = pacote

        val agora = System.currentTimeMillis()
        if (agora - ultimoProcessado < INTERVALO_MS) return
        ultimoProcessado = agora

        val raiz = rootInActiveWindow ?: return
        val linhas = mutableListOf<String>()
        coletar(raiz, linhas, 0)
        if (linhas.isEmpty()) return

        val texto = linhas.joinToString("\n")
        if (!pareceEndereco(texto)) return
        if (texto == ultimoTexto) return
        ultimoTexto = texto

        escopo.launch {
            val dao = AppDatabase.obter(applicationContext).capturaDao()
            if (dao.quantasComTexto(texto) == 0) {
                dao.inserir(Captura(texto = texto, pacote = pacote))
            }
        }
    }

    override fun onInterrupt() { /* nada a desfazer: o servico so le */ }

    /** Percorre a arvore da tela juntando todo texto visivel. */
    private fun coletar(no: AccessibilityNodeInfo?, saida: MutableList<String>, nivel: Int) {
        if (no == null || saida.size >= MAX_NOS || nivel > 40) return
        val t = no.text?.toString()?.trim()
        if (!t.isNullOrBlank() && t.length in 2..200) saida.add(t)
        val d = no.contentDescription?.toString()?.trim()
        if (!d.isNullOrBlank() && d.length in 2..200 && d != t) saida.add(d)
        for (i in 0 until no.childCount) coletar(no.getChild(i), saida, nivel + 1)
    }

    /** Filtro barato: sem logradouro nao e endereco, e sem CEP/UF nao da para geocodificar. */
    private fun pareceEndereco(texto: String): Boolean =
        RE_LOGRADOURO.containsMatchIn(texto) &&
                (RE_CEP.containsMatchIn(texto) || RE_UF.containsMatchIn(texto))
}
