package com.nexcheck.app

import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date

// =========================================================================
// MODELOS HTML DOS RELATÓRIOS IMPRESSOS (A4 -> PDF)
// Mesma identidade visual do app: azul NexCheck, neutros slate, selos de status.
// =========================================================================

/** Escapa texto digitado pelo usuário para não quebrar o HTML. */
private fun esc(value: Any?): String = (value?.toString() ?: "")
    .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

private fun orDash(value: Any?): String = esc(value).ifBlank { "—" }

private fun fmt(date: Date?, pattern: String) = date?.let { SimpleDateFormat(pattern, BrLocale).format(it) } ?: "—"

private val ReportCss = """
    @page { size: A4; margin: 12mm 12mm 14mm 12mm; }
    * { box-sizing: border-box; -webkit-print-color-adjust: exact !important; print-color-adjust: exact !important; }
    body { font-family: 'Inter', 'Segoe UI', Roboto, Arial, sans-serif; color: #0f172a; margin: 0; font-size: 11px; line-height: 1.4; }

    /* Cabeçalho */
    .top { display: flex; align-items: center; gap: 16px; padding-bottom: 14px; border-bottom: 2px solid #e2e8f0; }
    .brand { flex: 1; }
    .brand img { height: 42px; max-width: 190px; object-fit: contain; display: block; }
    .wordmark { font-size: 22px; font-weight: 800; letter-spacing: -0.5px; color: #0f172a; }
    .wordmark span { color: #2563eb; }
    .tagline { font-size: 8px; font-weight: 700; letter-spacing: 1.6px; color: #64748b; text-transform: uppercase; margin-top: 2px; }
    .title { flex: 1.4; text-align: center; }
    .title h1 { font-size: 15px; font-weight: 800; margin: 0; text-transform: uppercase; letter-spacing: 0.4px; }
    .title .sub { font-size: 9.5px; color: #64748b; margin-top: 4px; }
    .doc-no { display: inline-block; margin-top: 6px; padding: 3px 10px; border-radius: 999px; background: #eff5ff; color: #1d4ed8; font-weight: 800; font-size: 11px; }
    .verify { flex: 1; display: flex; justify-content: flex-end; align-items: center; gap: 10px; }
    .verify .qr { width: 62px; height: 62px; border: 1px solid #e2e8f0; border-radius: 6px; padding: 3px; }
    .verify .qr-label { font-size: 8px; color: #64748b; text-align: right; line-height: 1.3; }

    /* Faixa de status */
    .status { margin: 14px 0; border-radius: 10px; padding: 12px 16px; display: flex; align-items: center; justify-content: space-between; }
    .status.ok { background: #dcfce7; border: 1.5px solid #86efac; color: #15803d; }
    .status.bad { background: #fee2e2; border: 1.5px solid #fca5a5; color: #b91c1c; }
    .status .label { font-size: 9px; font-weight: 700; letter-spacing: 1.2px; text-transform: uppercase; opacity: 0.85; }
    .status .value { font-size: 20px; font-weight: 800; text-transform: uppercase; letter-spacing: 0.5px; }
    .status .counts { text-align: right; font-size: 10px; font-weight: 600; }
    .status .counts b { font-size: 13px; }

    /* Blocos de informação */
    .section-title { font-size: 9px; font-weight: 800; color: #64748b; letter-spacing: 1.4px; text-transform: uppercase; margin: 16px 0 8px; }
    .info { display: grid; grid-template-columns: repeat(3, 1fr); gap: 1px; background: #e2e8f0; border: 1px solid #e2e8f0; border-radius: 10px; overflow: hidden; break-inside: avoid; }
    .info > div { background: #fff; padding: 9px 12px; }
    .info .k { display: block; font-size: 8px; font-weight: 700; color: #64748b; text-transform: uppercase; letter-spacing: 0.6px; }
    .info .v { display: block; font-size: 11.5px; font-weight: 700; color: #0f172a; margin-top: 2px; }
    .info .v.accent { color: #2563eb; }

    /* Placa Mercosul */
    .plate { display: inline-block; border: 1.5px solid #1e293b; border-radius: 5px; overflow: hidden; text-align: center; background: #fff; vertical-align: middle; }
    .plate .band { background: #1d4ed8; color: #fff; font-size: 5.5px; font-weight: 700; letter-spacing: 1px; padding: 1px 0; }
    .plate .num { font-size: 13px; font-weight: 800; letter-spacing: 1.5px; padding: 1px 8px; color: #0f172a; }

    /* Checklist */
    .grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
    .card { border: 1px solid #e2e8f0; border-radius: 10px; overflow: hidden; break-inside: avoid; page-break-inside: avoid; }
    .card-head { display: flex; justify-content: space-between; align-items: center; background: #f6f8fb; padding: 7px 10px; border-bottom: 1px solid #e2e8f0; }
    .card-head .name { font-weight: 800; font-size: 10.5px; }
    .row { display: flex; justify-content: space-between; align-items: center; gap: 8px; padding: 5px 10px; border-bottom: 1px solid #f1f5f9; font-size: 10px; }
    .row:last-child { border-bottom: 0; }
    .row.fail { background: #fef2f2; font-weight: 700; color: #991b1b; }
    .pill { display: inline-block; padding: 2px 8px; border-radius: 999px; font-size: 8px; font-weight: 800; letter-spacing: 0.4px; text-transform: uppercase; white-space: nowrap; }
    .pill.ok { background: #dcfce7; color: #15803d; }
    .pill.bad { background: #fee2e2; color: #b91c1c; }
    .pill.na { background: #f1f5f9; color: #64748b; }

    /* Conclusão */
    .notes { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; break-inside: avoid; }
    .note { border-radius: 10px; padding: 10px 12px; border: 1px solid #e2e8f0; background: #f8fafc; }
    .note.danger { background: #fef2f2; border-color: #fecaca; }
    .note h4 { margin: 0 0 4px; font-size: 9px; letter-spacing: 1px; text-transform: uppercase; color: #475569; }
    .note.danger h4 { color: #b91c1c; }
    .note p { margin: 0; white-space: pre-wrap; word-wrap: break-word; font-size: 10.5px; }
    .note.danger p { color: #991b1b; font-weight: 700; }

    /* Assinaturas */
    .signs { display: grid; grid-template-columns: 1fr 1fr; gap: 40px; margin-top: 26px; break-inside: avoid; }
    .sign { text-align: center; }
    .sign .img { height: 56px; display: flex; align-items: flex-end; justify-content: center; }
    .sign img { max-height: 56px; max-width: 100%; object-fit: contain; }
    .sign .empty { color: #94a3b8; font-size: 9px; font-style: italic; }
    .sign .line { border-top: 1px solid #94a3b8; margin-top: 6px; padding-top: 5px; }
    .sign .who { font-size: 10px; font-weight: 700; }
    .sign .role { font-size: 8px; color: #64748b; text-transform: uppercase; letter-spacing: 1px; }

    .declaration { margin-top: 14px; font-size: 8.5px; color: #64748b; text-align: center; font-style: italic; }
    .footer { margin-top: 18px; padding-top: 8px; border-top: 1px solid #e2e8f0; display: flex; justify-content: space-between; font-size: 8px; color: #94a3b8; }
"""

private fun page(title: String, body: String) = """
<!DOCTYPE html>
<html lang="pt-BR"><head><meta charset="utf-8"><title>${esc(title)}</title><style>$ReportCss</style></head>
<body>$body</body></html>
""".trimIndent()

private fun brandHtml(logoBase64: String?) =
    if (!logoBase64.isNullOrBlank()) "<img src='$logoBase64' alt='Logo'>"
    else "<div class='wordmark'>Nex<span>Check</span></div><div class='tagline'>Gestão de frota</div>"

private fun plateHtml(plate: Any?) =
    "<span class='plate'><div class='band'>BRASIL</div><div class='num'>${orDash(plate)}</div></span>"

private fun infoCell(label: String, value: String, accent: Boolean = false, raw: Boolean = false) =
    "<div><span class='k'>$label</span><span class='v${if (accent) " accent" else ""}'>${if (raw) value else orDash(value)}</span></div>"

private fun pill(status: String): String {
    val s = status.trim().lowercase()
    return when (s) {
        "bom", "ok" -> "<span class='pill ok'>${esc(status)}</span>"
        "ruim", "nok" -> "<span class='pill bad'>${esc(status)}</span>"
        else -> "<span class='pill na'>N/A</span>"
    }
}

private fun signature(img: String?, who: String, role: String): String {
    val picture = if (!img.isNullOrBlank() && img.length > 10) "<img src='$img' alt='Assinatura'>" else "<span class='empty'>Sem assinatura</span>"
    return "<div class='sign'><div class='img'>$picture</div><div class='line'><div class='who'>${orDash(who)}</div><div class='role'>$role</div></div></div>"
}

private fun footer(docLabel: String) =
    "<div class='footer'><span>$docLabel</span><span>Documento gerado eletronicamente pelo NexCheck em ${fmt(Date(), "dd/MM/yyyy 'às' HH:mm")}</span></div>"

// =========================================================================
// RELATÓRIO DE INSPEÇÃO ELETROMECÂNICA
// =========================================================================

fun buildInspectionReportHtml(data: Map<String, Any>, companyLogoBase64: String?): String {
    val info = data["vehicleInfo"] as? Map<*, *>
    val vehicleType = data["vehicleType"]?.toString() ?: ""
    val isCavalo = vehicleType.contains("Cavalo")
    val status = data["status"]?.toString() ?: "Pendente"
    val isLiberado = status == "Liberado" || status == "Aprovado"
    val num = data["inspectionNumber"]?.toString() ?: "S/N"
    val inspectionId = data["id"]?.toString() ?: ""
    val plate = if (isCavalo) info?.get("placa") else info?.get("composicao1")

    val qrUrl = "https://api.qrserver.com/v1/create-qr-code/?size=160x160&margin=0&data=" +
        URLEncoder.encode("https://app.nexcheck.com/?public_verify=$inspectionId", "UTF-8")

    // Contagem dos itens
    val itemsMap = data["items"] as? Map<*, *>
    val allStatuses = itemsMap?.values?.flatMap { ((it as? Map<*, *>)?.get("items") as? Map<*, *>)?.values ?: emptyList() }
        ?.map { it.toString().lowercase() } ?: emptyList()
    val bons = allStatuses.count { it == "bom" }
    val ruins = allStatuses.count { it == "ruim" }
    val nas = allStatuses.size - bons - ruins

    val vehicleCells = if (isCavalo) {
        infoCell("Placa", plateHtml(info?.get("placa")), raw = true) +
            infoCell("Marca / modelo", listOf(info?.get("marca"), info?.get("modelo")).mapNotNull { it?.toString()?.ifBlank { null } }.joinToString(" / ")) +
            infoCell("Hodômetro", info?.get("km")?.toString()?.ifBlank { null }?.let { "$it km" } ?: "") +
            infoCell("Motorista", data["driverName"]?.toString() ?: "") +
            infoCell("Transportadora", data["company"]?.toString() ?: "") +
            infoCell("Próxima inspeção", fmt(data["nextInspectionDate"].asDate(), "dd/MM/yyyy"), accent = true)
    } else {
        infoCell("1ª composição", plateHtml(info?.get("composicao1")), raw = true) +
            infoCell("2ª composição", info?.get("composicao2")?.toString() ?: "") +
            infoCell("3ª composição", info?.get("composicao3")?.toString() ?: "") +
            infoCell("Cavalo vinculado", info?.get("linkedCavaloPlate")?.toString()?.ifBlank { null } ?: "Não informado") +
            infoCell("Transportadora", data["company"]?.toString() ?: "") +
            infoCell("Cliente", data["clientName"]?.toString() ?: "")
    }
    val inspectionCells =
        infoCell("Data da vistoria", fmt(data["inspectionDate"].asDate(), "dd/MM/yyyy 'às' HH:mm")) +
            infoCell("Tipo", data["inspectionType"]?.toString() ?: "Vistoria") +
            infoCell("Vistoriador", data["inspectorName"]?.toString() ?: "")

    val sectionsHtml = StringBuilder()
    itemsMap?.forEach { (_, sectionObj) ->
        val section = sectionObj as? Map<*, *> ?: return@forEach
        val inner = section["items"] as? Map<*, *>
        if (inner.isNullOrEmpty()) return@forEach
        val hasRuim = inner.values.any { it.toString().lowercase() == "ruim" }
        sectionsHtml.append("<div class='card'><div class='card-head'><span class='name'>${orDash(section["title"])}</span>")
        sectionsHtml.append(if (hasRuim) "<span class='pill bad'>Reprovado</span>" else "<span class='pill ok'>Aprovado</span>")
        sectionsHtml.append("</div>")
        inner.forEach { (name, st) ->
            val s = st.toString()
            sectionsHtml.append("<div class='row${if (s.lowercase() == "ruim") " fail" else ""}'><span>${esc(name)}</span>${pill(s)}</div>")
        }
        sectionsHtml.append("</div>")
    }

    val signatureLabel = if (isCavalo) "Motorista" else "Responsável"
    val body = """
        <div class='top'>
            <div class='brand'>${brandHtml(companyLogoBase64)}</div>
            <div class='title'>
                <h1>Relatório de Inspeção</h1>
                <div class='sub'>${esc(data["inspectionType"] ?: "Vistoria")} · ${esc(vehicleType.replace("Inspeção Eletromecânica - ", "").ifBlank { "Eletromecânica" })}</div>
                <div class='doc-no'>Nº $num</div>
            </div>
            <div class='verify'>
                <div class='qr-label'>Validação<br>digital</div>
                <img class='qr' src='$qrUrl' alt='QR Code'>
            </div>
        </div>

        <div class='status ${if (isLiberado) "ok" else "bad"}'>
            <div><div class='label'>Resultado da inspeção</div><div class='value'>${esc(status)}</div></div>
            <div class='counts'><b>${allStatuses.size}</b> itens verificados<br>$bons bom · $ruins ruim · $nas N/A</div>
        </div>

        <div class='section-title'>Veículo</div>
        <div class='info'>$vehicleCells</div>

        <div class='section-title'>Inspeção</div>
        <div class='info'>$inspectionCells</div>

        <div class='section-title'>Itens vistoriados</div>
        <div class='grid'>$sectionsHtml</div>

        <div class='section-title'>Conclusão</div>
        <div class='notes'>
            <div class='note'><h4>Observações gerais</h4><p>${esc(data["observations"]).ifBlank { "Nenhuma observação." }}</p></div>
            <div class='note ${if (ruins > 0 || !isLiberado) "danger" else ""}'><h4>Serviços / pendências</h4><p>${esc(data["servicesNeeded"]).ifBlank { "Nenhum apontamento." }}</p></div>
        </div>

        <div class='signs'>
            ${signature(data["driverSignature"]?.toString(), if (isCavalo) data["driverName"]?.toString() ?: "" else "", "Assinatura do $signatureLabel")}
            ${signature(data["inspectorSignature"]?.toString(), data["inspectorName"]?.toString() ?: "", "Assinatura do vistoriador")}
        </div>

        ${footer("Relatório de inspeção Nº $num")}
    """.trimIndent()

    return page("Relatório de Inspeção Nº $num", body)
}

// =========================================================================
// DIÁRIO DE BORDO (CHECKLIST PRÉ-CARREGAMENTO)
// =========================================================================

fun buildLogbookReportHtml(data: Map<String, Any>, logoBase64: String?): String {
    val num = data["logNumber"]?.toString() ?: "S/N"
    val status = data["status"]?.toString() ?: "Pendente"
    val isApproved = status == "Aprovado"
    val struct = data["checklistStruct"].asMapList()
    val allStatuses = struct.flatMap { s -> s["items"].asMapList().map { it["status"]?.toString()?.uppercase() ?: "NA" } }
    val ok = allStatuses.count { it == "OK" }
    val nok = allStatuses.count { it == "NOK" }
    val na = allStatuses.size - ok - nok

    val cells =
        infoCell("Placa", plateHtml(data["plate"]), raw = true) +
            infoCell("Composição", compositionLabel(data["compositionType"])) +
            infoCell("Carroceria", data["bodyType"]?.toString() ?: "SIDER") +
            infoCell("Motorista", data["driverName"]?.toString() ?: "") +
            infoCell("Transportadora", data["carrier"]?.toString() ?: "") +
            infoCell("Data", fmt(data["createdAt"].asDate(), "dd/MM/yyyy 'às' HH:mm"))

    val sectionsHtml = StringBuilder()
    struct.forEach { section ->
        val items = section["items"].asMapList()
        if (items.isEmpty()) return@forEach
        val hasNok = items.any { it["status"]?.toString()?.uppercase() == "NOK" }
        sectionsHtml.append("<div class='card'><div class='card-head'><span class='name'>${orDash(section["sectionTitle"])}</span>")
        sectionsHtml.append(if (hasNok) "<span class='pill bad'>Reprovado</span>" else "<span class='pill ok'>Aprovado</span>")
        sectionsHtml.append("</div>")
        items.forEach { itm ->
            val st = itm["status"]?.toString() ?: "NA"
            sectionsHtml.append("<div class='row${if (st.uppercase() == "NOK") " fail" else ""}'><span>${esc(itm["name"])}</span>${pill(st)}</div>")
        }
        sectionsHtml.append("</div>")
    }

    val failed = data["failedItems"].asStringList()
    val failedHtml = if (failed.isEmpty()) "" else
        "<div class='section-title'>Reprovações</div><div class='note danger'><p>${failed.joinToString("<br>") { "• ${esc(it)}" }}</p></div>"

    val body = """
        <div class='top'>
            <div class='brand'>${brandHtml(logoBase64)}</div>
            <div class='title'>
                <h1>Checklist Pré-Carregamento</h1>
                <div class='sub'>Diário de bordo</div>
                <div class='doc-no'>Nº $num</div>
            </div>
            <div class='verify'></div>
        </div>

        <div class='status ${if (isApproved) "ok" else "bad"}'>
            <div><div class='label'>Resultado da avaliação</div><div class='value'>${esc(status)}</div></div>
            <div class='counts'><b>${allStatuses.size}</b> itens verificados<br>$ok OK · $nok NOK · $na N/A</div>
        </div>

        <div class='section-title'>Veículo e operação</div>
        <div class='info'>$cells</div>

        $failedHtml

        <div class='section-title'>Itens verificados</div>
        <div class='grid'>$sectionsHtml</div>

        <div class='signs' style='grid-template-columns: 1fr; max-width: 320px; margin-left: auto; margin-right: auto;'>
            ${signature(data["signature"]?.toString(), data["driverName"]?.toString() ?: "", "Assinatura do motorista")}
        </div>
        <div class='declaration'>
            Declaro, para os devidos fins, que realizei pessoalmente a inspeção dos itens constantes neste checklist,
            atestando a integridade e veracidade das informações aqui prestadas.
        </div>

        ${footer("Diário de bordo Nº $num")}
    """.trimIndent()

    return page("Diário de Bordo Nº $num", body)
}
