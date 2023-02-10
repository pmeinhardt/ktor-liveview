import resolve from "./resolve";
import Socket from "./Socket";
import type { Params } from "./types";
import View from "./View";

export function setup(endpoint: string, params: Params) {
  if (["complete", "interactive", "loaded"].includes(document.readyState)) {
    connect(endpoint, params);
  } else {
    const deferred = () => connect(endpoint, params);
    document.addEventListener("DOMContentLoaded", deferred);
  }
}

export function connect(endpoint: string, params: Params) {
  const socket = new Socket(resolve(endpoint));
  const view = new View(socket, document.documentElement, params);

  view.join();

  socket.connect();
}
