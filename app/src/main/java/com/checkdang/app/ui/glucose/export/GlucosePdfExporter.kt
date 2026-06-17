package com.checkdang.app.ui.glucose.export

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.checkdang.app.data.model.GlucoseRecord
import com.checkdang.app.util.GlucoseEvaluator
import com.checkdang.app.util.GlucoseStatus
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 혈당 기록을 A4 PDF 리포트로 만들어 **공유(①)** 또는 **기기 저장(②)** 한다.
 *
 * - PDF 생성: [android.graphics.pdf.PdfDocument] 기본 API (외부 의존성 없음).
 * - ① 공유 : `cacheDir/reports` → [FileProvider] `content://` → `ACTION_SEND`.
 * - ② 저장 : API 29+ 는 [MediaStore] `Downloads/체크당` (권한 불요),
 *            API 26–28 은 레거시 `Downloads/체크당` (WRITE_EXTERNAL_STORAGE 필요 — 호출 측에서 사전 요청).
 *
 * 렌더링([render])은 출력 대상과 무관한 [OutputStream] 에 쓰므로 두 경로가 동일 코드를 공유한다.
 */
object GlucosePdfExporter {

    private const val PAGE_W = 595          // A4 @72dpi
    private const val PAGE_H = 842
    private const val MARGIN = 40f
    private const val BOTTOM_LIMIT = 792f   // 이 y 를 넘으면 다음 페이지

    // 표 컬럼 x (좌측 정렬 baseline)
    private const val COL_DATE = 44f
    private const val COL_TIME = 150f
    private const val COL_TIMING = 230f
    private const val COL_VALUE = 360f
    private const val COL_STATUS = 470f

    private val fileStamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.KOREAN)
    private fun fileName() = "혈당리포트_${fileStamp.format(Date())}.pdf"

    private fun authority(context: Context) = "${context.packageName}.fileprovider"

    // ── ① 공유 ──────────────────────────────────────────────────────────────
    /** cacheDir 에 PDF 를 쓰고 공유용 chooser [Intent] 를 만들어 반환한다. (파일 IO 포함 → 백그라운드에서 호출) */
    fun buildShareIntent(context: Context, records: List<GlucoseRecord>, nickname: String): Intent {
        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
        val file = File(dir, fileName())
        file.outputStream().use { render(records, nickname, it) }

        val uri = FileProvider.getUriForFile(context, authority(context), file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "${nickname}님 혈당 기록")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "혈당 리포트 공유")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    // ── ② 기기 저장 ─────────────────────────────────────────────────────────
    /** Downloads/체크당 에 저장하고 사용자에게 보여줄 경로 문자열을 반환(실패 시 null). 백그라운드에서 호출. */
    fun saveToDownloads(context: Context, records: List<GlucoseRecord>, nickname: String): String? = runCatching {
        val name = fileName()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/체크당")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
            resolver.openOutputStream(uri)?.use { render(records, nickname, it) } ?: return null
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } else {
            @Suppress("DEPRECATION")
            val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val dir = File(downloads, "체크당").apply { mkdirs() }
            File(dir, name).outputStream().use { render(records, nickname, it) }
        }
        "다운로드/체크당/$name"
    }.getOrNull()

    // ── PDF 렌더링 ────────────────────────────────────────────────────────────
    private fun render(records: List<GlucoseRecord>, nickname: String, out: OutputStream) {
        val dateFmt = SimpleDateFormat("yyyy.MM.dd", Locale.KOREAN)
        val timeFmt = SimpleDateFormat("HH:mm", Locale.KOREAN)
        val genFmt = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.KOREAN)

        val sorted = records.sortedByDescending { it.measuredAt }

        val title = paint("#1A1A1A", 22f, bold = true)
        val sub = paint("#6E6E73", 11f)
        val section = paint("#1A1A1A", 14f, bold = true)
        val body = paint("#1A1A1A", 11f)
        val bodySec = paint("#6E6E73", 11f)
        val linePaint = Paint().apply { color = Color.parseColor("#E5E5EA"); strokeWidth = 1f }
        val headerBg = Paint().apply { color = Color.parseColor("#F7F8FA") }

        val doc = PdfDocument()
        var pageNo = 1
        var page = doc.startPage(pageInfo(pageNo))
        var canvas = page.canvas

        fun drawTableHeader(yTop: Float): Float {
            canvas.drawRect(MARGIN, yTop - 13f, PAGE_W - MARGIN, yTop + 6f, headerBg)
            canvas.drawText("날짜", COL_DATE, yTop, bodySec)
            canvas.drawText("시간", COL_TIME, yTop, bodySec)
            canvas.drawText("측정유형", COL_TIMING, yTop, bodySec)
            canvas.drawText("수치", COL_VALUE, yTop, bodySec)
            canvas.drawText("상태", COL_STATUS, yTop, bodySec)
            return yTop + 22f
        }

        // === 헤더 ===
        var y = 60f
        canvas.drawText("혈당 기록 리포트", MARGIN, y, title)
        y += 22f
        canvas.drawText("${nickname}님 · 생성일 ${genFmt.format(Date())}", MARGIN, y, sub)
        y += 15f
        val period = if (sorted.isEmpty()) "기록 없음"
        else "${dateFmt.format(Date(sorted.last().measuredAt))} ~ ${dateFmt.format(Date(sorted.first().measuredAt))}"
        canvas.drawText("측정 기간: $period  ·  총 ${sorted.size}건", MARGIN, y, sub)
        y += 18f
        canvas.drawLine(MARGIN, y, PAGE_W - MARGIN, y, linePaint)
        y += 28f

        // === 요약 ===
        if (sorted.isNotEmpty()) {
            val avg = sorted.map { it.value }.average().toInt()
            val max = sorted.maxOf { it.value }
            val min = sorted.minOf { it.value }
            val normal = sorted.count { it.status == GlucoseStatus.NORMAL }
            val warning = sorted.count { it.status == GlucoseStatus.WARNING }
            val danger = sorted.count { it.status == GlucoseStatus.DANGER }
            canvas.drawText("요약", MARGIN, y, section)
            y += 20f
            canvas.drawText("평균 $avg · 최고 $max · 최저 $min  (mg/dL)", MARGIN, y, body)
            y += 16f
            canvas.drawText("정상 ${normal}건 · 주의 ${warning}건 · 위험 ${danger}건", MARGIN, y, bodySec)
            y += 28f
        }

        // === 측정 기록 표 ===
        canvas.drawText("측정 기록", MARGIN, y, section)
        y += 22f
        y = drawTableHeader(y)

        for (rec in sorted) {
            if (y > BOTTOM_LIMIT) {
                doc.finishPage(page)
                page = doc.startPage(pageInfo(++pageNo))
                canvas = page.canvas
                y = drawTableHeader(60f)
            }
            val statusPaint = when (rec.status) {
                GlucoseStatus.NORMAL -> paint("#4CAF50", 11f)
                GlucoseStatus.WARNING -> paint("#FF9800", 11f)
                GlucoseStatus.DANGER -> paint("#F44336", 11f, bold = true)
            }
            canvas.drawText(dateFmt.format(Date(rec.measuredAt)), COL_DATE, y, body)
            canvas.drawText(timeFmt.format(Date(rec.measuredAt)), COL_TIME, y, body)
            canvas.drawText(rec.timing.label, COL_TIMING, y, body)
            canvas.drawText("${rec.value}", COL_VALUE, y, body)
            canvas.drawText(GlucoseEvaluator.getStatusLabel(rec.status), COL_STATUS, y, statusPaint)
            y += 8f
            canvas.drawLine(MARGIN, y, PAGE_W - MARGIN, y, linePaint)
            y += 16f
        }
        if (sorted.isEmpty()) {
            canvas.drawText("표시할 혈당 기록이 없습니다.", MARGIN, y, bodySec)
        }

        // 면책 문구 (마지막 페이지 하단)
        canvas.drawText(
            "본 리포트는 사용자가 기록한 측정값을 정리한 자료이며 의학적 진단을 대체하지 않습니다.",
            MARGIN, 820f, paint("#6E6E73", 9f)
        )

        doc.finishPage(page)
        doc.writeTo(out)
        doc.close()
    }

    private fun pageInfo(n: Int) = PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, n).create()

    private fun paint(hex: String, size: Float, bold: Boolean = false) = Paint().apply {
        color = Color.parseColor(hex)
        textSize = size
        isAntiAlias = true
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }
}
