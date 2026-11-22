(() => {
  if (window.__multicaThemeFolderInstalled) return;
  window.__multicaThemeFolderInstalled = true;
  const token = "__TOKEN__";
  let input = null;
  let chunks = [];

  document.addEventListener("click", (event) => {
    const target = event.target;
    if (!(target instanceof HTMLInputElement) || !target.hasAttribute("webkitdirectory")) return;
    if (!/(^|\/)settings$/.test(location.pathname)) return;
    event.preventDefault();
    event.stopImmediatePropagation();
    input = target;
    chunks = [];
    window.MulticaThemeFolders.choose(token);
  }, true);

  window.__multicaThemeFolderCancel = (candidate) => {
    if (candidate !== token) return;
    input = null;
    chunks = [];
  };

  window.__multicaThemeFolderChunk = (candidate, chunk, last) => {
    if (candidate !== token || !input) return false;
    chunks.push(chunk);
    if (!last) return true;
    try {
      const binary = atob(chunks.join(""));
      const bytes = Uint8Array.from(binary, (char) => char.charCodeAt(0));
      const file = new File([bytes], "multica-theme.zip", { type: "application/zip" });
      const transfer = new DataTransfer();
      transfer.items.add(file);
      input.files = transfer.files;
      input.dispatchEvent(new Event("change", { bubbles: true }));
      return true;
    } catch (_) {
      return false;
    } finally {
      input = null;
      chunks = [];
    }
  };
})();
