package com.checkdang.app.data.remote

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.checkdang.app.data.mock.SessionHolder
import com.checkdang.app.data.model.GlucoseRecord

/**
 * 백엔드로 push 완료한 혈당 record ID 를 SharedPreferences 에 영속 기록한다.
 *
 * 혈당 탭에 진입할 때마다 [com.checkdang.app.ui.glucose.GlucoseViewModel.refresh] 가
 * 삼성헬스 90일치를 통째로 재전송해 서버에 중복 row 가 쌓이던 문제를 앱 측에서 차단한다.
 * 백엔드의 멱등 처리(`sourceId` 기반 upsert) 가 도입되기 전까지의 방어막.
 *
 * push 한 ID 는 `userId` 별로 네임스페이스 분리한다 — 한 단말에서 계정을 바꿔 로그인해도
 * 다른 사용자의 기록까지 "이미 전송됨"으로 잘못 걸러지지 않도록 한다.
 */
object GlucoseSyncStore {

    private const val TAG = "GlucoseSyncStore"
    private const val PREFS_NAME = "glucose_sync_store"

    private var prefs: SharedPreferences? = null

    /** Application.onCreate() 에서 [com.checkdang.app.data.mock.MockDataProvider.init] 와 함께 호출. */
    fun init(context: Context) {
        prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /** userId 별 키. 게스트(userId == null)는 어차피 push 가 스킵되지만 안전하게 분리. */
    private fun key(): String = "pushed_ids_${SessionHolder.userId ?: "guest"}"

    /** 아직 push 하지 않은 record 만 추려 반환. */
    fun filterUnsent(records: List<GlucoseRecord>): List<GlucoseRecord> {
        val sent = prefs?.getStringSet(key(), emptySet()).orEmpty()
        return records.filter { it.id !in sent }
    }

    /** push 성공한 record 의 ID 를 영속 기록. */
    fun markPushed(records: List<GlucoseRecord>) {
        val store = prefs ?: return
        if (records.isEmpty()) return
        // getStringSet 이 돌려준 캐시 인스턴스는 수정 금지 → 새 HashSet 으로 복사 후 저장.
        val updated = HashSet(store.getStringSet(key(), emptySet()).orEmpty())
        updated.addAll(records.map { it.id })
        store.edit().putStringSet(key(), updated).apply()
        Log.i(TAG, "markPushed: +${records.size} (total ${updated.size})")
    }
}
