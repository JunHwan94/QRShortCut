package com.devbearstudio.qrshortcut

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Message
import android.provider.Browser
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.webkit.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.devbearstudio.qrshortcut.databinding.ActivityMainBinding
import kotlinx.coroutines.*
import java.util.*

const val BASE_URL = "https://nid.naver.com/login/privacyQR"
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        initWebView()
    }

    override fun onResume() {
        super.onResume()
        binding.webView.loadUrl(BASE_URL)
    }

    private fun initWebView(){
        binding.webView.apply {
            settings.apply {
                javaScriptEnabled = true // 자바스크립트 동작 허용
                javaScriptCanOpenWindowsAutomatically = true // 자바스크립트 새창 띄우기 허용
                setSupportMultipleWindows(true) // 새 창 띄우기 허용
                loadWithOverviewMode = true // 메타태그 허용
                domStorageEnabled = true // 로컬 저장소 허용
            }
            webViewClient = newWebViewClient()
            webChromeClient = newWebChromeClient(context)
            addJavascriptInterface(MyJavascriptInterface(applicationContext), "Android")
        }
    }

    private fun loadNaver(url: String){
//        Log.d("URL", url)
        val intent = applicationContext.packageManager.getLaunchIntentForPackage("com.nhn.android.search")
        // 앱 없으면 플레이스토어 이동
        if(intent == null){
            val nurl = "market://details?id=com.nhn.android.search"
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(nurl)).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
            })
        }else{
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                putExtra(Browser.EXTRA_APPLICATION_ID, packageName)
            })
        }
    }

    private fun newWebViewClient(): WebViewClient{
        return object : WebViewClient(){
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
//                Log.d("URL", url)
                if(url?.contains("com?session") == true){ // 네이버 로그인 url일 때
                    loadNaver(url)
//                    Toast.makeText(baseContext, "appscheme", Toast.LENGTH_SHORT).show();
                }else{
                    view?.loadUrl(url)
                }
                return true
            }

            override fun onPageFinished(view: WebView?, url: String?) { // 정보 제공 동의
                super.onPageFinished(view, url)
                view?.loadUrl("javascript:window.Android.getTitle(document.querySelector('head > title').innerText)")
            }
        }
    }

    /**
     * 웹뷰의 alert를 안드로이드의 AlertDialog로 대체하는 메소드를 구현한
     * WebChromeCLient 객체를 반환하는 메소드
     * @param context : AlertDialog를 보여주기 위한 context
     * @return WebChromeClient 객체
     */
    private fun newWebChromeClient(context: Context): WebChromeClient? {
        return object : WebChromeClient() {
            //웹뷰에서 뜨는 url이 포함된 alert창을 없애고 AlertDialog로 대체
            override fun onJsAlert(view: WebView, url: String, message: String, result: JsResult): Boolean {
                AlertDialog.Builder(context)
                        .setTitle("")
                        .setMessage(message)
                        .setPositiveButton(android.R.string.ok) { dialog: DialogInterface?, which: Int -> result.confirm() }
                        .setCancelable(false)
                        .create()
                        .show()
                return true
            }

            //웹뷰에서 뜨는 url이 포함된 Confirm창을 없애고 안드로이드의 AlertDialog로 대체
            override fun onJsConfirm(view: WebView, url: String, message: String, result: JsResult): Boolean {
                AlertDialog.Builder(context)
                        .setTitle("")
                        .setMessage(message)
                        .setPositiveButton(android.R.string.ok) { dialog: DialogInterface?, which: Int -> result.confirm() }
                        .setNegativeButton(android.R.string.cancel) { dialog: DialogInterface?, which: Int -> result.cancel() }
                        .create()
                        .show()
                return true
            }

            /**
             * 웹 소셜 로그인 ( 웹뷰에서 팝업(브라우저에서 새창 여는 것) 실행 될 때 안에서 열리도록 처리 )
             **/
            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?
            ): Boolean {
//                Log.i("webview onCreateWindow", "called")
                // Dialog Create Code
                val newWebView = WebView(this@MainActivity)
                val webSettings = newWebView.settings
                webSettings.javaScriptEnabled = true

                val dialog = Dialog(this@MainActivity)
                dialog.setContentView(newWebView)

                val params: ViewGroup.LayoutParams = dialog.window?.attributes!!
                params.width = ViewGroup.LayoutParams.MATCH_PARENT
                params.height = ViewGroup.LayoutParams.MATCH_PARENT
                dialog.window?.attributes = params as WindowManager.LayoutParams
                dialog.show()
                newWebView.webChromeClient = object : WebChromeClient() {
                    override fun onCloseWindow(window: WebView) {
                        dialog.dismiss()
                    }
                }

                // WebView Popup에서 내용이 안보이고 빈 화면만 보여 아래 코드 추가
                newWebView.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest
                    ): Boolean {
                        return false
                    }
                }

                (resultMsg!!.obj as WebView.WebViewTransport).webView = newWebView
                resultMsg.sendToTarget()
                return true
                //                return super.onCreateWindow(view, isDialog, isUserGesture, resultMsg)
            }
        }
        // https://rhkdgus0779.tistory.com/23
    }

    class MyJavascriptInterface(val context: Context){
        @JavascriptInterface
        fun getTitle(title: String){
            if (title.contains("동의")) {
//                Log.d("head title", title)
                Toast.makeText(context, "브라우저에서 최초 개인정보 제공 동의 후 계속 사용할 수 있습니다", Toast.LENGTH_LONG).show()
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BASE_URL)).apply{
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                })
            }
        }
    }
}