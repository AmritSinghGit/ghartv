import test from "node:test";
import assert from "node:assert/strict";
import { authorizedMediaUrl, channelFromRaw, createAppServer, mergeTicketCookies, normalizeMobile, rewriteHlsManifest, safeMediaUrl } from "../server.mjs";

test("normalizes an Indian mobile number without retaining formatting", () => {
  assert.equal(normalizeMobile("+91 98765 43210"), "9876543210");
  assert.throws(() => normalizeMobile("123"), /valid 10-digit/);
});

test("normalizes one channel record", () => {
  const channel = channelFromRaw({ channel_id: 42, channel_name: "Punjab Test", channelCategoryId: 2, channelLanguageId: 3, logoUrl: "test.png" }, 7, { 2: "News" }, { 3: "Punjabi" });
  assert.deepEqual({ id: channel.id, number: channel.number, name: channel.name, category: channel.category, language: channel.language }, { id: "42", number: 7, name: "Punjab Test", category: "News", language: "Punjabi" });
});

test("rewrites HLS variants, segments and key URIs through the local ticket", () => {
  const source = '#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,URI="keys/live.key"\nvariant.m3u8\nhttps://media.example/segment.ts';
  const hosts = [];
  const result = rewriteHlsManifest(source, "https://edge.example/live/master.m3u8", "ticket1", (url) => hosts.push(new URL(url).hostname));
  assert.match(result, /\/api\/stream\/ticket1\?u=/);
  assert.equal((result.match(/\/api\/stream\/ticket1/g) || []).length, 3);
  assert.deepEqual(hosts, ["edge.example", "edge.example", "media.example"]);
});

test("blocks non-HTTPS and private-network media targets", () => {
  assert.equal(safeMediaUrl("https://edge.example/live.m3u8").hostname, "edge.example");
  assert.throws(() => safeMediaUrl("http://edge.example/live.m3u8"), /HTTPS/);
  assert.throws(() => safeMediaUrl("https://127.0.0.1/private"), /Private-network/);
});

test("inherits the Jio stream authorization onto protected child media", () => {
  const authorization = ["__hdnea__", "fixture"].join("=");
  assert.match(authorizedMediaUrl("https://tv.media.jio.com/live/aes128.key", authorization).href, /__hdnea__=/);
  assert.doesNotMatch(authorizedMediaUrl("https://media.example/live.key", authorization).href, /__hdnea__=/);
});

test("promotes a manifest response token for protected child media", () => {
  const ticket = { headers: { cookie: "existing=1" }, authorization: "" };
  const fixture = ["__hdnea__", "fixture"].join("=");
  mergeTicketCookies(ticket, [`${fixture}; Path=/; Secure`]);
  assert.equal(ticket.authorization, fixture);
  assert.match(ticket.headers.cookie, /existing=1/);
  assert.match(ticket.headers.cookie, /__hdnea__=/);
});

test("serves the local health and review page", async (context) => {
  const server = createAppServer();
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  context.after(() => server.close());
  const address = server.address();
  const health = await fetch(`http://127.0.0.1:${address.port}/api/health`).then((response) => response.json());
  assert.equal(health.service, "ghartv-web-player");
  const page = await fetch(`http://127.0.0.1:${address.port}/`).then((response) => response.text());
  assert.match(page, /Your live television/);
});
