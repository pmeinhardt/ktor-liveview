export default function resolve(endpoint: string): string {
  const here = window.location;

  const base = here.toString();
  const protocol = here.protocol === "https:" ? "wss:" : "ws:";

  const uri = new URL(endpoint, base);
  uri.protocol = protocol;

  return uri.toString();
}
