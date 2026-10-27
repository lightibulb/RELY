(function () {
    if (window.__RELY_BRIDGE_LOADED__) {
        return;
    }
    window.__RELY_BRIDGE_LOADED__ = true;

    function ready(fn) {
        if (document.readyState !== "loading") {
            fn();
        } else {
            document.addEventListener("DOMContentLoaded", fn);
        }
    }

    function q(selector) {
        return document.querySelector(selector);
    }

    function setText(selector, value) {
        var node = q(selector);
        if (node) {
            node.textContent = value;
        }
    }

    ready(function () {
        if (window.RELY_LOCAL_FONT === true) {
            var fontStyle = document.createElement("style");
            fontStyle.textContent = [
                "@font-face {",
                "  font-family: \"Pixelify Sans\";",
                "  src: url(\"file:///android_asset/fonts/PixelifySans.ttf\") format(\"truetype\");",
                "  font-weight: 100 900;",
                "  font-display: swap;",
                "}"
            ].join("\n");
            document.head.appendChild(fontStyle);
        }

        var style = document.createElement("style");
        style.textContent = [
            "#rely-load-btn {",
            "  appearance: none;",
            "  border: 4px solid var(--fg, #0d0d0d);",
            "  background: var(--bg, #ececec);",
            "  color: var(--fg, #0d0d0d);",
            "  font-family: inherit;",
            "  font-size: 14px;",
            "  line-height: 1;",
            "  padding: 10px 12px;",
            "  margin-left: auto;",
            "  cursor: pointer;",
            "  image-rendering: pixelated;",
            "  text-transform: uppercase;",
            "}",
            "#rely-load-btn:active {",
            "  transform: translateY(1px);",
            "}"
        ].join("\n");
        document.head.appendChild(style);

        var btn = document.createElement("button");
        btn.id = "rely-load-btn";
        btn.type = "button";
        btn.className = "px";
        btn.textContent = "LOAD";
        btn.setAttribute("aria-label", "Load audio file");

        var top = q(".top");
        if (top) {
            top.appendChild(btn);
        } else {
            btn.style.position = "fixed";
            btn.style.top = "12px";
            btn.style.right = "12px";
            btn.style.zIndex = "9999";
            document.body.appendChild(btn);
        }

        btn.addEventListener("click", function () {
            if (window.Android && typeof Android.pickAudioFile === "function") {
                Android.pickAudioFile();
            }
        });

        window.pickTrack = function () {
            if (window.Android && typeof Android.pickAudioFile === "function") {
                Android.pickAudioFile();
            }
        };

        window.onFilePicked = function (uri, name) {
            var title = String(name || "Unknown").replace(/\.[^.]+$/, "");

            setText("#mt", title);
            setText("#ma", "RELY");

            var np = q("#np");
            if (np) {
                np.classList.add("open");
                np.dataset.tab = "1";
            }

            if (window.Android) {
                if (typeof Android.keepScreenOn === "function") {
                    Android.keepScreenOn(true);
                }
                if (typeof Android.play === "function") {
                    Android.play(uri, title, "RELY");
                }
            }

            try {
                document.dispatchEvent(new CustomEvent("rely:track", {
                    detail: {
                        uri: uri,
                        title: title,
                        artist: "RELY"
                    }
                }));
            } catch (e) {
                // Older WebView fallback: ignore custom event failure.
            }
        };

        if (typeof window.relyBack !== "function") {
            window.relyBack = function () {
                var np = q("#np");

                if (np && (np.classList.contains("open") || String(np.dataset.tab) === "1")) {
                    np.classList.remove("open");
                    np.dataset.tab = "0";

                    if (window.Android && typeof Android.keepScreenOn === "function") {
                        Android.keepScreenOn(false);
                    }

                    return true;
                }

                return false;
            };
        }

        var np = q("#np");
        if (np && "MutationObserver" in window) {
            var observer = new MutationObserver(function () {
                var open = np.classList.contains("open") || String(np.dataset.tab) === "1";

                if (window.Android && typeof Android.keepScreenOn === "function") {
                    Android.keepScreenOn(open);
                }
            });

            observer.observe(np, {
                attributes: true,
                attributeFilter: ["class", "data-tab"]
            });
        }
    });
})();
