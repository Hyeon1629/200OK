package com.checkdang.app.ui.legal

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.checkdang.app.databinding.ActivityLegalDocumentBinding
import io.noties.markwon.Markwon
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.linkify.LinkifyPlugin

/**
 * 법적 문서(이용약관 / 개인정보처리방침 등) 조회 화면.
 *
 * 호출 측은 [start] / [agreementIntent] 빌더로 인텐트를 생성한다. 직접 Intent 를 만들기보다
 * 빌더를 통해 enum 안전성 + extra 누락 방지.
 *
 * 모드 분기:
 *  - [MODE_VIEW]      : 단순 조회. 메뉴/로그인 화면에서 진입.
 *  - [MODE_AGREEMENT] : Terms 한정 — 동의 액션 footer 노출. RESULT_OK 반환.
 *
 * Privacy 의 경우 mode 는 무시되며 항상 조회 모드 + 문의 footer.
 */
class LegalDocumentActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_DOCUMENT = "extra_document"
        const val EXTRA_MODE     = "extra_mode"
        const val MODE_VIEW      = "view"
        const val MODE_AGREEMENT = "agreement"

        /** 단순 조회 인텐트. 어디서든 호출. */
        fun intent(context: android.content.Context, doc: LegalDocument): Intent =
            Intent(context, LegalDocumentActivity::class.java)
                .putExtra(EXTRA_DOCUMENT, doc.name)
                .putExtra(EXTRA_MODE, MODE_VIEW)

        /** 동의 액션 인텐트 (Terms 전용). 회원가입 흐름에서 startActivityForResult 와 함께 사용. */
        fun agreementIntent(context: android.content.Context): Intent =
            Intent(context, LegalDocumentActivity::class.java)
                .putExtra(EXTRA_DOCUMENT, LegalDocument.TERMS.name)
                .putExtra(EXTRA_MODE, MODE_AGREEMENT)
    }

    private val binding by lazy { ActivityLegalDocumentBinding.inflate(layoutInflater) }
    private lateinit var document: LegalDocument

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        document = LegalDocument.fromName(intent.getStringExtra(EXTRA_DOCUMENT))

        setupToolbar()
        setupMeta()
        loadDocument()
        setupFooters()
    }

    private fun setupToolbar() {
        binding.toolbar.title = getString(document.titleRes)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupMeta() {
        binding.tvMeta.text = getString(document.metaTextRes)
        if (document.subtitleRes != 0) {
            binding.tvMetaSubtitle.text = getString(document.subtitleRes)
            binding.tvMetaSubtitle.visibility = View.VISIBLE
        } else {
            binding.tvMetaSubtitle.visibility = View.GONE
        }
    }

    private fun loadDocument() {
        val markdown = resources.openRawResource(document.rawResId)
            .bufferedReader(Charsets.UTF_8).use { it.readText() }

        val markwon = Markwon.builder(this)
            .usePlugin(LinkifyPlugin.create())
            .usePlugin(TablePlugin.create(this))
            .build()
        markwon.setMarkdown(binding.tvDocument, markdown)
    }

    private fun setupFooters() {
        val mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_VIEW

        // Terms 동의 footer
        if (document.showAgreementFooter && mode == MODE_AGREEMENT) {
            binding.footerAgreement.visibility = View.VISIBLE
            binding.btnAgree.setOnClickListener {
                setResult(RESULT_OK)
                finish()
            }
        } else {
            binding.footerAgreement.visibility = View.GONE
        }

        // Privacy 문의 footer
        if (document.showContactFooter) {
            binding.btnContact.visibility = View.VISIBLE
            binding.btnContact.setOnClickListener { openPrivacyContact() }
        } else {
            binding.btnContact.visibility = View.GONE
        }
    }

    /**
     * mailto: 인텐트로 이메일 앱 호출.
     * Android 11+ 의 `<queries>` 가시성 제한으로 `resolveActivity` 가 false negative 를 낼 수 있어,
     * 실제 호출 시점에 [runCatching] 으로 처리.
     */
    private fun openPrivacyContact() {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:" + getString(com.checkdang.app.R.string.legal_contact_email))
            putExtra(Intent.EXTRA_SUBJECT, getString(com.checkdang.app.R.string.legal_contact_subject))
        }
        runCatching { startActivity(intent) }.onFailure {
            Toast.makeText(
                this,
                getString(com.checkdang.app.R.string.legal_contact_fallback),
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
