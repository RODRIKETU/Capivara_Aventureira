package com.bessasistema.camaracidada

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.webkit.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.bessasistema.camaracidada.databinding.ActivityMainBinding
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val BASE_URL = "https://dev.aporttec.com"

    // Lista de imagens de loading (substitua pelos seus drawables)
    private val loadingImages = listOf(
        R.drawable.loading1,
        R.drawable.loading2,
        R.drawable.loading3,
        R.drawable.loading4,
        R.drawable.loading5
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupWebView()
        setupBackButton()
        startAppLogic()

        // Botão de tentar novamente em caso de erro
        binding.btnRetry.setOnClickListener {
            startAppLogic()
        }

        // Verifica se veio de uma URL externa
        intent.getStringExtra("url")?.let {
            binding.webView.loadUrl(it)
        }
    }

    private fun startAppLogic() {
        if (isNetworkAvailable()) {
            binding.layoutError.visibility = View.GONE
            binding.layoutLoading.visibility = View.VISIBLE
            startLoadingAnimation()
            
            // Se já tiver uma URL carregando (via intent), não recarrega a base
            if (binding.webView.url == null) {
                binding.webView.loadUrl(BASE_URL)
            }
        } else {
            binding.layoutLoading.visibility = View.GONE
            binding.webView.visibility = View.GONE
            binding.layoutError.visibility = View.VISIBLE
        }
    }

    private fun startLoadingAnimation() {
        // Alterna imagens aleatoriamente usando Glide para transições suaves
        val timer = Timer()
        timer.schedule(object : TimerTask() {
            var count = 0
            override fun run() {
                runOnUiThread {
                    if (binding.layoutLoading.visibility == View.VISIBLE) {
                        Glide.with(this@MainActivity)
                            .load(loadingImages[count % loadingImages.size])
                            .into(binding.imgLoading)
                        count++
                    } else {
                        timer.cancel()
                    }
                }
            }
        }, 0, 1500) // Troca a cada 1.5 segundos
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val webSettings = binding.webView.settings
        webSettings.javaScriptEnabled = true
        webSettings.domStorageEnabled = true
        webSettings.useWideViewPort = true
        webSettings.loadWithOverviewMode = true
        webSettings.setSupportZoom(true)
        webSettings.builtInZoomControls = true
        webSettings.displayZoomControls = false
        webSettings.cacheMode = WebSettings.LOAD_DEFAULT

        binding.webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                binding.layoutLoading.visibility = View.GONE
                binding.webView.visibility = View.VISIBLE
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    binding.webView.visibility = View.GONE
                    binding.layoutError.visibility = View.VISIBLE
                }
            }
        }

        binding.webView.webChromeClient = object : WebChromeClient() {}
    }

    private fun setupBackButton() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.webView.canGoBack()) {
                    binding.webView.goBack()
                } else {
                    finish()
                }
            }
        })
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val nw = connectivityManager.activeNetwork ?: return false
        val actNw = connectivityManager.getNetworkCapabilities(nw) ?: return false
        return when {
            actNw.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
            actNw.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
            actNw.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
            else -> false
        }
    }
}
