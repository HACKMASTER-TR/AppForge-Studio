package com.appforge.studio.io

import android.content.Context
import com.appforge.studio.net.RemoteTemplate
import java.io.File

data class TemplateProjectResult(
    val projectDir: File,
    val startPage: File
)

object TemplateProjectFactory {

    fun materialize(
        context: Context,
        template: RemoteTemplate
    ): TemplateProjectResult? {

        val html =
            htmlFor(
                template.slug
            ) ?: return null

        val safeSlug =
            template.slug
                .lowercase()
                .replace(
                    Regex("[^a-z0-9_-]"),
                    "-"
                )
                .ifBlank {
                    "template"
                }

        val projectDir =
            File(
                context.filesDir,
                "projects/template_$safeSlug"
            )

        projectDir.deleteRecursively()
        projectDir.mkdirs()

        val startPage =
            File(
                projectDir,
                "index.html"
            )

        startPage.writeText(
            html,
            Charsets.UTF_8
        )

        return TemplateProjectResult(
            projectDir = projectDir,
            startPage = startPage
        )
    }


    private fun shell(
        title: String,
        subtitle: String,
        body: String,
        script: String = ""
    ): String =
        """
        <!doctype html>
        <html lang="tr">
        <head>
            <meta charset="utf-8">
            <meta name="viewport"
                  content="width=device-width,initial-scale=1,viewport-fit=cover">

            <title>$title</title>

            <style>
                :root {
                    color-scheme: dark;
                    --bg:#070b12;
                    --card:#101925;
                    --card2:#152131;
                    --text:#f5f7fb;
                    --muted:#99a6b8;
                    --accent:#80caff;
                    --good:#58e6a9;
                    --danger:#ff7387;
                    --border:#26364b;
                }

                * {
                    box-sizing:border-box;
                }

                body {
                    margin:0;
                    min-height:100vh;
                    font-family:
                        system-ui,
                        -apple-system,
                        BlinkMacSystemFont,
                        "Segoe UI",
                        sans-serif;
                    background:
                        radial-gradient(
                            circle at top,
                            #14243a 0,
                            var(--bg) 42%
                        );
                    color:var(--text);
                }

                main {
                    width:min(760px,100%);
                    margin:auto;
                    padding:
                        max(24px,env(safe-area-inset-top))
                        18px
                        max(36px,env(safe-area-inset-bottom));
                }

                header {
                    margin-bottom:22px;
                }

                .badge {
                    display:inline-block;
                    padding:7px 11px;
                    border-radius:999px;
                    background:#142b3d;
                    color:var(--accent);
                    font-size:12px;
                    font-weight:700;
                    margin-bottom:12px;
                }

                h1 {
                    margin:0;
                    font-size:30px;
                }

                p {
                    color:var(--muted);
                    line-height:1.55;
                }

                .grid {
                    display:grid;
                    grid-template-columns:
                        repeat(
                            auto-fit,
                            minmax(210px,1fr)
                        );
                    gap:12px;
                }

                .card {
                    background:
                        linear-gradient(
                            145deg,
                            var(--card2),
                            var(--card)
                        );
                    border:1px solid var(--border);
                    border-radius:20px;
                    padding:18px;
                    box-shadow:
                        0 14px 34px
                        rgba(0,0,0,.22);
                }

                .card h2,
                .card h3 {
                    margin-top:0;
                }

                button,
                .button {
                    width:100%;
                    border:0;
                    border-radius:14px;
                    padding:14px 16px;
                    margin-top:10px;
                    background:var(--accent);
                    color:#06111d;
                    font-weight:800;
                    font-size:14px;
                    cursor:pointer;
                }

                button.secondary {
                    background:#1e2d40;
                    color:var(--text);
                    border:1px solid #344a65;
                }

                button.danger {
                    background:var(--danger);
                }

                input,
                textarea {
                    width:100%;
                    padding:13px 14px;
                    background:#09111d;
                    color:var(--text);
                    border:1px solid var(--border);
                    border-radius:12px;
                    outline:none;
                    margin-top:8px;
                }

                .value {
                    margin-top:10px;
                    padding:12px;
                    border-radius:12px;
                    background:#080f18;
                    border:1px solid #1f3045;
                    color:var(--good);
                    overflow-wrap:anywhere;
                }

                .muted {
                    color:var(--muted);
                    font-size:13px;
                }

                .hero {
                    padding:22px;
                    border-radius:24px;
                    background:
                        linear-gradient(
                            135deg,
                            #13243a,
                            #101722
                        );
                    border:1px solid var(--border);
                    margin-bottom:14px;
                }

                canvas {
                    width:100%;
                    max-width:100%;
                    border-radius:18px;
                    background:#05080d;
                    border:1px solid var(--border);
                    touch-action:none;
                }

                .toast {
                    position:fixed;
                    left:50%;
                    bottom:30px;
                    transform:
                        translate(-50%,30px);
                    opacity:0;
                    background:#edf7ff;
                    color:#07101c;
                    padding:12px 18px;
                    border-radius:999px;
                    font-weight:700;
                    transition:.22s;
                    z-index:99;
                }

                .toast.show {
                    opacity:1;
                    transform:
                        translate(-50%,0);
                }
            </style>
        </head>

        <body>
            <main>
                <header>
                    <div class="badge">
                        AppForge Studio Şablonu
                    </div>

                    <h1>$title</h1>

                    <p>$subtitle</p>
                </header>

                $body
            </main>

            <div id="toast"
                 class="toast">
            </div>

            <script>
                function showToast(message) {
                    var el =
                        document.getElementById("toast");

                    el.textContent =
                        String(message);

                    el.classList.add("show");

                    setTimeout(
                        function() {
                            el.classList.remove("show");
                        },
                        1800
                    );
                }

                $script
            </script>
        </body>
        </html>
        """.trimIndent()


    private fun htmlFor(
        slug: String
    ): String? =
        when (
            slug
        ) {

            /*
             * ETKİLEŞİM
             */
            "interaction-toolkit" ->
                shell(
                    title =
                        "Etkileşim Araçları",
                    subtitle =
                        "Toast, titreşim ve kullanıcı etkileşimlerini test eden hazır proje.",
                    body =
                        """
                        <div class="grid">

                            <section class="card">
                                <h3>Toast</h3>
                                <p>
                                    Uygulama içinde hızlı bilgi mesajı göster.
                                </p>

                                <button onclick="showToast('Merhaba AppForge!')">
                                    TOAST GÖSTER
                                </button>
                            </section>

                            <section class="card">
                                <h3>Titreşim</h3>
                                <p>
                                    Cihaz destekliyorsa kısa titreşim çalıştırır.
                                </p>

                                <button onclick="runVibration()">
                                    TİTREŞİM TESTİ
                                </button>
                            </section>

                            <section class="card">
                                <h3>Bildirim API</h3>
                                <p>
                                    Web Notification desteğini kontrol eder.
                                </p>

                                <button onclick="notificationTest()">
                                    BİLDİRİMİ KONTROL ET
                                </button>
                            </section>

                        </div>
                        """.trimIndent(),
                    script =
                        """
                        function runVibration() {
                            if (
                                navigator.vibrate
                            ) {
                                navigator.vibrate(
                                    [80,50,120]
                                );

                                showToast(
                                    "Titreşim gönderildi"
                                );
                            } else {
                                showToast(
                                    "Titreşim API desteklenmiyor"
                                );
                            }
                        }

                        async function notificationTest() {
                            if (
                                !("Notification" in window)
                            ) {
                                showToast(
                                    "Notification API yok"
                                );

                                return;
                            }

                            var permission =
                                await Notification
                                    .requestPermission();

                            showToast(
                                "Bildirim izni: " +
                                permission
                            );
                        }
                        """.trimIndent()
                )


            /*
             * BAŞLANGIÇ / OYUN
             */
            "html-game" ->
                shell(
                    title =
                        "HTML5 Mini Oyun",
                    subtitle =
                        "Canvas tabanlı, dokunmatik ekranla çalışan hazır oyun başlangıcı.",
                    body =
                        """
                        <section class="card">
                            <canvas
                                id="game"
                                width="720"
                                height="420">
                            </canvas>

                            <div
                                id="score"
                                class="value">
                                Skor: 0
                            </div>

                            <p class="muted">
                                Mavi hedefe dokun. Her isabette hedef başka yere gider.
                            </p>
                        </section>
                        """.trimIndent(),
                    script =
                        """
                        var canvas =
                            document.getElementById(
                                "game"
                            );

                        var ctx =
                            canvas.getContext(
                                "2d"
                            );

                        var score =
                            0;

                        var target = {
                            x:360,
                            y:210,
                            r:34
                        };

                        function randomTarget() {
                            target.x =
                                50 +
                                Math.random() *
                                (canvas.width - 100);

                            target.y =
                                50 +
                                Math.random() *
                                (canvas.height - 100);
                        }

                        function draw() {
                            ctx.clearRect(
                                0,
                                0,
                                canvas.width,
                                canvas.height
                            );

                            ctx.fillStyle =
                                "#09111d";

                            ctx.fillRect(
                                0,
                                0,
                                canvas.width,
                                canvas.height
                            );

                            ctx.beginPath();

                            ctx.arc(
                                target.x,
                                target.y,
                                target.r,
                                0,
                                Math.PI * 2
                            );

                            ctx.fillStyle =
                                "#80caff";

                            ctx.fill();

                            requestAnimationFrame(
                                draw
                            );
                        }

                        function hit(event) {
                            var rect =
                                canvas.getBoundingClientRect();

                            var clientX =
                                event.touches
                                    ? event.touches[0].clientX
                                    : event.clientX;

                            var clientY =
                                event.touches
                                    ? event.touches[0].clientY
                                    : event.clientY;

                            var x =
                                (
                                    clientX -
                                    rect.left
                                ) *
                                canvas.width /
                                rect.width;

                            var y =
                                (
                                    clientY -
                                    rect.top
                                ) *
                                canvas.height /
                                rect.height;

                            var dx =
                                x - target.x;

                            var dy =
                                y - target.y;

                            if (
                                Math.sqrt(
                                    dx * dx +
                                    dy * dy
                                ) <= target.r
                            ) {
                                score++;

                                document
                                    .getElementById(
                                        "score"
                                    )
                                    .textContent =
                                        "Skor: " +
                                        score;

                                randomTarget();

                                if (
                                    navigator.vibrate
                                ) {
                                    navigator.vibrate(
                                        30
                                    );
                                }
                            }
                        }

                        canvas.addEventListener(
                            "click",
                            hit
                        );

                        canvas.addEventListener(
                            "touchstart",
                            hit,
                            {
                                passive:true
                            }
                        );

                        draw();
                        """.trimIndent()
                )


            /*
             * STARTER LIBRARIES
             */
            "bootstrap-starter" ->
                shell(
                    title =
                        "Responsive Starter",
                    subtitle =
                        "Bootstrap tarzı responsive kartlar, butonlar ve form alanları içeren başlangıç.",
                    body =
                        """
                        <section class="hero">
                            <h2>
                                Yeni uygulaman hazır
                            </h2>

                            <p>
                                Bu ekran telefon ve tablet genişliğine otomatik uyum sağlar.
                            </p>

                            <button onclick="showToast('Başlangıç projesi hazır')">
                                BAŞLA
                            </button>
                        </section>

                        <div class="grid">
                            <div class="card">
                                <h3>Kart 1</h3>
                                <p>
                                    İçeriğini buradan değiştirebilirsin.
                                </p>
                            </div>

                            <div class="card">
                                <h3>Kart 2</h3>
                                <p>
                                    Mobil uyumlu grid düzeni hazır.
                                </p>
                            </div>

                            <div class="card">
                                <h3>Form</h3>

                                <input
                                    placeholder="Adın">

                                <button onclick="showToast('Form örneği çalışıyor')">
                                    GÖNDER
                                </button>
                            </div>
                        </div>
                        """.trimIndent()
                )


            /*
             * REKLAMLAR
             */
            "admob-starter" ->
                shell(
                    title =
                        "AdMob Uygulama Başlangıcı",
                    subtitle =
                        "Reklam alanları düşünülerek hazırlanmış mobil uygulama düzeni.",
                    body =
                        """
                        <section class="hero">
                            <h2>
                                İçerik Alanı
                            </h2>

                            <p>
                                Ana uygulama içeriğini bu alanda oluştur.
                            </p>
                        </section>

                        <div class="grid">
                            <section class="card">
                                <h3>Banner Alanı</h3>

                                <div class="value">
                                    Native AdMob banner
                                </div>

                                <p class="muted">
                                    Gerçek reklam kimliklerini Production → Para Kazanma bölümünden gir.
                                </p>
                            </section>

                            <section class="card">
                                <h3>Interstitial</h3>

                                <p>
                                    Geçiş reklamını uygun kullanıcı aksiyonundan sonra tetikle.
                                </p>

                                <button onclick="showToast('Interstitial tetikleme noktası')">
                                    ÖRNEK AKSİYON
                                </button>
                            </section>

                            <section class="card">
                                <h3>Rewarded</h3>

                                <p>
                                    Ödüllü reklam sonrasında kullanıcıya içerik açabilirsin.
                                </p>
                            </section>
                        </div>
                        """.trimIndent()
                )


            /*
             * CİHAZ
             */
            "device-api-kit" ->
                shell(
                    title =
                        "Cihaz API Kiti",
                    subtitle =
                        "Kamera, konum, paylaşım ve pano için çalışan örnekler.",
                    body =
                        """
                        <div class="grid">

                            <section class="card">
                                <h3>Kamera</h3>

                                <input
                                    id="camera"
                                    type="file"
                                    accept="image/*"
                                    capture="environment">

                                <div
                                    id="cameraResult"
                                    class="value">
                                    Fotoğraf seçilmedi
                                </div>
                            </section>

                            <section class="card">
                                <h3>Konum</h3>

                                <button onclick="getLocation()">
                                    KONUMU AL
                                </button>

                                <div
                                    id="location"
                                    class="value">
                                    Bekleniyor
                                </div>
                            </section>

                            <section class="card">
                                <h3>Paylaş</h3>

                                <button onclick="shareDemo()">
                                    PAYLAŞ
                                </button>
                            </section>

                            <section class="card">
                                <h3>Pano</h3>

                                <textarea
                                    id="clipboardText"
                                    rows="3">AppForge Studio</textarea>

                                <button onclick="copyDemo()">
                                    PANoya KOPYALA
                                </button>
                            </section>

                        </div>
                        """.trimIndent(),
                    script =
                        """
                        document
                            .getElementById(
                                "camera"
                            )
                            .addEventListener(
                                "change",
                                function(event) {
                                    var file =
                                        event.target.files[0];

                                    document
                                        .getElementById(
                                            "cameraResult"
                                        )
                                        .textContent =
                                            file
                                                ? file.name
                                                : "Fotoğraf seçilmedi";
                                }
                            );

                        function getLocation() {
                            var out =
                                document.getElementById(
                                    "location"
                                );

                            if (
                                !navigator.geolocation
                            ) {
                                out.textContent =
                                    "Konum API desteklenmiyor";

                                return;
                            }

                            out.textContent =
                                "Konum alınıyor...";

                            navigator.geolocation
                                .getCurrentPosition(
                                    function(pos) {
                                        out.textContent =
                                            "Enlem: " +
                                            pos.coords.latitude.toFixed(6) +
                                            " • Boylam: " +
                                            pos.coords.longitude.toFixed(6);
                                    },
                                    function(err) {
                                        out.textContent =
                                            "Hata: " +
                                            err.message;
                                    }
                                );
                        }

                        async function shareDemo() {
                            if (
                                navigator.share
                            ) {
                                await navigator.share({
                                    title:
                                        "AppForge Studio",
                                    text:
                                        "Cihaz API Kiti"
                                });

                                return;
                            }

                            showToast(
                                "Web Share API desteklenmiyor"
                            );
                        }

                        async function copyDemo() {
                            var text =
                                document.getElementById(
                                    "clipboardText"
                                ).value;

                            try {
                                await navigator.clipboard
                                    .writeText(
                                        text
                                    );

                                showToast(
                                    "Panoya kopyalandı"
                                );
                            } catch (_) {
                                showToast(
                                    "Pano izni kullanılamadı"
                                );
                            }
                        }
                        """.trimIndent()
                )


            /*
             * SENSÖRLER
             */
            "sensor-dashboard" ->
                shell(
                    title =
                        "Sensör Paneli",
                    subtitle =
                        "İvme ve yönelim verilerini canlı gösteren hazır dashboard.",
                    body =
                        """
                        <div class="grid">

                            <section class="card">
                                <h3>Yönelim</h3>

                                <div
                                    id="orientation"
                                    class="value">
                                    Sensör bekleniyor
                                </div>
                            </section>

                            <section class="card">
                                <h3>İvme</h3>

                                <div
                                    id="motion"
                                    class="value">
                                    Sensör bekleniyor
                                </div>
                            </section>

                        </div>

                        <section
                            class="card"
                            style="margin-top:12px">

                            <button onclick="requestSensors()">
                                SENSÖRLERİ BAŞLAT
                            </button>

                        </section>
                        """.trimIndent(),
                    script =
                        """
                        function attachSensors() {
                            window.addEventListener(
                                "deviceorientation",
                                function(event) {
                                    document
                                        .getElementById(
                                            "orientation"
                                        )
                                        .textContent =
                                            "Alpha: " +
                                            Number(
                                                event.alpha || 0
                                            ).toFixed(1) +
                                            " • Beta: " +
                                            Number(
                                                event.beta || 0
                                            ).toFixed(1) +
                                            " • Gamma: " +
                                            Number(
                                                event.gamma || 0
                                            ).toFixed(1);
                                }
                            );

                            window.addEventListener(
                                "devicemotion",
                                function(event) {
                                    var a =
                                        event.accelerationIncludingGravity || {};

                                    document
                                        .getElementById(
                                            "motion"
                                        )
                                        .textContent =
                                            "X: " +
                                            Number(
                                                a.x || 0
                                            ).toFixed(2) +
                                            " • Y: " +
                                            Number(
                                                a.y || 0
                                            ).toFixed(2) +
                                            " • Z: " +
                                            Number(
                                                a.z || 0
                                            ).toFixed(2);
                                }
                            );
                        }

                        async function requestSensors() {
                            try {
                                if (
                                    typeof DeviceMotionEvent !==
                                        "undefined" &&
                                    typeof DeviceMotionEvent
                                        .requestPermission ===
                                        "function"
                                ) {
                                    await DeviceMotionEvent
                                        .requestPermission();
                                }

                                attachSensors();

                                showToast(
                                    "Sensör dinleme başlatıldı"
                                );
                            } catch (error) {
                                showToast(
                                    "Sensör izni alınamadı"
                                );
                            }
                        }

                        attachSensors();
                        """.trimIndent()
                )


            /*
             * SİSTEM
             */
            "system-info" ->
                shell(
                    title =
                        "Sistem Bilgileri",
                    subtitle =
                        "Tarayıcı, ekran, bağlantı ve pil bilgilerini gösteren hazır sistem paneli.",
                    body =
                        """
                        <div class="grid">

                            <section class="card">
                                <h3>Cihaz</h3>

                                <div
                                    id="device"
                                    class="value">
                                </div>
                            </section>

                            <section class="card">
                                <h3>Ekran</h3>

                                <div
                                    id="screenInfo"
                                    class="value">
                                </div>
                            </section>

                            <section class="card">
                                <h3>Bağlantı</h3>

                                <div
                                    id="network"
                                    class="value">
                                </div>
                            </section>

                            <section class="card">
                                <h3>Pil</h3>

                                <div
                                    id="battery"
                                    class="value">
                                    Kontrol ediliyor
                                </div>
                            </section>

                        </div>
                        """.trimIndent(),
                    script =
                        """
                        document
                            .getElementById(
                                "device"
                            )
                            .textContent =
                                navigator.userAgent;

                        document
                            .getElementById(
                                "screenInfo"
                            )
                            .textContent =
                                screen.width +
                                " × " +
                                screen.height +
                                " • DPR " +
                                window.devicePixelRatio;

                        var connection =
                            navigator.connection ||
                            navigator.mozConnection ||
                            navigator.webkitConnection;

                        document
                            .getElementById(
                                "network"
                            )
                            .textContent =
                                connection
                                    ? (
                                        connection.effectiveType ||
                                        connection.type ||
                                        "Bağlantı mevcut"
                                    )
                                    : "Network Information API yok";

                        if (
                            navigator.getBattery
                        ) {
                            navigator
                                .getBattery()
                                .then(
                                    function(battery) {
                                        document
                                            .getElementById(
                                                "battery"
                                            )
                                            .textContent =
                                                "%" +
                                                Math.round(
                                                    battery.level *
                                                    100
                                                ) +
                                                (
                                                    battery.charging
                                                        ? " • Şarj oluyor"
                                                        : ""
                                                );
                                    }
                                );
                        } else {
                            document
                                .getElementById(
                                    "battery"
                                )
                                .textContent =
                                    "Battery API desteklenmiyor";
                        }
                        """.trimIndent()
                )


            /*
             * PANEL
             */
            "native-api-dashboard" ->
                shell(
                    title =
                        "Native API Paneli",
                    subtitle =
                        "QR, paylaşım, titreşim ve cihaz araçlarını tek ekranda toplar.",
                    body =
                        """
                        <div class="grid">

                            <section class="card">
                                <h3>QR / Barkod</h3>

                                <button onclick="scanQr()">
                                    QR TARA
                                </button>

                                <div
                                    id="qrResult"
                                    class="value">
                                    Sonuç bekleniyor
                                </div>
                            </section>

                            <section class="card">
                                <h3>Paylaşım</h3>

                                <button onclick="panelShare()">
                                    PAYLAŞ
                                </button>
                            </section>

                            <section class="card">
                                <h3>Titreşim</h3>

                                <button onclick="panelVibrate()">
                                    TİTREŞİM
                                </button>
                            </section>

                            <section class="card">
                                <h3>Durum</h3>

                                <div
                                    id="bridgeStatus"
                                    class="value">
                                    Kontrol ediliyor
                                </div>
                            </section>

                        </div>
                        """.trimIndent(),
                    script =
                        """
                        function refreshBridgeStatus() {
                            var status =
                                document.getElementById(
                                    "bridgeStatus"
                                );

                            if (
                                window.AppForge
                            ) {
                                status.textContent =
                                    "AppForge Native Bridge hazır";

                                return true;
                            }

                            status.textContent =
                                "Web modu / Bridge bekleniyor";

                            return false;
                        }

                        refreshBridgeStatus();

                        var bridgeCheckCount =
                            0;

                        var bridgeCheckTimer =
                            setInterval(
                                function() {
                                    bridgeCheckCount++;

                                    if (
                                        refreshBridgeStatus() ||
                                        bridgeCheckCount >= 20
                                    ) {
                                        clearInterval(
                                            bridgeCheckTimer
                                        );

                                        if (
                                            !window.AppForge
                                        ) {
                                            document
                                                .getElementById(
                                                    "bridgeStatus"
                                                )
                                                .textContent =
                                                    "Web modu / Bridge bulunamadı";
                                        }
                                    }
                                },
                                150
                            );

                        function scanQr() {
                            if (
                                window.AppForge &&
                                typeof window.AppForge
                                    .scanCode ===
                                    "function"
                            ) {
                                window.AppForge
                                    .scanCode();

                                showToast(
                                    "Tarayıcı açılıyor"
                                );

                                return;
                            }

                            showToast(
                                "QR Bridge etkin değil"
                            );
                        }

                        window.addEventListener(
                            "appforge-scan-result",
                            function(event) {
                                var value =
                                    event.detail &&
                                    (
                                        event.detail.value ||
                                        event.detail.rawValue
                                    );

                                document
                                    .getElementById(
                                        "qrResult"
                                    )
                                    .textContent =
                                        value ||
                                        JSON.stringify(
                                            event.detail || {}
                                        );
                            }
                        );

                        async function panelShare() {

                            /*
                             * Derlenmiş AppForge APK içinde
                             * öncelik Native Bridge'dedir.
                             */
                            if (
                                window.AppForge &&
                                typeof window.AppForge.share ===
                                    "function"
                            ) {
                                try {
                                    var result =
                                        window.AppForge.share(
                                            "AppForge",
                                            "Native API Paneli"
                                        );

                                    if (
                                        typeof result ===
                                            "string" &&
                                        result.indexOf(
                                            "ERROR|"
                                        ) === 0
                                    ) {
                                        showToast(
                                            result.substring(6)
                                        );
                                    }

                                    return;

                                } catch (error) {
                                    console.error(
                                        "AppForge share:",
                                        error
                                    );
                                }
                            }

                            /*
                             * Normal tarayıcı / PWA fallback.
                             */
                            if (
                                navigator.share
                            ) {
                                try {
                                    await navigator.share({
                                        title:
                                            "AppForge",
                                        text:
                                            "Native API Paneli"
                                    });

                                    return;
                                } catch (error) {
                                    console.error(
                                        "Web Share:",
                                        error
                                    );
                                }
                            }

                            showToast(
                                "Paylaşım özelliği kullanılamıyor"
                            );
                        }

                        function panelVibrate() {
                            if (
                                navigator.vibrate
                            ) {
                                navigator.vibrate(
                                    80
                                );

                                showToast(
                                    "Titreşim gönderildi"
                                );

                                return;
                            }

                            showToast(
                                "Titreşim desteklenmiyor"
                            );
                        }
                        """.trimIndent()
                )

            "task-manager" ->
                shell(
                    "Görev Yöneticisi",
                    "Görevlerini ekle, tamamla ve cihazında sakla.",
                    """
                    <section class="card">
                        <input id="taskInput" placeholder="Yeni görev">
                        <button onclick="addTask()">GÖREV EKLE</button>
                    </section>
                    <div id="tasks" class="grid"></div>
                    """.trimIndent(),
                    """
                    var tasks=JSON.parse(localStorage.getItem('tasks')||'[]');
                    function renderTasks(){
                        tasks=document.getElementById('tasks')?tasks:[];
                        document.getElementById('tasks').innerHTML=tasks.map(function(t,i){
                            return '<section class="card"><h3>'+t.text+'</h3><p>'+(t.done?'Tamamlandı':'Devam ediyor')+'</p><button onclick="toggleTask('+i+')">DURUMU DEĞİŞTİR</button></section>';
                        }).join('');
                        localStorage.setItem('tasks',JSON.stringify(tasks));
                    }
                    function addTask(){var input=document.getElementById('taskInput');if(!input.value.trim())return;tasks.push({text:input.value.trim(),done:false});input.value='';renderTasks();}
                    function toggleTask(i){tasks[i].done=!tasks[i].done;renderTasks();}
                    renderTasks();
                    """.trimIndent()
                )

            "inventory-panel" ->
                shell(
                    "Stok ve Envanter",
                    "Kritik seviyeleri hızlıca takip eden örnek yönetim paneli.",
                    """
                    <div class="grid">
                        <section class="card"><h3>Toplam ürün</h3><div class="value">128</div></section>
                        <section class="card"><h3>Kritik stok</h3><div class="value">7 ürün</div></section>
                        <section class="card"><h3>Bugün giriş</h3><div class="value">34 adet</div></section>
                    </div>
                    <section class="card"><h3>Hızlı işlem</h3><input placeholder="Ürün veya barkod"><button onclick="showToast('Stok kaydı hazır')">STOK EKLE</button></section>
                    """.trimIndent()
                )

            "booking-form" ->
                shell(
                    "Randevu Oluştur",
                    "Mobil uyumlu tarih, saat ve müşteri formu.",
                    """
                    <section class="card">
                        <input id="customer" placeholder="Ad soyad">
                        <input id="date" type="date">
                        <input id="time" type="time">
                        <textarea placeholder="Not"></textarea>
                        <button onclick="saveBooking()">RANDEVUYU KAYDET</button>
                        <div id="booking" class="value">Henüz randevu yok</div>
                    </section>
                    """.trimIndent(),
                    """
                    function saveBooking(){var name=document.getElementById('customer').value||'Müşteri';var date=document.getElementById('date').value;var time=document.getElementById('time').value;document.getElementById('booking').textContent=name+' • '+date+' '+time;showToast('Randevu kaydedildi');}
                    """.trimIndent()
                )

            "restaurant-menu" ->
                shell(
                    "Mobil Menü",
                    "Ürün kartları ve çalışan sepet sayacıyla restoran başlangıcı.",
                    """
                    <div class="grid">
                        <section class="card"><h3>Karışık Menü</h3><p>Izgara, garnitür ve içecek</p><div class="value">₺320</div><button onclick="addCart()">SEPETE EKLE</button></section>
                        <section class="card"><h3>Tavuk Menü</h3><p>Tavuk, patates ve içecek</p><div class="value">₺245</div><button onclick="addCart()">SEPETE EKLE</button></section>
                    </div>
                    <section class="card"><h3>Sepet</h3><div id="cart" class="value">0 ürün</div></section>
                    """.trimIndent(),
                    """var cart=0;function addCart(){cart++;document.getElementById('cart').textContent=cart+' ürün';showToast('Sepete eklendi');}"""
                )

            "event-invitation" ->
                shell(
                    "Etkinlik Davetiyesi",
                    "Katılım yanıtı, konum ve paylaşım için hazır etkinlik ekranı.",
                    """
                    <section class="hero"><h2>28 Ağustos • 19:30</h2><p>Etkinlik adresi ve program ayrıntılarını buraya yaz.</p></section>
                    <div class="grid">
                        <section class="card"><h3>Katılım</h3><button onclick="showToast('Katılımın kaydedildi')">KATILIYORUM</button></section>
                        <section class="card"><h3>Konum</h3><button onclick="getEventLocation()">KONUMU KONTROL ET</button><div id="eventLocation" class="value">Bekleniyor</div></section>
                        <section class="card"><h3>Paylaş</h3><button onclick="shareEvent()">DAVETİ PAYLAŞ</button></section>
                    </div>
                    """.trimIndent(),
                    """
                    function getEventLocation(){navigator.geolocation?navigator.geolocation.getCurrentPosition(function(p){document.getElementById('eventLocation').textContent=p.coords.latitude.toFixed(4)+', '+p.coords.longitude.toFixed(4);},function(){showToast('Konum alınamadı');}):showToast('Konum desteklenmiyor');}
                    async function shareEvent(){if(navigator.share){await navigator.share({title:'Etkinlik',text:'Etkinlik davetiyesi'});}else{showToast('Paylaşım desteklenmiyor');}}
                    """.trimIndent()
                )

            "visual-designer" ->
                shell(
                    "Görsel Sayfa Tasarımcısı",
                    "Blok ekle, önizle ve tarayıcıda yerel olarak sakla.",
                    """
                    <section class="card"><button onclick="addBlock('h3')">METİN EKLE</button><button onclick="addBlock('button')">BUTON EKLE</button><button onclick="addBlock('section')">KART EKLE</button></section>
                    <div id="canvas" class="grid"></div>
                    """.trimIndent(),
                    """
                    var blocks=JSON.parse(localStorage.getItem('designerBlocks')||'[]');
                    function draw(){document.getElementById('canvas').innerHTML=blocks.map(function(b,i){return b==='h3'?'<h3 contenteditable="true">Düzenlenebilir metin</h3>':b==='button'?'<button onclick="showToast(\'Buton çalışıyor\')">ÖRNEK BUTON</button>':'<section class="card"><h3>Kart '+(i+1)+'</h3><p>İçeriği projene göre düzenle.</p></section>';}).join('');localStorage.setItem('designerBlocks',JSON.stringify(blocks));}
                    function addBlock(type){blocks.push(type);draw();}draw();
                    """.trimIndent()
                )

            "personnel-tracker" ->
                shell(
                    "Personel Takibi",
                    "Ekip durumunu ve vardiyaları tek ekranda izle.",
                    """
                    <section class="card"><input id="staffSearch" placeholder="Personel ara" oninput="filterStaff()"></section>
                    <div id="staff" class="grid"><section class="card" data-name="ayşe"><h3>Ayşe • Sabah</h3><p>Görevde</p></section><section class="card" data-name="mehmet"><h3>Mehmet • Akşam</h3><p>İzinli</p></section></div>
                    """.trimIndent(),
                    "function filterStaff(){var q=document.getElementById('staffSearch').value.toLowerCase();document.querySelectorAll('[data-name]').forEach(function(x){x.style.display=x.dataset.name.includes(q)?'block':'none';});}"
                )

            "qr-menu" ->
                shell(
                    "QR Menü ve Sipariş",
                    "Kamera/QR yeteneğine hazır çalışan sepet başlangıcı.",
                    """
                    <section class="card"><button onclick="showToast('QR tarayıcı proje izinlerinden açılır')">QR TARA</button></section>
                    <div class="grid"><section class="card"><h3>Günün Menüsü</h3><p>₺280</p><button onclick="addOrder()">EKLE</button></section></div><section class="card"><div id="order" class="value">0 ürün</div></section>
                    """.trimIndent(),
                    "var order=0;function addOrder(){order++;document.getElementById('order').textContent=order+' ürün';}"
                )

            "education-quiz" ->
                shell(
                    "Eğitim ve Quiz",
                    "Puan tutan etkileşimli soru başlangıcı.",
                    """
                    <section class="card"><h3 id="question">Android uygulama paketi hangisidir?</h3><button onclick="answer(true)">APK</button><button onclick="answer(false)">CSV</button><div id="score" class="value">Puan: 0</div></section>
                    """.trimIndent(),
                    "var score=0;function answer(ok){if(ok)score++;document.getElementById('score').textContent='Puan: '+score;showToast(ok?'Doğru':'Tekrar dene');}"
                )

            "firebase-login" ->
                shell(
                    "Firebase Giriş",
                    "Firebase bağlantısı eklenince gerçek kimlik doğrulamaya uyarlanabilen ekran.",
                    """
                    <section class="card"><input id="email" type="email" placeholder="E-posta"><input type="password" placeholder="Şifre"><button onclick="loginPreview()">GİRİŞ YAP</button><p id="loginState">Demo modu</p></section>
                    """.trimIndent(),
                    "function loginPreview(){var email=document.getElementById('email').value;document.getElementById('loginState').textContent=email?'Form doğrulandı • Firebase yapılandırmasını bağla':'E-posta gerekli';}"
                )

            else ->
                null
        }
}
