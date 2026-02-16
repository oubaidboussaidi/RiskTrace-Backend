(function () {
    "use strict";

    // ====================================================
    // 1. CONFIGURATION & INITIALISATION
    // ====================================================
    const SCRIPT = document.currentScript || (function () {
        const scripts = document.getElementsByTagName("script");
        for (let s of scripts) {
            if (s.getAttribute("data-api-key")) return s;
        }
        return null;
    })();

    const API_KEY = SCRIPT?.getAttribute("data-api-key");
    const DEBUG = SCRIPT?.getAttribute("data-debug") === "true";
    const API_URL = SCRIPT?.getAttribute("data-endpoint") || "http://localhost:8080/api/logs/collect";

    if (!API_KEY) {
        console.error("[Tracker] Clé API manquante.");
        return;
    }

    // Session ID
    let SESSION_ID = sessionStorage.getItem("tracker_session_id");
    if (!SESSION_ID) {
        SESSION_ID = "s_" + Date.now() + "_" + Math.random().toString(36).substring(2, 10);
        sessionStorage.setItem("tracker_session_id", SESSION_ID);
    }

    // ====================================================
    // 2. FILE D’ATTENTE & BATCHING
    // ====================================================
    const logQueue = [];
    let batchTimer = null;

    function enqueueLog(logData) {
        logQueue.push({
            apiKey: API_KEY,
            sessionId: SESSION_ID,
            ipAddress: null, // rempli par le backend
            country: null, // rempli par le backend
            city: null, // rempli par le backend
            url: logData.url || window.location.pathname,
            method: logData.method || "GET",
            statusCode: logData.statusCode || null,
            userAgent: navigator.userAgent,
            device: getDeviceType(),
            responseTime: logData.responseTime || null,
            type: logData.type,
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
            logQueue.unshift(...payload);
        });
    }

    // ====================================================
    // 3. OUTILS
    // ====================================================
    function getDeviceType() {
        const ua = navigator.userAgent.toLowerCase();
        if (/mobile|android|iphone/.test(ua)) return "mobile";
        if (/tablet|ipad/.test(ua)) return "tablet";
        return "desktop";
    }

    let requestCount = 0;
    let alertSentThisMinute = false;

    function incrementRequestCount() {
        requestCount++;
        if (requestCount > 20 && !alertSentThisMinute) {
            enqueueLog({
                type: "suspicious_activity",
                method: "GET",
                statusCode: 429
            });
            alertSentThisMinute = true;
        }
    }

    setInterval(() => {
        requestCount = 0;
        alertSentThisMinute = false;
    }, 60000);

    // ====================================================
    // 4. INTERCEPTION FETCH
    // ====================================================
    const originalFetch = window.fetch;
    window.fetch = function (...args) {
        const requestStart = performance.now();
        const [url, options = {}] = args;
        const method = options.method || "GET";
        const isOwnRequest = typeof url === "string" && url.includes(API_URL);

        if (!isOwnRequest) {
            incrementRequestCount();
            enqueueLog({
                type: "fetch_request",
                url: typeof url === "string" ? url : url.url,
                method: method
            });
        }

        return originalFetch.apply(this, args).then((response) => {
            if (!isOwnRequest) {
                const responseTime = Math.round(performance.now() - requestStart);
                enqueueLog({
                    type: "fetch_response",
                    url: typeof url === "string" ? url : url.url,
                    method: method,
                    statusCode: response.status,
                    responseTime: responseTime
                });
            }
            return response;
        });
    };

    // ====================================================
    // 5. INTERCEPTION XHR
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
            const url = this._trackerData.url;
            const method = this._trackerData.method;
            const isOwnRequest = url.includes(API_URL);

            if (!isOwnRequest) {
                incrementRequestCount();
            }

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
    // 6. CHARGEMENT PAGE
    // ====================================================
    window.addEventListener("load", () => {
        const perf = performance.getEntriesByType("navigation")[0];
        const responseTime = perf ? Math.round(perf.duration) : null;
        enqueueLog({
            type: "page_load",
            method: "GET",
            statusCode: 200,
            responseTime: responseTime
        });
    });

    // ====================================================
    // 7. FORMULAIRES
    // ====================================================
    document.addEventListener("submit", (e) => {
        const form = e.target;
        enqueueLog({
            type: "form_submit",
            method: (form.method || "POST").toUpperCase(),
            url: form.action || window.location.pathname
        });
    });

    // ====================================================
    // 8. ERREURS JAVASCRIPT
    // ====================================================
    window.addEventListener("error", (e) => {
        enqueueLog({
            type: "js_error",
            method: "GET",
            statusCode: 500,
            url: e.filename || window.location.pathname
        });
    });

    window.addEventListener("unhandledrejection", (e) => {
        enqueueLog({
            type: "unhandled_promise_rejection",
            method: "GET",
            statusCode: 500
        });
    });

    // ====================================================
    // 9. VIDAGE FINAL
    // ====================================================
    window.addEventListener("visibilitychange", () => {
        if (document.visibilityState === "hidden") {
            flushLogs();
        }
    });

    setTimeout(flushLogs, 2000);
})();
