(function () {
    "use strict";

    // ====================================================
    // 1. CONFIGURATION & INITIALISATION
    // ====================================================
    // Finds the <script> tag that loaded this file to extract API key and endpoint.
    const SCRIPT = document.currentScript || (function () {
        const scripts = document.getElementsByTagName("script");
        for (let s of scripts) {
            if (s.getAttribute("data-api-key")) return s;
        }
        return null;
    })();

    const API_KEY = SCRIPT?.getAttribute("data-api-key");
    const DEBUG = SCRIPT?.getAttribute("data-debug") === "true";
    const API_URL = SCRIPT?.getAttribute("data-endpoint") || "http://localhost:8084/api/logs/collect";

    if (!API_KEY) {
        console.error("[Tracker] Clé API manquante.");
        return;
    }

    // Initialize or retrieve the unique session identifier.
    let SESSION_ID = sessionStorage.getItem("tracker_session_id");
    if (!SESSION_ID) {
        SESSION_ID = "s_" + Date.now() + "_" + Math.random().toString(36).substring(2, 10);
        sessionStorage.setItem("tracker_session_id", SESSION_ID);
    }

    // ====================================================
    // 2. EXTRACTION DE DONNÉES AVANCÉES
    // ====================================================
    
    // Default geolocation structure
    let geoData = {
        ip: null,
        country: null,
        city: null
    };

    // Fetch IP and Geo Location asynchronously from a reliable free API
    function fetchGeoData() {
        fetch("https://get.geojs.io/v1/ip/geo.json")
            .then(response => response.json())
            .then(data => {
                geoData.ip = data.ip || null;
                geoData.country = data.country || null;
                geoData.city = data.city || null;
                if (DEBUG) console.log("[Tracker] GeoData récupérée", geoData);
            })
            .catch(error => {
                if (DEBUG) console.error("[Tracker] Erreur lors de la récupération GeoData", error);
            });
    }
    fetchGeoData(); // Trigger immediately

    function getDeviceType() {
        const ua = navigator.userAgent.toLowerCase();
        if (/mobile|android|iphone|ipod/.test(ua)) return "mobile";
        if (/tablet|ipad/.test(ua)) return "tablet";
        return "desktop";
    }

    // ====================================================
    // 3. FILE D’ATTENTE & BATCHING
    // ====================================================
    const logQueue = [];
    let batchTimer = null;

    function enqueueLog(logData) {
        logQueue.push({
            apiKey: API_KEY,
            sessionId: SESSION_ID,
            // Enhanced Tracker Fields
            ipAddress: geoData.ip, 
            country: geoData.country, 
            city: geoData.city, 
            url: logData.url || window.location.href, // full URL
            method: logData.method || "GET",
            statusCode: logData.statusCode || null,
            userAgent: navigator.userAgent,
            device: getDeviceType(),
            responseTime: logData.responseTime || null,
            type: logData.type || "unknown",
            createdAt: new Date().toISOString()
        });

        if (DEBUG) console.log("[Tracker] Log ajouté", logData);

        if (logQueue.length >= 10) {
            flushLogs();
        } else if (!batchTimer) {
            batchTimer = setTimeout(flushLogs, 5000);
        }
    }

    function flushLogs() {
        if (batchTimer) {
            clearTimeout(batchTimer);
            batchTimer = null;
        }
        if (logQueue.length === 0) return;

        const payload = logQueue.slice();
        logQueue.length = 0;

        fetch(API_URL, {
            method: "POST",
            headers: {
                "Content-Type": "application/json"
            },
            body: JSON.stringify(payload),
            keepalive: true
        }).catch(() => {
            logQueue.unshift(...payload); // Return failed logs to the front of queue
        });
    }



    // ====================================================
    // 5. INTERCEPTION FETCH
    // ====================================================
    const originalFetch = window.fetch;
    window.fetch = function (...args) {
        const requestStart = performance.now();
        const [url, options = {}] = args;
        const method = options.method || "GET";
        const isOwnRequest = typeof url === "string" && url.includes(API_URL);
        const urlString = typeof url === "string" ? url : (url.url || "unknown");

        if (!isOwnRequest && !urlString.includes("geojs.io")) { // Skip tracking the GeoIP fetch
            enqueueLog({
                type: "fetch_request",
                url: urlString,
                method: method
            });
        }

        return originalFetch.apply(this, args).then((response) => {
            if (!isOwnRequest && !urlString.includes("geojs.io")) {
                const responseTime = Math.round(performance.now() - requestStart);
                enqueueLog({
                    type: "fetch_response",
                    url: urlString,
                    method: method,
                    statusCode: response.status,
                    responseTime: responseTime
                });
            }
            return response;
        });
    };

    // ====================================================
    // 6. INTERCEPTION XHR
    // ====================================================
    const originalXHROpen = XMLHttpRequest.prototype.open;
    const originalXHRSend = XMLHttpRequest.prototype.send;

    XMLHttpRequest.prototype.open = function (method, url) {
        this._trackerData = { method, url, startTime: null };
        return originalXHROpen.apply(this, arguments);
    };

    XMLHttpRequest.prototype.send = function () {
        if (this._trackerData) {
            this._trackerData.startTime = performance.now();
            const url = this._trackerData.url || "";
            const isOwnRequest = url.includes(API_URL) || url.includes("geojs.io");

            this.addEventListener("loadend", () => {
                if (!isOwnRequest && this._trackerData) {
                    const responseTime = Math.round(performance.now() - this._trackerData.startTime);
                    enqueueLog({
                        type: "xhr_response",
                        url: this._trackerData.url,
                        method: this._trackerData.method,
                        statusCode: this.status,
                        responseTime: responseTime
                    });
                }
            });
        }
        return originalXHRSend.apply(this, arguments);
    };

    // ====================================================
    // 7. CHARGEMENT PAGE
    // ====================================================
    window.addEventListener("load", () => {
        // Use timeout to ensure geoData API fetch had a bit of time to resolve if possible
        setTimeout(() => {
            const perf = performance.getEntriesByType("navigation")[0];
            const responseTime = perf ? Math.round(perf.duration) : null;
            enqueueLog({
                type: "page_load",
                method: "GET",
                statusCode: 200,
                responseTime: responseTime
            });
        }, 1500);
    });

    // ====================================================
    // 8. FORMULAIRES
    // ====================================================
    document.addEventListener("submit", (e) => {
        const form = e.target;
        enqueueLog({
            type: "form_submit",
            method: (form.method || "POST").toUpperCase(),
            url: form.action || window.location.href
        });
    });

    // ====================================================
    // 9. ERREURS JAVASCRIPT
    // ====================================================
    window.addEventListener("error", (e) => {
        enqueueLog({
            type: "js_error",
            method: "GET",
            statusCode: 500,
            url: e.filename || window.location.href
        });
    });

    window.addEventListener("unhandledrejection", (e) => {
        enqueueLog({
            type: "unhandled_promise_rejection",
            method: "GET",
            statusCode: 500,
            url: window.location.href
        });
    });

    // ====================================================
    // 10. VIDAGE FINAL
    // ====================================================
    window.addEventListener("visibilitychange", () => {
        if (document.visibilityState === "hidden") {
            flushLogs();
        }
    });

    window.addEventListener("beforeunload", () => {
        flushLogs();
    });

    setTimeout(flushLogs, 2000);
})();
