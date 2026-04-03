package com.example.camara

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.example.camara.databinding.ActivityMainBinding
import com.google.firebase.messaging.FirebaseMessaging
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val PREFS_NAME = "AportecPrefs"
    private val FIRST_ACCESS_KEY = "isFirstAccess"
    private val BASE_URL = "https://dev.aporttec.com"

    // Lista de imagens de loading (substitua pelos seus drawables)
    private val loadingImages = listOf(
        R.drawable.loading1,
        R.drawable.loading2,
        R.drawable.loading3,
        R.drawable.loading4,
        R.drawable.loading5
    )

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d("FCM", "Permissão de notificação concedida")
        }
        // Após a permissão, inicia o carregamento normal
        startAppLogic()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWebView()
        setupBackButton()
        checkFirstAccess()

        // Botão de tentar novamente em caso de erro
        binding.btnRetry.setOnClickListener {
            startAppLogic()
        }

        // Verifica se veio de uma notificação com URL
        intent.getStringExtra("url")?.let {
            Log.d("FCM", "Abrindo via notificação com URL: $it")
            binding.webView.loadUrl(it)
        }
    }

    private fun checkFirstAccess() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isFirstAccess = prefs.getBoolean(FIRST_ACCESS_KEY, true)

        if (isFirstAccess) {
            showWelcomeDialog()
            prefs.edit().putBoolean(FIRST_ACCESS_KEY, false).apply()
        } else {
            startAppLogic()
        }
    }

    private fun showWelcomeDialog() {
        AlertDialog.Builder(this)
            .setTitle("Bem-vindo ao Aportec!")
            .setMessage("Gostaria de receber notificações importantes, promoções e atualizações do Aportec?\nÉ rápido e você pode mudar depois nas configurações.")
            .setPositiveButton("Sim, quero receber") { _, _ ->
                askNotificationPermission()
            }
            .setNegativeButton("Agora não") { _, _ ->
                startAppLogic()
            }
            .setCancelable(false)
            .show()
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                startAppLogic()
            } else {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            startAppLogic()
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
                // Não esconder o loading aqui para dar tempo de renderizar
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
        
        // Log do Token FCM
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w("FCM", "Falha ao obter token", task.exception)
                return@addOnCompleteListener
            }
            Log.d("FCM", "Token atual: ${task.result}")
        }
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
