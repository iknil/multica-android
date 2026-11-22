(() => {
  if (window.__multicaDownloadsInstalled) return;
  window.__multicaDownloadsInstalled = true;
  const token = "__TOKEN__";
  const bridge = window.MulticaDownloads;
  const inProgress = new Set();
  const revoke = URL.revokeObjectURL.bind(URL);
  const click = HTMLAnchorElement.prototype.click;

  URL.revokeObjectURL = (url) => {
    if (!inProgress.has(url)) revoke(url);
  };

  window.__multicaDownloadSave = (url, name) => {
    if (!url.startsWith("blob:") && !url.startsWith("data:")) return false;
    inProgress.add(url);
    const response = fetch(url); // Start before the caller revokes a blob URL.
    (async () => {
      try {
        const blob = await (await response).blob();
        if (!bridge.begin(token, name || "multica-export", blob.type, blob.size)) return;
        const bytes = new Uint8Array(await blob.arrayBuffer());
        for (let offset = 0; offset < bytes.length; offset += 32768) {
          const chunk = bytes.subarray(offset, offset + 32768);
          let binary = "";
          for (const byte of chunk) binary += String.fromCharCode(byte);
          if (!bridge.append(token, btoa(binary))) return;
        }
        bridge.finish(token);
      } catch (_) {
        bridge.fail(token);
      } finally {
        inProgress.delete(url);
        if (url.startsWith("blob:")) revoke(url);
      }
    })();
    return true;
  };

  // Programmatic clicks on detached anchors never reach document listeners.
  // The web app uses this form for CSV and theme exports.
  HTMLAnchorElement.prototype.click = function () {
    if (this.hasAttribute("download") &&
        (this.href.startsWith("blob:") || this.href.startsWith("data:"))) {
      window.__multicaDownloadSave(this.href, this.download);
      return;
    }
    return click.call(this);
  };

  document.addEventListener("click", (event) => {
    const target = event.target;
    const anchor = target instanceof Element ? target.closest("a[download]") : null;
    if (!anchor) return;
    if (!anchor.href.startsWith("blob:") && !anchor.href.startsWith("data:")) return;
    event.preventDefault();
    window.__multicaDownloadSave(anchor.href, anchor.download);
  }, true);
})();
