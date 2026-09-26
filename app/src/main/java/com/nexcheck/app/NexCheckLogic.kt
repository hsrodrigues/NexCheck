package com.nexcheck.app

import java.text.Normalizer

object NexCheckLogic {

    // ==============================================================
    // PERMISSÕES (FONTE ÚNICA PARA MENU E GESTÃO DE USUÁRIOS)
    // ==============================================================
    private val gestaoRoles = setOf("admin", "escritorio", "torre_de_controle")

    fun defaultPermissions(role: String): Map<String, Boolean> {
        val isGestao = role.lowercase().trim() in gestaoRoles
        return mapOf(
            "nova_vistoria_cavalo" to true,
            "nova_vistoria_carreta" to true,
            "historico" to true,
            "fumaca_nova" to true,
            "diario_bordo" to true,
            "pendencias" to isGestao,
            "agenda" to isGestao,
            "fumaca_pendencias" to isGestao,
            "ocorrencias" to isGestao
        )
    }

    // O que está salvo no banco sobrescreve o padrão do cargo; chaves ausentes
    // caem no padrão (antes, salvar uma única chave zerava todas as outras).
    fun effectivePermissions(role: String, stored: Any?): Map<String, Boolean> {
        val saved = (stored as? Map<*, *>)
            ?.mapNotNull { (k, v) -> (v as? Boolean)?.let { k.toString() to it } }
            ?.toMap() ?: emptyMap()
        return defaultPermissions(role) + saved
    }

    // ==============================================================
    // LÓGICA DE FUMAÇA (ESCALA RINGELMANN)
    // ==============================================================
    fun avaliarFumaca(nivelRingelmann: Int): String {
        // Exatamente como no JS: Nível 3, 4 ou 5 Reprova. 1 ou 2 Aprova.
        return if (nivelRingelmann >= 3) "Reprovado" else "Aprovado"
    }

    // ==============================================================
    // NEXCHECK IA V12 - "MUNDO REAL" (TRADUZIDO DO JS)
    // ==============================================================
    fun predictCauseFromReport(text: String?): String {
        if (text == null || text.length < 3) return "A ANALISAR"

        // 1. Limpeza: Remove acentos e deixa tudo maiúsculo
        val normalizedText = Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
            .uppercase()

        // 2. DICIONÁRIO DE PESOS
        val dictionary = mapOf(
            "SISTEMA DE TRANSMISSÃO" to listOf(
                Pair("CAIXA DE MARCHA", 1000), Pair("CAIXA DE MACHA", 1000),
                Pair("NAO PASSA MARCHA", 900), Pair("NAO PASSA MACHA", 900),
                Pair("CAMBIO", 800), Pair("EMBREAGEM", 800), Pair("EMBREAGEN", 800),
                Pair("PEDAL MOLE", 500), Pair("CARDAN", 800), Pair("CARDAM", 800),
                Pair("CRUZETA", 800), Pair("DIFERENCIAL", 900), Pair("EIXO PILOTO", 600),
                Pair("PATINANDO", 500), Pair("ARRANHANDO", 500), Pair("TRAMBULADOR", 700),
                Pair("MARCHA", 300), Pair("MACHA", 300)
            ),
            "PANE ELÉTRICA - PARTIDA" to listOf(
                Pair("NAO PEGA", 900), Pair("NAO LIGA", 800), Pair("NAO DA PARTIDA", 900),
                Pair("MOTOR DE ARRANQUE", 1000), Pair("MOTOR DE ARANQUE", 1000),
                Pair("ARRANQUE", 800), Pair("ARANQUE", 800), Pair("PARTIDA", 600),
                Pair("BATERIA", 800), Pair("ARRIOU", 500), Pair("CHAVE GERAL", 500),
                Pair("ALTERNADOR", 800), Pair("NAO CARREGA", 700),
                Pair("DESLIGOU SOZINHO", 600), Pair("MODULO", 600), Pair("PAINEL APAGOU", 600)
            ),
            "FALHA NO SISTEMA DE COMBUSTÍVEL" to listOf(
                Pair("ENTRADA DE AR", 2000), Pair("FALTA DE COMBUSTIVEL", 1000),
                Pair("PANE SECA", 1000), Pair("SEM DIESEL", 1000),
                Pair("BOMBA INJETORA", 800), Pair("BICO", 500), Pair("INJETOR", 500),
                Pair("RACOR", 800), Pair("FILTRO DE COMBUSTIVEL", 600), Pair("TANQUE", 400),
                Pair("AR NA LINHA", 1500), Pair("PESCADOR", 500), Pair("BOIA", 500)
            ),
            "SISTEMA PNEUMÁTICO (AR)" to listOf(
                Pair("VAZAMENTO DE AR", 1000), Pair("VAZANDO AR", 1000), Pair("MANGUEIRA", 400),
                Pair("CUICA", 900), Pair("CUICAO", 900), Pair("MANECO", 900), Pair("VALVULA", 500),
                Pair("COMPRESSOR", 800), Pair("SECADOR", 600), Pair("APU", 600),
                Pair("BOLSA DE AR", 900), Pair("BOLSA ESTOUROU", 1000), Pair("NIVELADORA", 500),
                Pair("PEDAL DURO", 500), Pair("TRAVOU", 300), Pair("FREIO DE MOLA", 600)
            ),
            "FALHA NO MOTOR" to listOf(
                Pair("FERVEU", 1000), Pair("AQUECEU", 1000), Pair("RADIADOR", 800), Pair("AGUA", 300),
                Pair("MANGOTE", 500), Pair("CORREIA", 600), Pair("COREIA", 600),
                Pair("TURBINA", 800), Pair("INTERCOOLER", 700), Pair("CABECOTE", 800),
                Pair("VIBRANDO", 400), Pair("FUMACA", 500), Pair("OLEO", 400), Pair("CARTER", 500),
                Pair("DESINCRONIZACAO", 900), Pair("MOTOR BATENDO", 900)
            ),
            "ACIDENTE DE TRÂNSITO" to listOf(
                Pair("COLISAO", 1000), Pair("BATIDA", 1000), Pair("BATEU", 900), Pair("CHOQUE", 800),
                Pair("ABALROAMENTO", 900), Pair("ATINGIU", 600), Pair("ENCOSTOU", 400),
                Pair("TRASEIRA", 500), Pair("FRONTAL", 500), Pair("LATERAL", 500),
                Pair("ULTRAPASSAGEM", 600), Pair("FECHADA", 500), Pair("CONTRAMAO", 600),
                Pair("VEICULO DE PASSEIO", 400), Pair("MOTO", 400), Pair("CARRO", 300)
            ),
            "ACIDENTE - TOMBAMENTO/SAÍDA" to listOf(
                Pair("TOMBOU", 1000), Pair("TOMBAMENTO", 1000), Pair("VIROU", 800), Pair("CAPOTOU", 1000),
                Pair("SAIU DA PISTA", 900), Pair("SAIDA DE PISTA", 900), Pair("BARRANCO", 800),
                Pair("CANTEIRO", 700), Pair("MEIO FIO", 600), Pair("PERDEU CONTROLE", 700),
                Pair("CHICOTEOU", 900), Pair("FEZ L", 800), Pair("L NA PISTA", 800)
            ),
            "INCIDENTE COM CARGA" to listOf(
                Pair("QUEDA DE FARDO", 1000), Pair("CAIU FARDO", 1000), Pair("FARDO", 500),
                Pair("CARGA SOLTA", 800), Pair("CINTA", 400), Pair("CATRACA", 400),
                Pair("SOLTOU O RODEIO", 1000), Pair("RODEIO", 800), Pair("QUINTA RODA", 600),
                Pair("PINO REI", 600), Pair("DESENGATOU", 700)
            ),
            "FALHA DE PNEUS" to listOf(
                Pair("PNEU", 800), Pair("FURADO", 800), Pair("FUROU", 800), Pair("ESTOUROU", 800),
                Pair("RASGOU", 700), Pair("ARAME", 600), Pair("RECAPAGEM", 500),
                Pair("SOLTOU A BANDA", 900), Pair("ESTEPE", 600), Pair("STEP", 600)
            ),
            "SSMA - INFRAÇÃO GRAVÍSSIMA" to listOf(
                Pair("ALCOOL", 2000), Pair("ETILOMETRO", 2000), Pair("BAFOMETRO", 2000),
                Pair("EMBRIAGUEZ", 2000), Pair("RECUSOU", 1500), Pair("RECUSA", 1500),
                Pair("POSITIVO", 1000), Pair("DROGA", 2000), Pair("BEBIDA", 1500)
            ),
            "SEGURANÇA PATRIMONIAL" to listOf(
                Pair("PORTARIA", 100), Pair("VIGILANTE", 100), Pair("CNH", 500),
                Pair("DOCUMENTO", 400), Pair("BRIGA", 1000), Pair("AMEACA", 1000),
                Pair("AGRESSAO", 1000), Pair("BLOQUEIO", 500), Pair("CRACHA", 400)
            )
        )

        val scores = mutableMapOf<String, Int>()
        var bestCategory = "MANUTENÇÃO GERAL / A ANALISAR"
        var highestScore = 0

        for ((category, terms) in dictionary) {
            scores[category] = 0
            for ((term, weight) in terms) {
                if (normalizedText.contains(term)) {
                    scores[category] = scores.getOrDefault(category, 0) + weight
                }
            }
        }

        // REGRAS DE DESEMPATE (O Segredo do Sucesso)
        if (normalizedText.contains("ARRANQUE") || normalizedText.contains("PARTIDA")) {
            scores["FALHA NO MOTOR"] = 0
        }
        if (normalizedText.contains("ENTRADA DE AR")) {
            scores["SISTEMA PNEUMÁTICO (AR)"] = 0
        }

        for ((category, score) in scores) {
            if (score > highestScore) {
                highestScore = score
                bestCategory = category
            }
        }

        if (highestScore < 50) return "A ANALISAR / OUTROS"
        return bestCategory
    }
}