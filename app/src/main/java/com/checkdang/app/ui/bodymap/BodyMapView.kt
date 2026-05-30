package com.checkdang.app.ui.bodymap

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Region
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.checkdang.app.R
import com.checkdang.app.data.model.BodyPart
import com.checkdang.app.data.model.BodyView

/**
 * 인체 실루엣(PNG) 기반 2D 바디맵 커스텀 뷰.
 *
 * - 좌표계: viewBox 0 0 200 400 (확정 자산 기준)
 * - 인체 이미지 배치: x=18.87, y=-2.04, w=162.79, h=403.56 (자산 핸드오프 상수)
 * - 부위는 **해부학적 다각형(폴리곤)** 으로 정의 — 사각형이 아니라 실제 부위 형태를 따른다.
 *   (어깨↔팔 사선, 몸통↔팔 간격, 좌우 다리 갈림 등을 반영)
 * - 점등은 폴리곤을 그린 뒤 **인체 실루엣 알파로 마스킹**(PorterDuff DST_IN)되어 몸 밖으로 번지지 않는다.
 * - 좌/우 = 인체 자신 기준 (의학적 관례). 정면: 인체 LEFT = 화면 오른쪽(x>100)
 *
 * 정면/후면 전환: setBodyView() · 선택 부위: selectedPart · 클릭 콜백: onPartSelected
 */
class BodyMapView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var onPartSelected: ((BodyPart) -> Unit)? = null

    private var currentView: BodyView = BodyView.FRONT
    var selectedPart: BodyPart? = null
        private set

    // ── 인체 이미지 (회색 실루엣, 투명 배경) ───────────────────────────────
    private val decodeOpts = BitmapFactory.Options().apply { inSampleSize = 2 }
    private val frontBitmap: Bitmap by lazy {
        BitmapFactory.decodeResource(resources, R.drawable.bodymap_grey_front, decodeOpts)
    }
    private val backBitmap: Bitmap by lazy {
        BitmapFactory.decodeResource(resources, R.drawable.bodymap_grey_back, decodeOpts)
    }
    private fun currentBitmap() = if (currentView == BodyView.FRONT) frontBitmap else backBitmap

    // ── Paints ─────────────────────────────────────────────────────────────
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
    private val highlightFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.brand_green); alpha = 100; style = Paint.Style.FILL
    }
    private val highlightStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.brand_green); alpha = 200
        style = Paint.Style.STROKE; strokeWidth = 2.5f
    }
    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = true; xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }

    // ── 부위 폴리곤: viewBox(200×400) 좌표의 x,y 쌍 평면 배열 ─────────────────
    // 정면 16부위 — 인체 LEFT = 화면 오른쪽(x>100)
    private val frontPolys: Map<BodyPart, FloatArray> = linkedMapOf(
        BodyPart.HEAD                 to floatArrayOf(84f,6f, 116f,6f, 120f,30f, 113f,54f, 100f,60f, 87f,54f, 80f,30f),
        BodyPart.NECK_FRONT           to floatArrayOf(88f,54f, 112f,54f, 114f,67f, 86f,67f),
        BodyPart.RIGHT_SHOULDER_FRONT to floatArrayOf(85f,61f, 68f,63f, 55f,71f, 47f,89f, 47f,120f, 67f,124f, 77f,102f, 82f,80f),
        BodyPart.LEFT_SHOULDER_FRONT  to floatArrayOf(115f,61f, 132f,63f, 145f,71f, 153f,89f, 153f,120f, 133f,124f, 123f,102f, 118f,80f),
        BodyPart.CHEST                to floatArrayOf(84f,66f, 116f,66f, 122f,110f, 124f,150f, 76f,150f, 78f,110f),
        BodyPart.RIGHT_ARM_FRONT      to floatArrayOf(47f,116f, 67f,122f, 61f,165f, 49f,205f, 48f,222f, 31f,231f, 27f,210f, 34f,176f, 43f,140f),
        BodyPart.LEFT_ARM_FRONT       to floatArrayOf(153f,116f, 133f,122f, 139f,165f, 151f,205f, 152f,222f, 169f,231f, 173f,210f, 166f,176f, 157f,140f),
        BodyPart.ABDOMEN              to floatArrayOf(76f,150f, 124f,150f, 130f,196f, 70f,196f),
        BodyPart.RIGHT_HIP_FRONT      to floatArrayOf(70f,196f, 100f,196f, 100f,230f, 61f,228f, 62f,205f),
        BodyPart.LEFT_HIP_FRONT       to floatArrayOf(100f,196f, 130f,196f, 138f,205f, 139f,228f, 100f,230f),
        BodyPart.RIGHT_THIGH_FRONT    to floatArrayOf(60f,228f, 100f,228f, 91f,300f, 59f,300f),
        BodyPart.LEFT_THIGH_FRONT     to floatArrayOf(100f,228f, 141f,228f, 146f,300f, 110f,300f),
        BodyPart.RIGHT_KNEE           to floatArrayOf(59f,300f, 89f,300f, 84f,326f, 58f,326f),
        BodyPart.LEFT_KNEE            to floatArrayOf(118f,300f, 146f,300f, 147f,326f, 122f,326f),
        BodyPart.RIGHT_SHIN           to floatArrayOf(58f,326f, 80f,326f, 74f,378f, 40f,396f, 54f,360f),
        BodyPart.LEFT_SHIN            to floatArrayOf(120f,326f, 146f,326f, 148f,360f, 160f,396f, 126f,378f),
    )

    // 후면 12부위 — 인체 LEFT = 화면 왼쪽(x<100)
    private val backPolys: Map<BodyPart, FloatArray> = linkedMapOf(
        BodyPart.HEAD                to floatArrayOf(84f,6f, 116f,6f, 120f,30f, 113f,54f, 100f,60f, 87f,54f, 80f,30f),
        BodyPart.NECK_BACK           to floatArrayOf(88f,54f, 112f,54f, 114f,67f, 86f,67f),
        BodyPart.LEFT_SHOULDER_BACK  to floatArrayOf(85f,61f, 68f,63f, 55f,71f, 47f,89f, 47f,120f, 67f,124f, 77f,102f, 82f,80f),
        BodyPart.RIGHT_SHOULDER_BACK to floatArrayOf(115f,61f, 132f,63f, 145f,71f, 153f,89f, 153f,120f, 133f,124f, 123f,102f, 118f,80f),
        BodyPart.UPPER_BACK          to floatArrayOf(84f,66f, 116f,66f, 124f,120f, 126f,168f, 74f,168f, 76f,120f),
        BodyPart.LOWER_BACK          to floatArrayOf(74f,168f, 126f,168f, 140f,210f, 139f,256f, 100f,256f, 61f,256f, 60f,210f),
        BodyPart.LEFT_ARM_FRONT      to floatArrayOf(47f,116f, 67f,122f, 61f,165f, 49f,205f, 46f,222f, 25f,233f, 22f,210f, 34f,176f, 43f,140f),
        BodyPart.RIGHT_ARM_FRONT     to floatArrayOf(153f,116f, 133f,122f, 139f,165f, 151f,205f, 154f,222f, 175f,233f, 178f,210f, 166f,176f, 157f,140f),
        BodyPart.LEFT_THIGH_FRONT    to floatArrayOf(60f,256f, 100f,256f, 92f,326f, 58f,326f),
        BodyPart.RIGHT_THIGH_FRONT   to floatArrayOf(100f,256f, 140f,256f, 147f,326f, 110f,326f),
        BodyPart.LEFT_SHIN           to floatArrayOf(58f,326f, 80f,326f, 74f,378f, 40f,396f, 54f,360f),
        BodyPart.RIGHT_SHIN          to floatArrayOf(120f,326f, 146f,326f, 148f,360f, 160f,396f, 126f,378f),
    )

    private fun currentPolys() = if (currentView == BodyView.FRONT) frontPolys else backPolys

    // 히트 테스트용 Region 캐시 (뷰 전환 시 재생성)
    private var hitRegions: Map<BodyPart, Pair<Region, Float>>? = null

    private val bodyDst = RectF()
    private val tmpPath = Path()

    fun setBodyView(view: BodyView) {
        if (currentView == view) return
        currentView = view
        selectedPart = null
        hitRegions = null
        invalidate()
    }

    fun clearSelection() {
        selectedPart = null
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, w * 2)   // viewBox 200:400 = 1:2
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val sx = w / VIEWBOX_W
        val sy = h / VIEWBOX_H

        val bitmap = currentBitmap()
        bodyDst.set(IMG_X * sx, IMG_Y * sy, (IMG_X + IMG_W) * sx, (IMG_Y + IMG_H) * sy)

        // 1) 회색 인체
        canvas.drawBitmap(bitmap, null, bodyDst, bodyPaint)

        // 2) 선택 부위 폴리곤 점등 — 인체 알파로 마스킹
        val part = selectedPart ?: return
        val poly = currentPolys()[part] ?: return

        buildScaledPath(poly, sx, sy, tmpPath)
        val layer = canvas.saveLayer(0f, 0f, w, h, null)
        canvas.drawPath(tmpPath, highlightFillPaint)
        canvas.drawPath(tmpPath, highlightStrokePaint)
        canvas.drawBitmap(bitmap, null, bodyDst, maskPaint)
        canvas.restoreToCount(layer)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_UP) return true
        val vx = (event.x / width * VIEWBOX_W * HIT_SCALE).toInt()
        val vy = (event.y / height * VIEWBOX_H * HIT_SCALE).toInt()

        // 겹치는 영역은 가장 작은(구체적인) 부위 우선
        val hit = ensureHitRegions().entries
            .filter { (_, rp) -> rp.first.contains(vx, vy) }
            .minByOrNull { (_, rp) -> rp.second }
            ?.key

        if (hit != null) {
            selectedPart = hit
            invalidate()
            performClick()
            onPartSelected?.invoke(hit)
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun ensureHitRegions(): Map<BodyPart, Pair<Region, Float>> {
        hitRegions?.let { return it }
        val clip = Region(0, 0, (VIEWBOX_W * HIT_SCALE).toInt(), (VIEWBOX_H * HIT_SCALE).toInt())
        val map = LinkedHashMap<BodyPart, Pair<Region, Float>>()
        for ((part, poly) in currentPolys()) {
            val p = Path()
            p.moveTo(poly[0] * HIT_SCALE, poly[1] * HIT_SCALE)
            var i = 2
            while (i < poly.size) { p.lineTo(poly[i] * HIT_SCALE, poly[i + 1] * HIT_SCALE); i += 2 }
            p.close()
            val region = Region().apply { setPath(p, Region(clip)) }
            map[part] = region to polygonArea(poly)
        }
        hitRegions = map
        return map
    }

    private fun buildScaledPath(poly: FloatArray, sx: Float, sy: Float, out: Path) {
        out.rewind()
        out.moveTo(poly[0] * sx, poly[1] * sy)
        var i = 2
        while (i < poly.size) { out.lineTo(poly[i] * sx, poly[i + 1] * sy); i += 2 }
        out.close()
    }

    private fun polygonArea(poly: FloatArray): Float {
        var area = 0f
        var j = poly.size - 2
        var i = 0
        while (i < poly.size) {
            area += (poly[j] + poly[i]) * (poly[j + 1] - poly[i + 1])
            j = i; i += 2
        }
        return kotlin.math.abs(area / 2f)
    }

    companion object {
        private const val VIEWBOX_W = 200f
        private const val VIEWBOX_H = 400f
        private const val IMG_X = 18.87f
        private const val IMG_Y = -2.04f
        private const val IMG_W = 162.79f
        private const val IMG_H = 403.56f
        private const val HIT_SCALE = 5f   // Region 정수 좌표 정밀도용 (1000×2000)
    }
}
