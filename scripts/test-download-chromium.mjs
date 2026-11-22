import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { pathToFileURL } from "node:url";

const capture = readFileSync(new URL("../app/src/main/assets/download-capture.js", import.meta.url), "utf8")
  .replace("__TOKEN__", "chromium-test");
const work = mkdtempSync(join(process.cwd(), ".chromium-export-"));
try {
  const page = join(work, "export.html");
  writeFileSync(page, `<!doctype html><meta charset="utf-8"><title>pending</title><body>
<script>
const exports = [];
const pending = [];
const revocations = [];
const nativeRevoke = URL.revokeObjectURL.bind(URL);
URL.revokeObjectURL = (url) => { revocations.push(url); nativeRevoke(url); };
window.MulticaDownloads = {
  begin(token, name, type, size) {
    if (token !== "chromium-test") throw Error("bad token");
    pending.push({ name, type, size, chunks: [] });
    return true;
  },
  append(token, chunk) { pending.at(-1).chunks.push(chunk); return true; },
  finish(token) {
    const item = pending.pop();
    const binary = item.chunks.map(atob).join("");
    item.bytes = Array.from(binary, (char) => char.charCodeAt(0));
    delete item.chunks;
    exports.push(item);
    return true;
  },
  fail() { throw Error("export failed"); },
};
</script><script>${capture}</script><script>
(async () => {
  const png = Uint8Array.from(atob("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/lS8AAAAASUVORK5CYII="), c => c.charCodeAt(0));
  // Stored ZIP with manifest.json and theme.css, as produced by the Web theme exporter.
  const theme = Uint8Array.from(atob("UEsDBBQAAAAAADl/dl0MprUpHQAAAB0AAAANAAAAbWFuaWZlc3QuanNvbnsiaWQiOiJzYW1wbGUiLCJuYW1lIjoiVGVzdCJ9UEsDBBQAAAAAADl/dl1WKXYxHAAAABwAAAAJAAAAdGhlbWUuY3NzLnRoZW1lLWxpZ2h0IHsgY29sb3I6IHJlZDsgfVBLAQIUAxQAAAAAADl/dl0MprUpHQAAAB0AAAANAAAAAAAAAAAAAACAAQAAAABtYW5pZmVzdC5qc29uUEsBAhQDFAAAAAAAOX92XVYpdjEcAAAAHAAAAAkAAAAAAAAAAAAAAIABSAAAAHRoZW1lLmNzc1BLBQYAAAAAAgACAHIAAACLAAAAAAA="), c => c.charCodeAt(0));
  const cases = [
    { name: "issues.csv", body: "\\uFEFFissue,title\\n1,Example\\n", type: "text/csv;charset=utf-8", mounted: false, delay: false },
    { name: "sample.zip", body: theme, type: "application/zip", mounted: false, delay: true },
    { name: "diagram.svg", body: '<svg xmlns="http://www.w3.org/2000/svg"><text>ok</text></svg>', type: "image/svg+xml", mounted: true, delay: true },
    { name: "diagram.png", body: png, type: "image/png", mounted: true, delay: true },
  ];
  for (const item of cases) {
    const expected = typeof item.body === "string" ? Array.from(new TextEncoder().encode(item.body)) : Array.from(item.body);
    const url = URL.createObjectURL(new Blob([item.body], { type: item.type }));
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = item.name;
    if (item.mounted) document.body.appendChild(anchor);
    const start = exports.length;
    anchor.click();
    if (item.delay) setTimeout(() => URL.revokeObjectURL(url), 0);
    else URL.revokeObjectURL(url);
    await new Promise((resolve, reject) => {
      const deadline = setTimeout(() => reject(Error("timed out: " + item.name)), 2000);
      const poll = () => exports.length > start ? (clearTimeout(deadline), resolve()) : setTimeout(poll, 5);
      poll();
    });
    anchor.remove();
    const actual = exports[start];
    if (actual.name !== item.name || actual.type !== item.type.split(";")[0] ||
        actual.size !== expected.length || JSON.stringify(actual.bytes) !== JSON.stringify(expected)) {
      throw Error("content mismatch: " + item.name + " " + JSON.stringify({ actual, expected: expected.slice(0, 20) }));
    }
    if (exports.length !== start + 1 || !revocations.includes(url)) throw Error("duplicate or unreleased export: " + item.name);
  }
  // A real click on a mounted anchor must still use the document listener.
  const link = document.createElement("a");
  link.href = "data:text/plain,hello";
  link.download = "plain.txt";
  document.body.appendChild(link);
  const before = exports.length;
  link.dispatchEvent(new MouseEvent("click", { bubbles: true, cancelable: true }));
  await new Promise((resolve, reject) => {
    const deadline = setTimeout(() => reject(Error("data URL timed out")), 2000);
    const poll = () => exports.length > before ? (clearTimeout(deadline), resolve()) : setTimeout(poll, 5);
    poll();
  });
  if (exports[before].name !== "plain.txt" || new TextDecoder().decode(new Uint8Array(exports[before].bytes)) !== "hello")
    throw Error("data URL content mismatch");
  document.title = encodeURIComponent(JSON.stringify({ ok: true, count: exports.length, names: exports.map(x => x.name) }));
})().catch(error => { document.title = encodeURIComponent(JSON.stringify({ ok: false, error: String(error) })); });
</script>`);
  const chrome = spawnSync("google-chrome", ["--headless=new", "--no-sandbox", "--disable-dev-shm-usage",
    "--disable-background-networking", "--virtual-time-budget=5000", "--dump-dom",
    `--user-data-dir=${join(work, "profile")}`, pathToFileURL(page).href],
  { encoding: "utf8", timeout: 25000, maxBuffer: 4 * 1024 * 1024 });
  assert.equal(chrome.status, 0, chrome.stderr);
  const title = chrome.stdout.match(/<title>(.*?)<\/title>/s)?.[1];
  assert.ok(title && title !== "pending", `Chromium did not finish: ${chrome.stderr}`);
  const result = JSON.parse(decodeURIComponent(title));
  assert.equal(result.ok, true, result.error);
  assert.deepEqual(result.names, ["issues.csv", "sample.zip", "diagram.svg", "diagram.png", "plain.txt"]);
  console.log(`Chromium ${chrome.stderr.match(/Chrome [0-9.]+/)?.[0] ?? "headless"}: ${result.count} exports, bytes and filenames verified`);
} finally {
  rmSync(work, { recursive: true, force: true });
}
